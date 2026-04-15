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
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Google Task 任务列表（文件夹）实体类。
 * 继承自 {@link Node}，对应云端的 TaskList 数据结构，在本地映射为“文件夹”或“系统目录”。
 * 负责组装与云端同步的 JSON 报文，并管理其包含的子任务（Task）集合的排序与双向链表状态。
 */
public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();

    /** 当前列表在用户所有任务列表中的排序索引 */
    private int mIndex;

    /** 子任务集合，利用 ArrayList 维护内存中的顺序结构 */
    private ArrayList<Task> mChildren;

    /**
     * 构造函数。
     * 初始化任务列表节点及其子任务容器。
     */
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    /**
     * 构建用于调用 Google Task API 创建任务列表的 JSON 动作报文。
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

            // 设定该列表的排序索引
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // 封装实体数据 (Entity Delta)，声明类型为 Group（列表）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 构建用于调用 Google Task API 更新任务列表的 JSON 动作报文。
     *
     * @param actionId 本次事务或会话的动作 ID
     * @return 封装完毕的更新请求 JSON 对象
     * @throws ActionFailureException 当 JSON 构建发生底层错误时向外抛出阻断异常
     */
    @Override
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 仅对列表名称及逻辑删除状态进行差量更新
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 从云端拉取的 JSON 响应中反序列化并更新任务列表实体的基础属性。
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

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 依据本地提取的 JSON 数据对象对当前文件夹实体进行名称赋值。
     * 对于上传至云端的文件夹名称，统一添加特定的前缀以标识其来源及类型。
     *
     * @param js 本地生成的便签目录 JSON 对象
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
            return;
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 普通用户文件夹，追加 MIUI 特有前缀
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统保留文件夹映射
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER) {
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                } else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER) {
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE);
                } else {
                    Log.e(TAG, "invalid system folder");
                }
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将远程任务列表结构转换为本地 SQLite 文件夹所需的 JSON 数据结构。
     * 解析时自动剥离系统添加的前缀标识。
     *
     * @return 适用于本地解析的 JSON 对象
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();
            // 剔除 MIUI 云同步专用前缀，还原真实的文件夹名称
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)) {
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            }
            folder.put(NoteColumns.SNIPPET, folderName);

            // 依据剥离后的名称判断是否还原为系统级文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE)) {
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            } else {
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
            }

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 核心仲裁机：针对 TaskList（文件夹）类型的同步动作评估。
     *
     * @param c 包含本地数据库便签记录的当前游标
     * @return 节点状态常量
     */
    @Override
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_NONE;
                } else {
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地存在修改
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 业务特性：对于文件夹的并发冲突，采取“本地优先”的合并策略
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    // =========================================================================
    // 子任务(Task) 层级关系与双向链表操作区
    // Google Task 的顺序强依赖 Prior Sibling 机制，因此所有增删改均需同步维护该关系。
    // =========================================================================

    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 在列表尾部追加一个子任务，并维护关联关系。
     *
     * @param task 待追加的任务实体
     * @return 添加成功返回 true
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置前置兄弟节点为原列表的最后一个元素
                task.setPriorSibling(mChildren.size() > 1 ? mChildren.get(mChildren.size() - 2) : null);
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定索引位置插入一个子任务，并重构受影响节点的前置兄弟关系。
     *
     * @param task  待插入的任务实体
     * @param index 插入位置的索引
     * @return 插入成功返回 true
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 更新新插入节点及其后继节点的 Prior Sibling 指针
            Task preTask = null;
            Task afterTask = null;
            if (index != 0) {
                preTask = mChildren.get(index - 1);
            }
            if (index != mChildren.size() - 1) {
                afterTask = mChildren.get(index + 1);
            }

            task.setPriorSibling(preTask);
            if (afterTask != null) {
                afterTask.setPriorSibling(task);
            }
        }

        return true;
    }

    /**
     * 移除指定的子任务，并修复断裂的双向链表关系。
     *
     * @param task 待移除的任务实体
     * @return 移除成功返回 true
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 断开被移除节点的关联
                task.setPriorSibling(null);
                task.setParent(null);

                // 缝合链表：将被移除节点的后继节点的前置指针，指向被移除节点的前置节点
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 移动子任务在列表中的位置。
     *
     * @param task  需要移动的任务实体
     * @param index 目标索引位置
     * @return 移动成功返回 true
     */
    public boolean moveChildTask(Task task, int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index) {
            return true;
        }

        // 逻辑重组：先移除旧位置，再插入新位置
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 通过 Google Task ID 查找特定的子任务节点。
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * @deprecated 该方法名称存在拼写错误且功能与 {@link #findChildTaskByGid(String)} 完全重复。
     * 在生产环境中应清理此类冗余代码。
     */
    @Deprecated
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    public void setIndex(int index) {
        this.mIndex = index;
    }

    public int getIndex() {
        return this.mIndex;
    }
}