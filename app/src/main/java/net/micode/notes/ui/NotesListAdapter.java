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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


/**
 * 笔记列表适配器
 * 作用：将数据库中的笔记/文件夹数据绑定到 ListView 上显示
 * 支持：单选/多选模式、批量操作、桌面小工具相关处理
 */
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";

    private Context mContext; // 上下文

    // 存储被选中的列表项 position -> 是否选中
    private HashMap<Integer, Boolean> mSelectedIndex;

    private int mNotesCount; // 笔记总数（仅笔记，不含文件夹）

    private boolean mChoiceMode; // 是否处于多选模式（批量操作模式）

    /**
     * 桌面小工具属性静态内部类
     * 存储小工具ID和类型
     */
    public static class AppWidgetAttribute {
        public int widgetId;    // 小工具ID
        public int widgetType;  // 小工具类型
    };

    /**
     * 构造方法
     */
    public NotesListAdapter(Context context) {
        super(context, null);
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    /**
     * 创建列表项的视图（每个item都是 NotesListItem）
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    /**
     * 绑定数据到列表项
     * 将数据库数据设置到界面上
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            // 从Cursor中封装成NoteItemData
            NoteItemData itemData = new NoteItemData(context, cursor);
            // 绑定数据到item视图
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 设置某个位置的item是否被选中
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged(); // 刷新列表
    }

    /**
     * 是否处于多选模式
     */
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /**
     * 设置是否开启多选模式
     * 开启时会清空之前的选中状态
     */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /**
     * 全选/取消全选 所有笔记
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                // 只选中笔记，不选文件夹
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 获取所有被选中item的ID集合
     * 用于批量删除、移动、同步等操作
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取选中项对应的桌面小工具信息
     * 用于删除笔记时同时删除小工具
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取被选中的笔记数量
     */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断是否全部笔记都被选中
     */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    /**
     * 判断某个位置的item是否被选中
     */
    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    /**
     * 数据内容变化时重新计算笔记总数
     */
    @Override
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    /**
     * 切换Cursor时重新计算笔记总数
     */
    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    /**
     * 计算列表中笔记的总数量（不含文件夹）
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}