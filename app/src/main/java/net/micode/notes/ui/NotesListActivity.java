/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    private int mode=0;
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;


    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;

    private static final int MENU_FOLDER_DELETE = 0;

    private static final int MENU_FOLDER_VIEW = 1;

    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    };

    private ListEditState mState;

    private BackgroundQueryHandler mBackgroundQueryHandler;

    private NotesListAdapter mNotesListAdapter;

    private ListView mNotesListView;

    private Button mAddNewNote;

    private boolean mDispatch;

    private int mOriginY;

    private int mDispatchY;

    private TextView mTitleBar;

    private long mCurrentFolderId;

    private ContentResolver mContentResolver;

    private ModeCallback mModeCallBack;

    private static final String TAG = "NotesListActivity";

    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;

    private NoteItemData mFocusNoteDataItem;

    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";

    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE  = 103;
    //页面初始化，背景，字体等各种资源
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);


         getWindow().setBackgroundDrawableResource(R.drawable.background1);
         getWindow().setBackgroundDrawableResource(R.drawable.background2);



        initResources();

        /**
         * Insert an introduction when user firstly use this application
         */
        setAppInfoFromRawRes();
    }
    /**
     * 页面返回结果回调
     * 当从编辑笔记页面返回时，刷新笔记列表
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
    /**
     * 首次打开APP时，自动创建一条【新手引导/使用说明】笔记
     * 只在第一次启动时创建一次，之后不再创建
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                 in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char [] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
// 创建一条空笔记，作为【新手使用说明】，并保存
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }
    /**
     * 初始化页面资源、控件、适配器、事件监听
     * 主页面（笔记列表页）的控件初始化核心方法
     */

    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
    }

    /**
     * 多选操作模式回调类
     * 实现：笔记列表的长按多选功能（删除、移动、全选等）
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        // 下拉菜单（全选/取消全选）
        private DropdownMenu mDropDownMenu;
        // 多选操作栏模式
        private ActionMode mActionMode;
        // 移动菜单按钮
        private MenuItem mMoveMenu;

        /**
         * 创建多选操作模式（长按条目进入多选时触发）
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // 加载多选菜单布局
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            // 给删除按钮设置点击监听
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            // 获取移动菜单项
            mMoveMenu = menu.findItem(R.id.move);

            // 判断：如果是通话记录文件夹 或 没有自定义文件夹，则隐藏【移动】按钮
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }

            mActionMode = mode;
            // 开启列表的多选模式
            mNotesListAdapter.setChoiceMode(true);
            // 禁用列表长按（避免重复触发）
            mNotesListView.setLongClickable(false);
            // 隐藏新建笔记按钮
            mAddNewNote.setVisibility(View.GONE);

            // 加载自定义多选标题栏（带下拉菜单）
            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);

            // 初始化下拉菜单（全选/取消全选）
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            // 下拉菜单点击事件
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item) {
                    // 切换 全选/取消全选
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    // 更新菜单显示
                    updateMenu();
                    return true;
                }
            });
            return true;
        }

        /**
         * 更新顶部菜单标题与全选状态
         * 例如：已选择 2 项、取消全选/全选
         */
        private void updateMenu() {
            // 获取选中的笔记数量
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // 设置标题：已选择 x 项
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);

            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                // 如果已经全部选中 → 显示“取消全选”
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    // 未全部选中 → 显示“全选”
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        /**
         * 刷新操作模式（系统空实现）
         */
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * 操作栏按钮点击（系统空实现）
         */
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        /**
         * 退出多选模式时调用
         * 恢复列表正常状态
         */
        public void onDestroyActionMode(ActionMode mode) {
            // 关闭多选模式
            mNotesListAdapter.setChoiceMode(false);
            // 恢复列表长按可用
            mNotesListView.setLongClickable(true);
            // 显示新建笔记按钮
            mAddNewNote.setVisibility(View.VISIBLE);
        }

        /**
         * 手动结束多选模式
         */
        public void finishActionMode() {
            mActionMode.finish();
        }

        /**
         * 列表条目选中状态改变时触发
         * 点击/取消点击条目时调用
         */
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            // 更新菜单标题和状态
            updateMenu();
        }

        /**
         * 菜单按钮点击事件
         * 处理：删除、移动 等操作
         */
        public boolean onMenuItemClick(MenuItem item) {
            // 未选中任何笔记 → 提示“请选择笔记”
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            int itemId = item.getItemId();
            // 点击删除按钮
            if (itemId == R.id.delete) {
                // 弹出删除确认对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_notes,
                        mNotesListAdapter.getSelectedCount()));
                // 确认删除
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // 执行批量删除
                                batchDelete();
                            }
                        });
                // 取消
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
            } else if (itemId == R.id.move) {
                // 点击移动按钮 → 打开文件夹选择界面
                startQueryDestinationFolders();
            } else {
                return false;
            }
            return true;
        }
    }

    /**
     * 新建笔记按钮的触摸监听
     * 核心作用：实现按钮透明区域穿透触摸，让底下的列表可以正常滑动/点击
     */
    private class NewNoteOnTouchListener implements OnTouchListener {

        public boolean onTouch(View v, MotionEvent event) {
            // 判断触摸动作：按下 / 移动 / 抬起
            switch (event.getAction()) {
                // 1. 手指按下按钮
                case MotionEvent.ACTION_DOWN: {
                    // 获取屏幕高度
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();

                    // 获取【新建笔记】按钮自身高度
                    int newNoteViewHeight = mAddNewNote.getHeight();

                    // 计算按钮在屏幕上的起始Y坐标
                    int start = screenHeight - newNoteViewHeight;

                    // 计算触摸点在屏幕上的真实Y坐标
                    int eventY = start + (int) event.getY();

                    // 如果是子文件夹状态，减去标题栏高度
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
                    }

                    /**
                     * 👇👇👇 核心逻辑 👇👇👇
                     * 这是一个UI适配的特殊处理：
                     * 按钮有一部分是【透明图片区域】，用户点这里时，
                     * 要把触摸事件【穿透】给底下的笔记列表，让列表能正常滑动
                     *
                     * 公式：y = -0.12x + 94
                     * 意思是：按钮左上角区域是透明的，需要事件穿透
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        // 获取列表最底部的可见条目
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());

                        // 判断：底部条目正好在按钮下方
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {

                            // 记录坐标，准备事件穿透
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);

                            // 标记：开启事件分发（穿透）
                            mDispatch = true;

                            // 把触摸事件交给底下的列表处理（滑动/点击）
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                }

                // 2. 手指在按钮上滑动
                case MotionEvent.ACTION_MOVE: {
                    // 如果开启了穿透，滑动事件也传给列表（实现列表滑动）
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }

                // 3. 手指抬起 / 其他事件
                default: {
                    // 结束穿透，恢复正常
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
            }
            // 默认不拦截事件，正常响应按钮点击
            return false;
        }
    };

    /**
     * 异步查询笔记列表数据（后台查询，不卡界面）
     * 根据当前选中的文件夹，加载对应的笔记数据
     */
    private void startAsyncNotesListQuery() {
        // 判断当前文件夹：
        // 如果是【根文件夹（所有笔记）】，使用根目录查询条件
        // 如果是【普通文件夹】，使用普通查询条件
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;

        // 后台异步查询数据库（不会阻塞UI线程）
        // 参数说明：
        // FOLDER_NOTE_LIST_QUERY_TOKEN = 查询标识
        // Notes.CONTENT_NOTE_URI       = 笔记数据访问地址
        // NoteItemData.PROJECTION      = 要查询的字段
        // selection                    = 查询条件（根据文件夹筛选）
        // new String[] { ... }         = 文件夹ID（查询哪个文件夹的笔记）
        // 最后参数                     = 排序规则：按笔记类型 + 修改时间 降序（最新的排在最上面）
        mBackgroundQueryHandler.startQuery(
                FOLDER_NOTE_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                NoteItemData.PROJECTION,
                selection,
                new String[] { String.valueOf(mCurrentFolderId) },
                NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC"
        );
    }

    /**
     * 后台异步查询处理器
     * 专门在子线程查询数据库，查询完成后自动切回主线程更新界面
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {

        // 构造方法：传入内容解析器，用于访问数据
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        /**
         * 查询完成回调（系统自动调用）
         * @param token 查询标识符（区分是哪个查询任务）
         * @param cookie 备用参数
         * @param cursor 查询返回的结果数据（笔记/文件夹列表）
         */
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // 根据 token 判断是哪次查询
            switch (token) {
                // 1. 笔记列表查询完成
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    // 将查询到的笔记数据设置给适配器，显示到列表上
                    mNotesListAdapter.changeCursor(cursor);
                    break;

                // 2. 文件夹列表查询完成
                case FOLDER_LIST_QUERY_TOKEN:
                    if (cursor != null && cursor.getCount() > 0) {
                        // 显示文件夹选择菜单
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;

                default:
                    return;
            }
        }
    }
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // if not synced, delete notes directly
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // in sync mode, we'll move the deleted note into the trash
                    // folder
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!isSyncMode()) {
            // if not synced, delete folder directly
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // in sync mode, we'll move the deleted folder into the trash folder
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote();
        }
    }

    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });

        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                if (!create) {
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                            String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        /**
         * When the name edit text is null, disable the positive button
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }
        });
    }

    @Override
    public void onBackPressed() {
        switch (mState) {
            case SUB_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                break;
            case CALL_RECORD_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                break;
            case NOTE_LIST:
                super.onBackPressed();
                break;
            default:
                break;
        }
    }

    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            appWidgetId
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_folder));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteFolder(mFocusNoteDataItem.getId());
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case MENU_FOLDER_CHANGE_NAME:
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // set sync or sync_cancel
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }

        if(mode==-1){
            menu.findItem(R.id.menu_background1).setVisible(false);
        }
        else if(mode==0){
            menu.findItem(R.id.menu_background2).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_background1) {
            mode=-1;
            getWindow().setBackgroundDrawableResource(R.drawable.background1);
        } else if (itemId == R.id.menu_background2) {
            mode=0;
            getWindow().setBackgroundDrawableResource(R.drawable.background2);
        } else if (itemId == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true);
        } else if (itemId == R.id.menu_export_text) {
            exportNoteToText();
        } else if (itemId == R.id.menu_sync) {
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                startPreferenceActivity();
            }
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_search) {
            onSearchRequested();
        }
        return true;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null /* appData */, false);
        return true;
    }

    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }

        }.execute();
    }

    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    private class OnListItemClickListener implements OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
            "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}
