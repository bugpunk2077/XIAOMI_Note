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

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 核心 UI 资源解析与映射工具类。
 * <p>集中管理便签应用中主题颜色、字体大小等逻辑抽象值（整型 ID）到 Android 物理层资源（R.drawable.*, R.style.*）的转换。
 * 这种设计有效隔离了数据模型层与 UI 渲染层，使得数据库中仅需存储极小的整型标记即可还原复杂的界面呈现。</p>
 */
public class ResourceParser {

    // =========================================================================
    // 便签主题颜色逻辑 ID 常量定义
    // =========================================================================
    public static final int YELLOW           = 0;
    public static final int BLUE             = 1;
    public static final int WHITE            = 2;
    public static final int GREEN            = 3;
    public static final int RED              = 4;

    /** 默认的便签主题颜色 (黄色) */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // =========================================================================
    // 便签字体大小逻辑 ID 常量定义
    // =========================================================================
    public static final int TEXT_SMALL       = 0;
    public static final int TEXT_MEDIUM      = 1;
    public static final int TEXT_LARGE       = 2;
    public static final int TEXT_SUPER       = 3;

    /** 默认的便签字体大小 (中号) */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 便签编辑界面（NoteEditActivity）的背景资源映射器。
     */
    public static class NoteBgResources {
        /** 编辑区主背景切图数组，索引与逻辑颜色 ID 严格对应 */
        private final static int [] BG_EDIT_RESOURCES = new int [] {
                R.drawable.edit_yellow,
                R.drawable.edit_blue,
                R.drawable.edit_white,
                R.drawable.edit_green,
                R.drawable.edit_red
        };

        /** 编辑区顶部标题栏（TitleBar）背景切图数组 */
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
                R.drawable.edit_title_yellow,
                R.drawable.edit_title_blue,
                R.drawable.edit_title_white,
                R.drawable.edit_title_green,
                R.drawable.edit_title_red
        };

        /**
         * 获取便签主编辑区的背景资源 ID。
         * @param id 逻辑颜色 ID (0-4)
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 获取便签顶部标题栏的背景资源 ID。
         * @param id 逻辑颜色 ID (0-4)
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 业务策略：获取新建便签时的默认背景颜色 ID。
     * <p>包含读取用户偏好设置的逻辑：如果用户在设置中开启了“新建便签使用随机背景色”
     * （PREFERENCE_SET_BG_COLOR_KEY），则利用 Math.random() 动态散列返回一种颜色；否则返回标准默认值。</p>
     *
     * @param context 应用程序上下文
     * @return 决议后的背景颜色逻辑 ID
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 便签列表界面（NotesListActivity）的列表项背景资源映射器。
     * <p>由于列表采用了按时间分组（Group）的视觉设计，为了实现圆角包裹效果，
     * 同一个颜色需要依据条目在分组内的相对位置（首项、中间项、末项、或者孤立单项）匹配不同的切图。</p>
     */
    public static class NoteItemBgResources {
        /** 列表分组内【首项】切图（仅顶部有圆角） */
        private final static int [] BG_FIRST_RESOURCES = new int [] {
                R.drawable.list_yellow_up,
                R.drawable.list_blue_up,
                R.drawable.list_white_up,
                R.drawable.list_green_up,
                R.drawable.list_red_up
        };

        /** 列表分组内【中间项】切图（无圆角，直角衔接） */
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
                R.drawable.list_yellow_middle,
                R.drawable.list_blue_middle,
                R.drawable.list_white_middle,
                R.drawable.list_green_middle,
                R.drawable.list_red_middle
        };

        /** 列表分组内【末项】切图（仅底部有圆角） */
        private final static int [] BG_LAST_RESOURCES = new int [] {
                R.drawable.list_yellow_down,
                R.drawable.list_blue_down,
                R.drawable.list_white_down,
                R.drawable.list_green_down,
                R.drawable.list_red_down,
        };

        /** 列表分组内【孤立项】切图（上下均有圆角） */
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
                R.drawable.list_yellow_single,
                R.drawable.list_blue_single,
                R.drawable.list_white_single,
                R.drawable.list_green_single,
                R.drawable.list_red_single
        };

        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /**
         * 获取通用的文件夹列表项背景资源。
         */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * 桌面小部件（Widget）的背景资源映射器。
     * 根据小部件占据的网格规格（2x2 或 4x4）以及颜色 ID 提供对应的切图。
     */
    public static class WidgetBgResources {
        /** 2x2 规格的小部件背景资源数组 */
        private final static int [] BG_2X_RESOURCES = new int [] {
                R.drawable.widget_2x_yellow,
                R.drawable.widget_2x_blue,
                R.drawable.widget_2x_white,
                R.drawable.widget_2x_green,
                R.drawable.widget_2x_red,
        };

        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        /** 4x4 规格的小部件背景资源数组 */
        private final static int [] BG_4X_RESOURCES = new int [] {
                R.drawable.widget_4x_yellow,
                R.drawable.widget_4x_blue,
                R.drawable.widget_4x_white,
                R.drawable.widget_4x_green,
                R.drawable.widget_4x_red
        };

        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 字体样式（TextAppearance）资源映射器。
     * 负责将字号逻辑 ID 转换为 res/values/styles.xml 中定义的标准 Android 字体样式属性。
     */
    public static class TextAppearanceResources {
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
                R.style.TextAppearanceNormal,
                R.style.TextAppearanceMedium,
                R.style.TextAppearanceLarge,
                R.style.TextAppearanceSuper
        };

        /**
         * 获取对应的字体 Style 资源 ID。
         * 包含针对脏数据或旧版本冗余数据的越界保护机制。
         *
         * @param id 字体大小逻辑 ID
         * @return Android Style 资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: Fix bug of store the resource id in shared preference.
             * The id may larger than the length of resources, in this case,
             * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
             * * [合规性防御批注]：处理历史遗留 Bug。早期的版本可能错误地将真实的资源 ID
             * （如 0x7F0...）直接写入了 SharedPreferences 中，导致按索引取数组时引发
             * ArrayIndexOutOfBoundsException。此处执行越界兜底，强转回默认中号字体。
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取当前应用支持的字体档位总数量。
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}