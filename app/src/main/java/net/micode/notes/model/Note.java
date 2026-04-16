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

package net.micode.notes.model;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 核心便签业务模型实体类。
 * <p>负责在内存中暂存便签主表（Note）及附表（Data）的字段修改差量（Diff）。
 * 并在需要时将这些差量通过 Android {@link android.content.ContentResolver} 推送（持久化）到底层 SQLite 数据库中。
 * 采用了内部类 {@link NoteData} 分离文本及通话记录数据逻辑，保障主从表关联更新的一致性。</p>
 */
public class Note {
    private static final String TAG = "Note";

    /** 暂存便签主表的待更新字段（例如修改时间、层级归属、背景颜色等） */
    private ContentValues mNoteDiffValues;

    /** 代理管理便签附表的数据模型实例（正文文本、通话记录属性） */
    private NoteData mNoteData;

    /**
     * 生成并初始化一条全新的便签记录到数据库中，返回新生成的主键 ID。
     * <p>注意：这是一个静态同步方法，直接执行磁盘 I/O 插入空记录占位。</p>
     *
     * @param context  应用程序上下文
     * @param folderId 所属文件夹 ID（根目录则传入 {@link Notes#ID_ROOT_FOLDER}）
     * @return 新生成的便签全局唯一主键 ID
     * @throws IllegalStateException 若生成 ID 为负数则判定数据库异常并阻断
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 初始化基础元数据：当前时间戳、默认类型（普通便签）、以及脏数据标识（需向云端同步）
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);

        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }

        // 【合规性批注】原作者在此处将 noteId == -1 作为非法判定，但若发生 NumberFormatException
        // noteId 会被赋为 0 并顺延返回，存在逻辑漏洞。生产环境中应当对 0 和负数一并实行阻断拦截。
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 默认构造函数。初始化用于承载主附表差量更新参数的容器。
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 修改便签主表的基础属性字段，并自动标记状态为 "本地已修改" 以触发下次云同步。
     *
     * @param key   表列名常量，详见 {@link NoteColumns}
     * @param value 待写入的字符串值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 修改便签的普通正文文本数据。
     * @param key   数据表列名常量，详见 {@link DataColumns}
     * @param value 正文内容
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 绑定本地便签正文数据所对应的底层详情表（Data）ID。
     * @param id 详情表中关联的条目主键
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取当前便签正文所关联的详情表（Data）ID。
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 绑定本地便签通话记录元数据所对应的底层详情表（Data）ID。
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 修改便签挂载的通话记录元数据（如手机号码、通话时长戳）。
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 检查当前内存实例中是否存在未固化到数据库的变更（包含主表属性或附表内容）。
     *
     * @return 存在脏数据则返回 true
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 执行内存脏数据至本地 SQLite 数据库的物理提交（Flush/Commit）。
     *
     * @param context 应用程序上下文
     * @param noteId  当前便签所对应的数据库主键 ID
     * @return 提交成功返回 true，无修改或由于底层异常导致失败返回 false
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {
            return true;
        }

        /**
         * 按照业务设计预期，主表数据若被修改，必定伴随 `LOCAL_MODIFIED` 和 `MODIFIED_DATE` 字段的变更。
         * 针对极端边缘场景（例如主表字段由于某种不可抗力更新失败），我们继续容错并下渗执行子表附带的数据刷新操作，
         * 从而最大程度保证用户撰写的便签文本正文（Data子表）不会丢失。
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // Do not return, fall through （继续向后穿透执行子表提交）
        }
        mNoteDiffValues.clear();

        // 将子表差量数据推送到 ContentResolver，如果发生严重事务异常或插入失败，则向上抛出 false 结果
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部业务类，专职代理便签详情表（Data 表）内多态数据（纯文本与通话记录）的隔离封装及批量持久化。
     */
    private class NoteData {
        private static final String TAG = "NoteData";

        /** 关联的文本明细条目主键 ID */
        private long mTextDataId;
        /** 暂存文本明细的待更新字段差量 */
        private ContentValues mTextDataValues;

        /** 关联的通话记录明细条目主键 ID */
        private long mCallDataId;
        /** 暂存通话记录明细的待更新字段差量 */
        private ContentValues mCallDataValues;

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 更新通话记录明细数据。
         * 此操作会向上层冒泡，同步刷新外层 {@link Note#mNoteDiffValues} 的本地修改标识及时间戳。
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 更新正文文本明细数据。
         * 此操作同样会向上层冒泡刷新主表脏数据标志。
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将所有内存中的明细数据差量通过系统 IPC 机制推送到 ContentProvider 中。
         * 采用混合策略：
         * 1. 针对尚未分配 ID 的新记录，直接调用独立的 Insert 进行入库，并回填新分配的主键。
         * 2. 针对已存在的明细记录更新，将其合并至 ContentProviderOperation 批处理列表（Batch），利用事务原子性提升 I/O 性能。
         *
         * @param context 应用程序上下文
         * @param noteId  其归属的主便签记录 ID
         * @return 若存在有效更新并提交成功，返回主便签的 Content Uri；若发生系统级崩溃或无有效提交，返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /** Check for safety */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // --- 处理文本明细记录持久化 ---
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // ID 为 0 代表这是首次为该便签写入正文，执行直接插入
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null; // 严重的数据完整性断裂，拒绝向下穿透
                    }
                } else {
                    // 已存在记录，包装为更新事务操作挂入批处理队列
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear(); // 清理已提交的脏记录
            }

            // --- 处理通话记录明细持久化 ---
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 同理，执行全新的通话明细插入
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 包装通话记录更新入批处理队列
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // --- 触底提交：一次性执行收集到的 IPC 更新事务 ---
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    // 校验批量返回结果是否异常
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    // 捕获跨进程调用时（如果 ContentProvider 部署在其它进程）发生的底层通信死锁或服务死亡
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    // 捕获 SQLite 层由于约束冲突、外键失效等引发的数据库内部执行崩溃
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null; // 若 operationList 为空（仅发生了直接 Insert），同样向外响应无批量更新状态
        }
    }
}