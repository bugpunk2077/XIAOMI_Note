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

package net.micode.notes.tool;

/**
 * Google Task 协议字符串常量工具类。
 * <p>该类集中定义了与 Google Task 隐藏 API 进行 JSON 通信时所依赖的所有键名 (Keys) 和预设值 (Values)。
 * 同时包含了小米便签云同步方案中，用于在远端实现自定义数据结构（如文件夹映射、元数据存储）的特殊标识前缀。</p>
 */
public class GTaskStringUtils {

    // =========================================================================
    // Google Task RPC JSON 请求与响应报文键名常量
    // =========================================================================

    /** 请求批处理中单个动作的唯一事务 ID */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    /** 动作列表队列，包含了本次提交的所有增删改请求 */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    /** 动作类型键名 */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    /** 动作类型值：创建新节点 */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    /** 动作类型值：全量拉取数据 */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    /** 动作类型值：移动节点（更改父级或排序） */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    /** 动作类型值：更新节点属性 */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    /** 创建者 ID */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    /** 子实体对象 */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    /** 客户端版本号防重放校验戳 */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    /** 任务完成状态标记 */
    public final static String GTASK_JSON_COMPLETED = "completed";

    /** 节点当前所属的列表 ID */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    /** 用户的默认任务列表 ID */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    /** 节点的逻辑删除状态标记 */
    public final static String GTASK_JSON_DELETED = "deleted";

    /** 节点移动操作的目标列表 ID */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    /** 节点移动操作的目标父级节点 ID */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    /** 节点移动操作的目标父级节点类型（通常为 GROUP） */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    /** 实体的差量更新数据块，包裹具体的属性修改 */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    /** 实体类型键名（如 TASK 或 GROUP） */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    /** 全量获取时是否包含已删除的节点 */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    /** 节点全局唯一标识 (GID) */
    public final static String GTASK_JSON_ID = "id";

    /** 节点在同级兄弟节点中的排序索引 */
    public final static String GTASK_JSON_INDEX = "index";

    /** 节点的最后修改时间戳（云端维护） */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    /** 最新同步点游标 */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    /** 所属的列表 ID */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    /** 任务列表集合键名 */
    public final static String GTASK_JSON_LISTS = "lists";

    /** 节点名称（标题） */
    public final static String GTASK_JSON_NAME = "name";

    /** 创建成功后服务器返回的新分配 GID */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    /** 任务备注信息（在同步方案中常被征用为 JSON 元数据载体） */
    public final static String GTASK_JSON_NOTES = "notes";

    /** 父级节点 ID */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    /** 前置兄弟节点 ID（用于确定在同级列表中的绝对排序位置） */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    /** 请求的执行结果集合 */
    public final static String GTASK_JSON_RESULTS = "results";

    /** 节点移动操作的源列表 ID */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    /** 任务节点集合键名 */
    public final static String GTASK_JSON_TASKS = "tasks";

    /** 通用类型键名 */
    public final static String GTASK_JSON_TYPE = "type";

    /** 节点类型值：组/文件夹（TaskList） */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    /** 节点类型值：普通任务（Task） */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    /** 用户信息对象键名 */
    public final static String GTASK_JSON_USER = "user";

    // =========================================================================
    // 小米便签自定义云同步扩展协议常量
    // =========================================================================

    /**
     * 云端文件夹前缀标识。
     * 由于 Google Task 原生不支持多级文件夹概念，为了区分小米便签的文件夹与用户普通列表，
     * 所有同步到云端的本地文件夹名称前都会被强制追加此字符串。
     * 拼写错误 "PREFFIX" 为历史遗留（应为 PREFIX），为保证数据兼容性在此保持原样。
     */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /** 默认根目录文件夹的虚拟名称 */
    public final static String FOLDER_DEFAULT = "Default";

    /** 通话记录归属文件夹的虚拟名称 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /** 专用于存储便签富文本属性与层级配置等隐藏元数据的系统级文件夹名称 */
    public final static String FOLDER_META = "METADATA";

    /** JSON 键名：在元数据中映射便签的 Google Task ID */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /** JSON 键名：在元数据中包裹便签的主表 (Note) 属性 */
    public final static String META_HEAD_NOTE = "meta_note";

    /** JSON 键名：在元数据中包裹便签的附表 (Data) 内容集合 */
    public final static String META_HEAD_DATA = "meta_data";

    /**
     * 元数据载体任务的警告名称。
     * 由于元数据被以文本形式存储在云端的隐藏任务中，为了防止用户在网页端误删或误改，
     * 强制将该任务命名为此警告标语。
     */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}