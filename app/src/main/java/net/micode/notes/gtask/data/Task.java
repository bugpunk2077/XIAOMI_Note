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

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Google Task 任务实体类。
 * 继承自 {@link Node}，代表一个具体的云端任务节点。
 * 负责与 Google Task API 进行 JSON 报文的序列化与反序列化封装，
 * 并提供本地与云端数据版本的对比逻辑，以决策双向同步的具体动作。
 */
public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    /** 任务的完成状态 */
    private boolean mCompleted;

    /** 任务的备注信息。在底层同步机制中，该字段常被用于存储本地便签的扩展元数据 JSON 字符串 */
    private String mNotes;

    /** 本地便签序列化后的元数据信息，缓存于内存中以便快速比对和组装 */
    private JSONObject mMetaInfo;

    /** 链表结构：指向当前任务在同一层级（TaskList）中的前一个兄弟节点，用于排序同步 */
    private Task mPriorSibling;

    /** 树状结构：指向当前任务所属的父级任务列表（TaskList） */
    private TaskList mParent;

    /**
     * 构造函数。
     * 初始化任务节点的各项默认状态。
     */
    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    /**
     * 构建用于调用 Google Task API 创建任务的 JSON 动作报文。
     * 严格遵照 Google Task API 的 entity_delta 规范组装数据。
     *
     * @param actionId 本次事务或会话的动作 ID
     * @return 封装完毕的创建请求 JSON 对象
     * @throws ActionFailureException 当 JSON 构建发生底层错误时向外抛出阻断异常
     */
    @Override
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 声明动作类型为创建 (create)
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 注入事务追踪 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 设定在父列表中的索引位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 封装实体数据 (Entity Delta)
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // 绑定层级与归属关联信息
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // 设定排序锚点：前置兄弟节点的 ID
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 构建用于调用 Google Task API 更新任务的 JSON 动作报文。
     *
     * @param actionId 本次事务或会话的动作 ID
     * @return 封装完毕的更新请求 JSON 对象
     * @throws ActionFailureException 当 JSON 构建发生底层错误时向外抛出阻断异常
     */
    @Override
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 声明动作类型为更新 (update)
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 注入事务追踪 ID 及目标任务 GID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 封装变更的实体差量数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    /**
     * 从云端拉取的 JSON 响应中反序列化并更新本实体的各项状态。
     *
     * @param js Google Task API 响应的节点 JSON 对象
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 依据本地提取的聚合 JSON 数据（包含 Note 表与 Data 表数据）对当前任务实体的摘要名称进行赋值。
     *
     * @param js 本地生成的便签聚合 JSON 对象
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
            return; // 补充了 return 确保程序的健壮性
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            // 类型安全性校验，仅处理普通便签
            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            // 遍历详情数据，提取 MIME 类型为普通文本的字段作为任务的 Name
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将远程任务结构逆向转换为本地 SQLite 所需的 JSON 数据结构，以便持久化到本地。
     * 需对新创建于云端（无 mMetaInfo）以及常规双端同步的任务进行分支处理。
     *
     * @return 适用于本地解析的 JSON 对象
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 场景 A：该任务是用户直接在 Google Tasks 网页端创建的新任务，本地不存在历史元数据
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();

                // 将远程任务的名称映射为本地数据表的内容字段
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // 场景 B：存在历史同步元数据，以远程的数据覆写本地元数据中的核心文本字段
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 注入元数据信息。
     * 从特殊的 MetaData 任务实体中解析 notes 字段并固化为本地 JSON 对象模型。
     *
     * @param metaData 承载元数据的特殊节点实例
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    /**
     * 核心仲裁机：根据本地游标状态与远程任务属性的对比，决定当前同步的具体动作。
     *
     * @param c 包含本地数据库便签记录的当前游标
     * @return 节点状态常量 (例如 SYNC_ACTION_NONE, SYNC_ACTION_UPDATE_LOCAL 等)
     */
    @Override
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            // 若元数据丢失，强制将远程数据推入以进行覆盖恢复
            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // 检查 ID 一致性：如果不匹配，说明发生游离或映射混乱，强制下发本地覆盖
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 分支 1：本地无任何修改操作 (Clean State)
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 且同步时间戳等同于远程修改时间，判定为完全同步，无动作
                    return SYNC_ACTION_NONE;
                } else {
                    // 远程发生了变更，因此应用远端修改到本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 分支 2：本地发生了修改 (Dirty State)
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR; // 发生严重的映射关系错位，抛出错误状态
                }

                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 本地最后一次同步标识与远程最后修改时间一致，说明远程未被第三方修改，
                    // 本次为单向的本地增量修改，应当推送至远端。
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 本地最后一次同步标识落后于远程的最后修改时间，且本地也有新修改，触发并发冲突。
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 完整性校验：评估当前任务是否具有有效载荷，避免生成垃圾空记录推送到云端或本地。
     *
     * @return 当存在元数据、任务名称不为空、或者备注信息不为空时，返回 true
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    // =========================================================================
    // 标准 Getter 与 Setter
    // =========================================================================

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }

}