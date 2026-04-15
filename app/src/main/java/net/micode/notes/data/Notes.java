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

package net.micode.notes.data;

import android.net.Uri;

/**
 * 便签应用的数据契约类（Contract Class）。
 * 集中管理数据库的 Authority、Content URI、表结构列名以及系统级常量。
 * 供 ContentProvider 及其调用方（如 Activity、Service、Widget）统一引用，确保数据访问的一致性。
 */
public class Notes {
    /** * ContentProvider 的全局唯一授权码 (Authority)
     */
    public static final String AUTHORITY = "micode_notes";

    public static final String TAG = "Notes";

    // --- 数据项类型常量 ---
    public static final int TYPE_NOTE     = 0; // 普通便签
    public static final int TYPE_FOLDER   = 1; // 文件夹
    public static final int TYPE_SYSTEM   = 2; // 系统级数据

    // --- 系统级文件夹标识符 (ID) ---
    /** 默认根文件夹 ID */
    public static final int ID_ROOT_FOLDER = 0;
    /** 临时文件夹 ID，用于存放尚未归属任何文件夹的散落便签 */
    public static final int ID_TEMPARAY_FOLDER = -1;
    /** 通话记录文件夹 ID，专用于存储通话相关的便签记录 */
    public static final int ID_CALL_RECORD_FOLDER = -2;
    /** 回收站文件夹 ID，存放已删除待彻底清除的便签 */
    public static final int ID_TRASH_FOLER = -3;

    // --- Intent 传参键值常量 (Extras Keys) ---
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // --- 桌面小部件 (Widget) 规格常量 ---
    public static final int TYPE_WIDGET_INVALIDE      = -1; // 无效规格
    public static final int TYPE_WIDGET_2X            = 0;  // 2x2 规格小部件
    public static final int TYPE_WIDGET_4X            = 1;  // 4x4 规格小部件

    /**
     * 数据类型常量字典。
     * 定义不同类型便签的 MIME 类型标识。
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    /**
     * 便签主表的 Content URI。
     * 用于对便签本身和文件夹的层级属性进行 CURD 操作。
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 详情数据表的 Content URI。
     * 用于查询具体便签的内部文本内容或通话记录数据。
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 便签主表 (Note Table) 的列名定义。
     * 记录便签/文件夹的基础元数据及层级拓扑关系。
     */
    public interface NoteColumns {
        /**
         * 记录的唯一主键 ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * 父节点的 ID，用于构建文件夹层级树
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * 记录创建的时间戳
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 记录最后一次修改的时间戳
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 设置的提醒闹钟时间戳
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 文件夹名称，或便签的文本摘要（截断后的正文）
         * <P> 类型: TEXT </P>
         */
        public static final String SNIPPET = "snippet";

        /**
         * 绑定的桌面小部件 ID，若未绑定则为空
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * 绑定的桌面小部件规格类型
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * 便签背景颜色的资源或枚举 ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * 附件标识符。文本便签为 0，多媒体便签（含录音、图片等）至少为 1
         * <P> 类型: INTEGER </P>
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * 当当前记录为文件夹时，该字段表示其包含的子便签数量
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * 节点类型标志：文件夹或普通便签 (参考 {@link Notes#TYPE_NOTE} 等常量)
         * <P> 类型: INTEGER </P>
         */
        public static final String TYPE = "type";

        /**
         * 云同步记录的最后一次同步 ID (通常对应云端的主键)
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * 脏数据标志位，指示本地数据是否在上次同步后被修改过
         * <P> 类型: INTEGER (1 为真，0 为假) </P>
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * 移动到临时文件夹之前的原始父文件夹 ID，用于撤销操作
         * <P> 类型 : INTEGER </P>
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * 绑定的 Google Task (GTask) 远程 ID
         * <P> 类型 : TEXT </P>
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * 数据版本控制号，通常用于乐观锁或同步冲突解决
         * <P> 类型 : INTEGER (long) </P>
         */
        public static final String VERSION = "version";
    }

    /**
     * 详情数据表 (Data Table) 的列名定义。
     * 设计类似于 Android 原生联系人数据库的泛型数据结构。
     */
    public interface DataColumns {
        /**
         * 记录的唯一主键 ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * 该行数据的 MIME 类型，决定了后续 DATA1-DATA5 字段的解析逻辑
         * <P> 类型: Text </P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * 外部键，关联到 NoteColumns 中的 {@link NoteColumns#ID}
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String NOTE_ID = "note_id";

        /**
         * 数据行的创建时间戳
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 数据行的最后修改时间戳
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 数据行的核心文本内容
         * <P> 类型: TEXT </P>
         */
        public static final String CONTENT = "content";

        /**
         * 泛型数据字段 1，具体语义由 {@link #MIME_TYPE} 决定，通常用于存储 INTEGER 类型
         * <P> 类型: INTEGER </P>
         */
        public static final String DATA1 = "data1";

        /**
         * 泛型数据字段 2，具体语义由 {@link #MIME_TYPE} 决定，通常用于存储 INTEGER 类型
         * <P> 类型: INTEGER </P>
         */
        public static final String DATA2 = "data2";

        /**
         * 泛型数据字段 3，具体语义由 {@link #MIME_TYPE} 决定，通常用于存储 TEXT 类型
         * <P> 类型: TEXT </P>
         */
        public static final String DATA3 = "data3";

        /**
         * 泛型数据字段 4，具体语义由 {@link #MIME_TYPE} 决定，通常用于存储 TEXT 类型
         * <P> 类型: TEXT </P>
         */
        public static final String DATA4 = "data4";

        /**
         * 泛型数据字段 5，具体语义由 {@link #MIME_TYPE} 决定，通常用于存储 TEXT 类型
         * <P> 类型: TEXT </P>
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本便签实体的常量定义。
     * 将泛型字段映射为具体的文本便签属性。
     */
    public static final class TextNote implements DataColumns {
        /**
         * 标识当前文本的显示模式是否为待办清单 (Check List)。
         * 映射自通用数据字段 DATA1。
         * <P> 类型: Integer (1: 清单模式, 0: 普通文本模式) </P>
         */
        public static final String MODE = DATA1;

        public static final int MODE_CHECK_LIST = 1;

        // MIME Types 及 URIs
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录便签实体的常量定义。
     * 将泛型字段映射为具体的通话记录属性。
     */
    public static final class CallNote implements DataColumns {
        /**
         * 本条通话记录的通话时间戳。
         * 映射自通用数据字段 DATA1。
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 本条通话记录对应的电话号码。
         * 映射自通用数据字段 DATA3。
         * <P> 类型: TEXT </P>
         */
        public static final String PHONE_NUMBER = DATA3;

        // MIME Types 及 URIs
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}