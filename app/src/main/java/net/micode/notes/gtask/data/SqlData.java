/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 针对便签详情数据（Data表）的 SQL 数据对象映射类。
 * 封装了底层 ContentProvider 的 CRUD 操作，并内置了脏数据检查（Dirty Checking）机制。
 * 仅当对象的字段值发生实际变更时，才将其列入待提交的 ContentValues 中，从而优化数据库的 I/O 性能。
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    /** 无效的数据主键标识 */
    private static final int INVALID_ID = -99999;

    /** * 数据库查询的默认投影（Projection）数组。
     * 定义了从 Data 表中提取的核心列名，顺序必须与下方的 COLUMN 常量严格对应。
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // --- 列索引映射常量 ---
    public static final int DATA_ID_COLUMN = 0;
    public static final int DATA_MIME_TYPE_COLUMN = 1;
    public static final int DATA_CONTENT_COLUMN = 2;
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    private ContentResolver mContentResolver;

    /** 标识当前对象是否为一条尚未持久化到数据库的新记录 */
    private boolean mIsCreate;

    /** 数据记录的主键 ID */
    private long mDataId;

    /** 数据记录的 MIME 类型 */
    private String mDataMimeType;

    /** 数据记录的正文内容 */
    private String mDataContent;

    /** 泛型扩展字段 1（通常用于存储整型数据） */
    private long mDataContentData1;

    /** 泛型扩展字段 3（通常用于存储文本数据） */
    private String mDataContentData3;

    /** 脏字段集合：记录自上次加载或清空后发生变更的键值对 */
    private ContentValues mDiffDataValues;

    /**
     * 构造函数（新建模式）。
     * 初始化一个处于“待创建”状态的空数据对象。
     *
     * @param context 应用上下文，用于获取 ContentResolver
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
     * 构造函数（加载模式）。
     * 根据传入的数据库游标还原数据对象的状态，并将其标记为“已存在”。
     *
     * @param context 应用上下文
     * @param c       包含数据记录的游标。调用方需确保游标已移动到有效位置且未被关闭。
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从游标中反序列化字段数据到当前对象。
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 依据云端下发的 JSON 数据更新本地对象状态。
     * 内部采用脏检查逻辑：仅当新值与当前内存值不一致时，才将新值推入 mDiffDataValues 待同步集合中。
     *
     * @param js 云端传回的 JSON 数据对象
     * @throws JSONException 当 JSON 数据结构异常或类型不匹配时抛出
     */
    public void setContent(JSONObject js) throws JSONException {
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将当前对象的最新状态序列化为 JSON 格式，以便推送至云端。
     *
     * @return 序列化后的 JSON 对象。如果当前对象是尚未入库的新记录，则拒绝序列化并返回 null。
     * @throws JSONException 当 JSON 组装失败时抛出
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 将暂存在 mDiffDataValues 中的脏数据持久化到本地数据库。
     *
     * @param noteId          当前数据关联的所属便签的主键 ID
     * @param validateVersion 是否启用乐观锁验证
     * @param version         用于乐观锁比对的版本号
     * @throws ActionFailureException 当插入操作失败或无法获取新纪录 ID 时抛出
     */
    public void commit(long noteId, boolean validateVersion, long version) {
        if (mIsCreate) {
            // 清理非法的主键，交由 SQLite 自增处理
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // 仅当存在脏数据时才发起更新 I/O
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 使用子查询实现对主表版本号的乐观锁校验
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 提交成功后清空脏状态，并转换生命周期为“已存在”
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    public long getId() {
        return mDataId;
    }
}