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
    /**
     * 显示【选择目标文件夹】对话框
     * 用于批量移动笔记时，让用户选择要移动到哪个文件夹
     * @param cursor 从数据库查询到的所有文件夹数据
     */
    private void showFolderListMenu(Cursor cursor) {
        // 创建弹窗对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);

        // 设置弹窗标题：选择文件夹
        builder.setTitle(R.string.menu_title_select_folder);

        // 创建文件夹列表适配器，把数据库中的文件夹数据加载进去
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);

        // 给弹窗设置列表，并设置条目点击事件
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {

            // 用户点击了某个文件夹
            public void onClick(DialogInterface dialog, int which) {
                // 执行批量移动操作：把选中的笔记移动到目标文件夹
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));

                // 弹出提示：成功移动 x 条笔记到 xxx 文件夹
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();

                // 移动完成后，关闭多选模式，刷新列表
                mModeCallBack.finishActionMode();
            }
        });

        // 显示文件夹选择弹窗
        builder.show();
    }

    /**
     * 创建一条新笔记
     * 点击右下角【新建笔记】按钮时执行
     */
    private void createNewNote() {
        // 构建跳转意图：从当前页面跳转到 笔记编辑页面
        Intent intent = new Intent(this, NoteEditActivity.class);

        // 设置动作：插入或编辑笔记
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);

        // 携带当前文件夹ID → 告诉编辑页面：新笔记要保存在哪个文件夹里
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);

        // 跳转到编辑页面，并等待返回结果（用于返回后刷新列表）
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }
    /**
     * 批量删除选中的笔记
     * 分为两种模式：普通模式（直接删除）、同步模式（移入回收站）
     */
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

    /**
     * 删除文件夹
     * 根据是否是同步模式，直接删除 或 移入回收站
     * @param folderId 要删除的文件夹ID
     */
    private void deleteFolder(long folderId) {
        // 禁止删除根文件夹（所有笔记），这是系统默认文件夹，不能删
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        // 把要删除的文件夹ID装进集合
        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);

        // 获取该文件夹下笔记关联的桌面小部件（用于删除后更新小部件）
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver, folderId);

        // 判断是否为同步模式
        if (!isSyncMode()) {
            // 非同步模式：直接永久删除这个文件夹（包含里面所有笔记）
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // 同步模式：不直接删除，将文件夹 移入【回收站】
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }

        // 删除/移动后，更新所有关联的桌面小部件
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    // 刷新桌面小部件显示
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }
    /**
     * 打开一条已有的笔记（查看/编辑）
     * @param data 被点击的笔记数据
     */
    private void openNode(NoteItemData data) {
        // 跳转到笔记编辑页面
        Intent intent = new Intent(this, NoteEditActivity.class);
        // 设置动作为：查看笔记
        intent.setAction(Intent.ACTION_VIEW);
        // 把当前笔记的 ID 传递给编辑页面（告诉页面要打开哪条笔记）
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        // 跳转并等待返回结果（返回后刷新列表）
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /**
     * 打开一个文件夹（进入文件夹查看里面的笔记）
     * @param data 被点击的文件夹数据
     */
    private void openFolder(NoteItemData data) {
        // 记录当前打开的文件夹 ID
        mCurrentFolderId = data.getId();
        // 异步查询该文件夹下的所有笔记，并显示到列表
        startAsyncNotesListQuery();

        // 判断：如果是【通话记录文件夹】
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 设置界面状态为通话记录文件夹
            mState = ListEditState.CALL_RECORD_FOLDER;
            // 隐藏“新建笔记”按钮（通话记录不能新建）
            mAddNewNote.setVisibility(View.GONE);
        } else {
            // 普通文件夹
            mState = ListEditState.SUB_FOLDER;
        }

        // 设置标题栏文字
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 固定显示：通话记录
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            // 显示文件夹名称
            mTitleBar.setText(data.getSnippet());
        }

        // 显示标题栏
        mTitleBar.setVisibility(View.VISIBLE);
    }

    /**
     * 点击事件监听
     * 目前只处理：右下角【新建笔记】按钮
     */
    public void onClick(View v) {
        // 判断点击的是不是新建笔记按钮
        if (v.getId() == R.id.btn_new_note) {
            // 执行新建笔记逻辑
            createNewNote();
        }
    }

    /**
     * 强制弹出软键盘（输入法键盘）
     * 一般在进入编辑页面、需要立即输入文字时调用
     */
    private void showSoftInput() {
        // 获取系统输入法管理器（负责控制键盘显示/隐藏）
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // 如果获取成功，强制显示软键盘
        if (inputMethodManager != null) {
            // SHOW_FORCED = 强制显示键盘
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    /**
     * 隐藏软键盘
     * @param view 当前获取焦点的View（用于获取窗口令牌）
     */
    private void hideSoftInput(View view) {
        // 获取系统输入法管理器
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // 隐藏软键盘，根据传入View的窗口令牌关闭
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * 显示【创建文件夹】或【重命名文件夹】的对话框
     * @param create true = 创建文件夹；false = 重命名文件夹
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        // 创建对话框构建器
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 加载对话框布局（包含一个输入框）
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        // 获取对话框中的输入框（用于输入/修改文件夹名称）
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        // 自动弹出软键盘，方便用户输入
        showSoftInput();

        // ===================== 区分：重命名 / 新建 =====================
        // 如果是【重命名文件夹】
        if (!create) {
            // 把当前文件夹的原有名称填入输入框
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                // 设置对话框标题：修改文件夹名称
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            // 如果是【新建文件夹】，清空输入框
            etName.setText("");
            // 设置对话框标题：新建文件夹
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        // 设置确定按钮（点击事件后面手动设置）
        builder.setPositiveButton(android.R.string.ok, null);
        // 设置取消按钮
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 点击取消，隐藏软键盘
                hideSoftInput(etName);
            }
        });

        // 显示对话框
        final Dialog dialog = builder.setView(view).show();
        // 获取对话框中的【确定】按钮
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);

        // ===================== 确定按钮点击事件 =====================
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // 点击确定后，隐藏键盘
                hideSoftInput(etName);
                // 获取输入的文件夹名称
                String name = etName.getText().toString();

                // 检查文件夹名称是否已存在，存在则提示并返回
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    // 选中输入框所有文字，方便重新输入
                    etName.setSelection(0, etName.length());
                    return;
                }

                // ===================== 执行操作 =====================
                if (!create) {
                    // 1. 重命名文件夹
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);          // 文件夹名称
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER); // 类型：文件夹
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);      // 标记本地已修改
                        // 更新数据库
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID + "=?",
                                new String[] { String.valueOf(mFocusNoteDataItem.getId()) });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    // 2. 新建文件夹
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);          // 文件夹名称
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER); // 类型：文件夹
                    // 插入数据库，创建新文件夹
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }

                // 关闭对话框
                dialog.dismiss();
            }
        });

        // 初始状态：如果输入框为空，禁用确定按钮
        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }

        // ===================== 输入框文字监听 =====================
        /**
         * 输入框内容变化时：
         * 空 -> 确定按钮禁用
         * 有文字 -> 确定按钮启用
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 输入框为空，禁用确定按钮
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    // 输入框有内容，启用确定按钮
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * 手机返回键点击事件
     * 处理文件夹层级返回逻辑，而不是直接退出App
     */
    @Override
    public void onBackPressed() {
        // 根据当前界面状态，判断返回逻辑
        switch (mState) {
            // 1. 当前在【普通子文件夹】里面
            case SUB_FOLDER:
                // 切回根文件夹（所有笔记）
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                // 状态改为：笔记列表
                mState = ListEditState.NOTE_LIST;
                // 重新加载根文件夹的笔记
                startAsyncNotesListQuery();
                // 隐藏标题栏
                mTitleBar.setVisibility(View.GONE);
                break;

            // 2. 当前在【通话记录文件夹】里面
            case CALL_RECORD_FOLDER:
                // 切回根文件夹
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                // 状态改为：笔记列表
                mState = ListEditState.NOTE_LIST;
                // 显示【新建笔记】按钮
                mAddNewNote.setVisibility(View.VISIBLE);
                // 隐藏标题栏
                mTitleBar.setVisibility(View.GONE);
                // 重新加载数据
                startAsyncNotesListQuery();
                break;

            // 3. 当前已经在【根目录（所有笔记）】
            case NOTE_LIST:
                // 执行系统默认返回 → 退出APP
                super.onBackPressed();
                break;

            default:
                break;
        }
    }

    /**
     * 更新桌面笔记小部件（Widget）
     * 当笔记增删改后，同步刷新桌面添加的便签小组件
     * @param appWidgetId 小部件的唯一ID
     * @param appWidgetType 小部件类型（2x格式 / 4x格式）
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        // 创建广播意图：用于通知桌面小部件更新
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        // 根据小部件类型，绑定对应的更新处理类
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            // 2x2 大小的桌面小部件
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            // 4x1 大小的桌面小部件
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            // 不支持的小部件类型，打印错误日志并退出
            Log.e(TAG, "Unspported widget type");
            return;
        }

        // 把要更新的小部件ID放进广播里
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                appWidgetId
        });

        // 发送广播，通知桌面小部件：数据变了，快刷新！
        sendBroadcast(intent);

        // 设置返回结果，表示更新成功
        setResult(RESULT_OK, intent);
    }

    /**
     * 文件夹 长按菜单创建监听器
     * 功能：长按文件夹时，弹出上下文操作菜单
     */
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {

        /**
         * 长按文件夹时，系统自动调用此方法创建菜单
         * @param menu  上下文菜单对象
         * @param v     被长按的视图（文件夹item）
         * @param menuInfo  菜单信息
         */
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            // 判断：当前有被长按选中的文件夹数据
            if (mFocusNoteDataItem != null) {
                // 设置菜单的标题 = 文件夹的名称
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());

                // 添加 菜单选项1：查看文件夹
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);

                // 添加 菜单选项2：删除文件夹
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);

                // 添加 菜单选项3：重命名文件夹
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    /**
     * 上下文菜单关闭时的回调方法
     * 作用：菜单关闭后，移除列表的上下文菜单监听，避免异常和重复触发
     */
    @Override
    public void onContextMenuClosed(Menu menu) {
        // 如果笔记列表不为空，清空它的上下文菜单创建监听器
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        // 调用父类的方法，执行系统默认逻辑
        super.onContextMenuClosed(menu);
    }

    /**
     * 上下文菜单条目点击事件
     * 作用：处理长按文件夹弹出菜单的点击操作（查看、删除、重命名）
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // 安全校验：如果长按的文件夹数据为空，打印日志并直接返回
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }

        // 根据点击的菜单ID，执行对应的操作
        switch (item.getItemId()) {
            // 点击【查看文件夹】菜单
            case MENU_FOLDER_VIEW:
                // 打开当前长按的文件夹，查看内部笔记
                openFolder(mFocusNoteDataItem);
                break;

            // 点击【删除文件夹】菜单
            case MENU_FOLDER_DELETE:
                // 创建删除确认对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                // 设置对话框标题
                builder.setTitle(getString(R.string.alert_title_delete));
                // 设置警告图标
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                // 设置提示信息：确认删除文件夹
                builder.setMessage(getString(R.string.alert_message_delete_folder));

                // 设置确定按钮：点击后执行删除文件夹操作
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // 调用删除文件夹的方法
                                deleteFolder(mFocusNoteDataItem.getId());
                            }
                        });
                // 设置取消按钮：点击无操作，关闭弹窗
                builder.setNegativeButton(android.R.string.cancel, null);
                // 显示删除确认弹窗
                builder.show();
                break;

            // 点击【重命名文件夹】菜单
            case MENU_FOLDER_CHANGE_NAME:
                // 打开重命名对话框（false 代表重命名，而非新建）
                showCreateOrModifyFolderDialog(false);
                break;

            // 其他未定义的菜单选项，不处理
            default:
                break;
        }

        // 返回true，表示已经处理了菜单点击事件
        return true;
    }
    /**
     * 每次打开右上角菜单前都会调用这个方法
     * 作用：动态准备、更新菜单内容
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 清空之前的菜单，防止菜单重复/残留
        menu.clear();

        // 根据当前界面状态，加载不同的菜单
        if (mState == ListEditState.NOTE_LIST) {
            // 当前在【主页面（所有笔记）】
            // 加载主页面菜单
            getMenuInflater().inflate(R.menu.note_list, menu);

            // 动态设置同步按钮文字
            // 如果正在同步 → 显示“取消同步”
            // 如果未同步 → 显示“同步”
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);

        } else if (mState == ListEditState.SUB_FOLDER) {
            // 当前在【普通子文件夹】
            // 加载子文件夹菜单
            getMenuInflater().inflate(R.menu.sub_folder, menu);

        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            // 当前在【通话记录文件夹】
            // 加载通话记录菜单
            getMenuInflater().inflate(R.menu.call_record_folder, menu);

        } else {
            // 异常状态，打印错误日志
            Log.e(TAG, "Wrong state:" + mState);
        }

        // 根据当前背景模式（mode），动态隐藏对应的背景切换菜单项
        if(mode == -1){
            // 模式-1：隐藏背景1选项
            menu.findItem(R.id.menu_background1).setVisible(false);
        } else if(mode == 0){
            // 模式0：隐藏背景2选项
            menu.findItem(R.id.menu_background2).setVisible(false);
        }

        // 返回true，表示菜单准备完成，可以显示
        return true;
    }

    /**
     * 右上角【选项菜单】的点击事件处理
     * 作用：用户点击菜单里的任意按钮，都会走到这个方法
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 获取当前点击的菜单按钮ID
        int itemId = item.getItemId();

        // ============== 背景皮肤切换 ==============
        // 点击 背景1 切换白色/默认背景
        if (itemId == R.id.menu_background1) {
            mode = -1;  // 记录当前背景模式
            getWindow().setBackgroundDrawableResource(R.drawable.background1); // 设置窗口背景
        }
        // 点击 背景2 切换深色背景
        else if (itemId == R.id.menu_background2) {
            mode = 0;   // 记录当前背景模式
            getWindow().setBackgroundDrawableResource(R.drawable.background2); // 设置窗口背景
        }

        // ============== 文件夹操作 ==============
        // 点击 新建文件夹
        else if (itemId == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true); // 弹出创建文件夹对话框
        }

        // ============== 导出笔记 ==============
        // 点击 导出为文本
        else if (itemId == R.id.menu_export_text) {
            exportNoteToText(); // 执行导出逻辑
        }

        // ============== 同步功能 ==============
        // 点击 同步/取消同步
        else if (itemId == R.id.menu_sync) {
            if (isSyncMode()) { // 如果开启了同步功能
                // 当前文字是 "同步" → 开始同步
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    // 当前文字是 "取消同步" → 停止同步
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                // 未开启同步 → 跳转到设置界面
                startPreferenceActivity();
            }
        }

        // ============== 设置页面 ==============
        // 点击 设置
        else if (itemId == R.id.menu_setting) {
            startPreferenceActivity(); // 跳转到设置界面
        }

        // ============== 新建笔记 ==============
        // 点击 新建笔记
        else if (itemId == R.id.menu_new_note) {
            createNewNote(); // 创建新笔记
        }

        // ============== 搜索功能 ==============
        // 点击 搜索
        else if (itemId == R.id.menu_search) {
            onSearchRequested(); // 打开搜索界面
        }

        // 返回 true 表示处理了点击事件
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

    /**
     * 判断是否开启了同步模式
     * @return true = 已设置同步账号，开启同步；false = 未开启
     */
    private boolean isSyncMode() {
        // 检查同步账号是否不为空
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 打开设置页面（同步、账号、偏好设置）
     */
    private void startPreferenceActivity() {
        // 获取当前要跳转的Activity（处理嵌套Activity情况）
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        // 跳转设置页面，如果需要的话
        from.startActivityIfNeeded(intent, -1);
    }

    /**
     * 列表条目点击监听器
     * 点击笔记/文件夹时触发
     */
    private class OnListItemClickListener implements OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // 判断点击的是不是笔记列表项
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();

                // 如果是多选模式（批量删除/移动）
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        // 切换条目选中状态
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                // 根据当前界面状态处理点击
                switch (mState) {
                    // 在主页面（所有笔记）
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER || item.getType() == Notes.TYPE_SYSTEM) {
                            // 点击文件夹 → 打开文件夹
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            // 点击笔记 → 打开笔记
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;

                    // 在子文件夹 / 通话记录文件夹
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            // 只允许点击打开笔记
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

    /**
     * 查询可移动的目标文件夹（用于批量移动笔记）
     */
    private void startQueryDestinationFolders() {
        // 查询条件：类型=文件夹，且不是回收站，且不是当前文件夹
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";

        // 如果是主页面，额外加入根文件夹
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
                "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        // 异步查询数据库
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

    /**
     * 列表长按事件
     * 长按笔记 → 进入多选模式
     * 长按文件夹 → 弹出上下文菜单（查看/删除/重命名）
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            // 获取长按的条目数据
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();

            // 长按的是【笔记】，且不是多选模式
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                // 进入批量操作模式
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    // 震动反馈
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            }
            // 长按的是【文件夹】
            else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                // 绑定文件夹长按菜单
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}
