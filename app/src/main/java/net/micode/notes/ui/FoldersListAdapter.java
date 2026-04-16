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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 这是一个文件夹列表数据适配器，继承自 CursorAdapter。
 * 主要用于将数据库查询返回的游标（Cursor）数据中的“文件夹（Folder）”记录适配并绑定到 ListView、Spinner（下拉列表）等视图控件上。
 */
public class FoldersListAdapter extends CursorAdapter {
    /** 规定查询数据库时需要的字段集，ID用于标识，SNIPPET(摘要)用作展示名称 */
    public static final String [] PROJECTION = {
        NoteColumns.ID,
        NoteColumns.SNIPPET
    };

    public static final int ID_COLUMN   = 0;
    public static final int NAME_COLUMN = 1;

    /**
     * 构建适配器实例
     * @param context 视图呈现的上下文环境
     * @param c 查询完毕包含数据的数据库游标
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    /**
     * 重写此方法用于根据游标创建每一个项的展示视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // 创建并返回自定义的一行布局项表示一个文件夹项
        return new FolderListItem(context);
    }

    /**
     * 将从 Cursor 获取的具体数据绑定到对应的视图实例（FolderListItem）上
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            // 如果是根文件夹（ID_ROOT_FOLDER），显示本地化字符串"移出文件夹"（或者父目录）。否则显示真实的文件夹名。
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 提供一个直接获取特定 Position(行) 的文件夹名称的公共方法，供外界获取用户选中项内容。
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    /**
     * 内部成员类：单独用来定义一个文件夹列表项所对应的视图 (LinearLayout包装TextView)
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName;

        public FolderListItem(Context context) {
            super(context);
            // 解析由 R.layout.folder_list_item XML 定义好的视图模板
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name); // 绑定文本视图
        }

        /**
         * 将模型层（文件夹名称文字）设定到UI组件
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
