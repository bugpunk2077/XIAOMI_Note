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

package net.micode.notes.gtask.data;

import android.database.Cursor;

import org.json.JSONObject;

/**
 * 同步节点抽象基类。
 * 定义了参与云端同步操作的数据实体的通用属性（如全局标识、修改时间、逻辑删除状态）及标准抽象契约。
 * 所有需要与远程服务器（如 Google Task）进行双向同步的具体数据模型均必须继承此类并实现其序列化与状态评估逻辑。
 */
public abstract class Node {

    // =========================================================================
    // 同步动作状态机常量定义 (Sync Action States)
    // =========================================================================

    /** 同步状态：无需任何操作（本地与云端已对齐） */
    public static final int SYNC_ACTION_NONE = 0;

    /** 同步状态：本地新增，需推送到云端 (Add to Remote) */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /** 同步状态：云端新增，需拉取到本地 (Add to Local) */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /** 同步状态：本地已物理/逻辑删除，需通知云端删除 (Delete from Remote) */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /** 同步状态：云端已删除，需同步清理本地数据 (Delete from Local) */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /** 同步状态：本地数据有更新，需覆写云端数据 (Update Remote) */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /** 同步状态：云端数据有更新，需覆写本地数据 (Update Local) */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /** 同步状态：数据冲突（双端均有更新），需进入冲突解决流程 (Conflict Resolution) */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /** 同步状态：同步过程中发生致命错误 (Error State) */
    public static final int SYNC_ACTION_ERROR = 8;

    // =========================================================================
    // 实体通用属性 (Common Properties)
    // =========================================================================

    /** 全局唯一标识符 (Global ID，通常由云端分配，如 Google Task ID) */
    private String mGid;

    /** 节点名称或摘要信息 */
    private String mName;

    /** 节点最后一次修改的 Unix 时间戳 (毫秒级) */
    private long mLastModified;

    /** 逻辑删除标记。true 表示该节点已被用户删除但尚未同步到云端 */
    private boolean mDeleted;

    /**
     * 构造函数。
     * 初始化一个空状态的节点对象。
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    // =========================================================================
    // 抽象契约方法 (Abstract Contracts)
    // =========================================================================

    /**
     * 构建用于向云端发起创建请求的 JSON 动作对象。
     *
     * @param actionId 本次同步操作的会话或事务 ID
     * @return 封装了创建请求参数的 JSON 对象
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 构建用于向云端发起更新请求的 JSON 动作对象。
     *
     * @param actionId 本次同步操作的会话或事务 ID
     * @return 封装了更新请求参数的 JSON 对象
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 依据云端下发的 JSON 数据结构，反序列化并更新当前实体对象的内部状态。
     *
     * @param js 云端返回的 JSON 对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 依据本地构建的 JSON 数据结构，反序列化并设置当前实体对象的内部状态。
     *
     * @param js 本地生成的 JSON 对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将当前实体对象的内部状态序列化为本地通用的 JSON 数据结构。
     *
     * @return 表示当前实体内容的 JSON 对象
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据当前本地数据库记录的游标状态，计算并返回该节点应执行的同步动作。
     *
     * @param c 指向当前节点在本地数据库中对应记录的游标 (Cursor)
     * @return 对应的同步动作常量，参考 {@link #SYNC_ACTION_NONE} 等枚举值
     */
    public abstract int getSyncAction(Cursor c);

    // =========================================================================
    // Getter & Setter
    // =========================================================================

    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }
}