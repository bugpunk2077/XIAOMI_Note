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
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 封装在便签列表中展示的单一项记录的数据实体对象类。
 * 它直接从数据库的游标 (Cursor) 中的单行数据中读取各种属性字段，
 * 如便签的种类（便签/文件夹）、包含几条子便签、是否含有附件等。
 * 这个类负责整理和准备数据，传递给 NotesListItem 等 UI 组件以进行具体的视图绘制与状态切换。
 */
public class NoteItemData {
    static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.CREATED_DATE,
        NoteColumns.HAS_ATTACHMENT,
        NoteColumns.MODIFIED_DATE,
        NoteColumns.NOTES_COUNT,
        NoteColumns.PARENT_ID,
        NoteColumns.SNIPPET,
        NoteColumns.TYPE,
        NoteColumns.WIDGET_ID,
        NoteColumns.WIDGET_TYPE,
    };

    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private boolean mHasAttachment;
    private long mModifiedDate;
    private int mNotesCount;
    private long mParentId;
    private String mSnippet;
    private int mType;
    private int mWidgetId;
    private int mWidgetType;
    private String mName;
    private String mPhoneNumber;

    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;
    private boolean mIsMultiNotesFollowingFolder;

    /**
     * 构造方法：将数据库查询结果 Cursor 封装成 NoteItemData 数据对象
     * 作用：把数据库里的一条记录，转换成 Java 可使用的对象
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 从数据库游标中获取各项字段数据
        mId = cursor.getLong(ID_COLUMN);                             // 笔记/文件夹唯一ID
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);           // 提醒时间
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);             // 背景颜色ID
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);         // 创建时间
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false; // 是否有附件
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);       // 修改时间
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);            // 文件夹内笔记数量
        mParentId = cursor.getLong(PARENT_ID_COLUMN);               // 父文件夹ID
        mSnippet = cursor.getString(SNIPPET_COLUMN);                // 笔记内容摘要

        // 移除文本中的复选框标记，让列表显示的纯文本更干净
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");

        mType = cursor.getInt(TYPE_COLUMN);                         // 类型：笔记/文件夹
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);                // 桌面小工具ID
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);            // 桌面小工具类型

        // 初始化通话记录相关的号码和联系人名称
        mPhoneNumber = "";

        // 判断：如果当前笔记属于【通话记录文件夹】
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            // 根据笔记ID查询对应的通话号码
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                // 根据号码查询联系人姓名
                mName = Contact.getContact(context, mPhoneNumber);
                // 如果没有姓名，直接显示电话号码
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        // 防止空指针，姓名为空时赋值空字符串
        if (mName == null) {
            mName = "";
        }

        // 检查当前条目在列表中的位置（用于设置背景样式）
        checkPostion(cursor);
    }

    /**
     * 检查当前条目在列表中的位置信息
     * 作用：判断是第一条、最后一条、单独一条、还是跟在文件夹后面
     * 用于给列表条目设置不同的圆角背景，优化UI显示效果
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast() ? true : false;               // 是否为最后一条
        mIsFirstItem = cursor.isFirst() ? true : false;             // 是否为第一条
        mIsOnlyOneItem = (cursor.getCount() == 1);                  // 是否是列表中唯一一条
        mIsMultiNotesFollowingFolder = false;                       // 标记：是否是文件夹后多条笔记中的一条
        mIsOneNoteFollowingFolder = false;                          // 标记：是否是文件夹后唯一一条笔记

        // 仅对【非第一条的笔记】进行位置判断
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();                   // 记录当前游标位置

            // 将游标移动到上一条，判断前一条是否是文件夹
            if (cursor.moveToPrevious()) {
                // 如果上一条是 文件夹 或 系统文件夹
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {

                    // 判断当前笔记后面是否还有其他笔记
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;        // 后面还有多条笔记
                    } else {
                        mIsOneNoteFollowingFolder = true;           // 后面没有笔记了，只有这一条
                    }
                }

                // 游标移回原来的位置，保证数据读取正确
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    public boolean isLast() {
        return mIsLastItem;
    }

    public String getCallName() {
        return mName;
    }

    public boolean isFirst() {
        return mIsFirstItem;
    }

    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    public long getId() {
        return mId;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getCreatedDate() {
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        return mHasAttachment;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public long getParentId() {
        return mParentId;
    }

    public int getNotesCount() {
        return mNotesCount;
    }

    public long getFolderId () {
        return mParentId;
    }

    public int getType() {
        return mType;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}
