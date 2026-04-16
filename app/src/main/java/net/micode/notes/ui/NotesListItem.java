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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;


/**
 * 列表中的每一条笔记/文件夹条目视图
 * 负责显示单个笔记、文件夹、通话记录的界面样式
 */
public class NotesListItem extends LinearLayout {
    private ImageView mAlert;          // 提醒图标（闹钟/通话记录）
    private TextView mTitle;           // 标题/内容预览
    private TextView mTime;            // 最后修改时间
    private TextView mCallName;        // 通话记录的联系人名称
    private NoteItemData mItemData;    // 当前条目的数据
    private CheckBox mCheckBox;        // 多选模式的勾选框

    // 构造方法：加载布局，初始化控件
    public NotesListItem(Context context) {
        super(context);
        // 加载列表条目布局
        inflate(context, R.layout.note_item, this);

        // 初始化各个控件
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 核心方法：绑定数据到视图
     * @param context 上下文
     * @param data 笔记/文件夹数据
     * @param choiceMode 是否多选模式
     * @param checked 是否选中
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // 如果是多选模式，并且是笔记，显示勾选框
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            // 否则隐藏勾选框
            mCheckBox.setVisibility(View.GONE);
        }

        // 保存当前条目数据
        mItemData = data;

        // ===================== 分类型显示界面 =====================
        // 1. 如果是【通话记录文件夹】
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);          // 隐藏通话人名
            mAlert.setVisibility(View.VISIBLE);          // 显示图标
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            // 标题显示：通话记录 + 里面的文件数量
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record); // 设置通话记录图标
        }

        // 2. 如果是【通话记录文件夹里的笔记】
        else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);  // 显示联系人名称
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));

            // 如果有提醒，显示闹钟图标
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        }

        // 3. 普通笔记 / 普通文件夹
        else {
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            // 如果是【文件夹】
            if (data.getType() == Notes.TYPE_FOLDER) {
                // 显示文件夹名 + 包含笔记数量
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
                mAlert.setVisibility(View.GONE); // 文件夹不显示提醒图标
            }

            // 如果是【普通笔记】
            else {
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                // 有提醒 → 显示闹钟图标
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // 设置最后修改时间（友好格式）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // 设置条目背景（区分第一条、最后一条、中间条、文件夹）
        setBackground(data);
    }

    /**
     * 根据条目类型设置不同的背景样式
     * 让列表看起来有分组、圆角、间隔效果
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId(); // 背景颜色ID

        // 如果是笔记，根据位置设置不同背景（第一条、最后一条、中间、单独）
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            // 文件夹使用统一背景
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前条目的数据
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}