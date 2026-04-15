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

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 针对 Google Task 同步的元数据管理类。
 * 继承自 {@link Task}，但并不代表一个实际的用户可见任务。
 * 由于 Google Task API 的结构限制，本类利用任务的 notes（备注）字段来持久化存储
 * 关联的系统级元数据（以 JSON 字符串形式），从而实现本地和云端的状态同步追踪。
 */
public class MetaData extends Task {
    private static final String TAG = MetaData.class.getSimpleName();

    /** 关联的 Google Task 全局唯一标识符 (GID) */
    private String mRelatedGid = null;

    /**
     * 将关联的 GID 注入到元数据对象中，并将序列化后的结果保存为该虚拟任务的内容。
     *
     * @param gid      需要绑定的 Google Task ID
     * @param metaInfo 包含其他同步状态或元数据的 JSON 容器
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
            // 生产环境注意：此处若 put 失败，下方的 toString() 将存储不完整的数据。
        }
        setNotes(metaInfo.toString());
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取当前元数据记录所关联的 Google Task ID。
     *
     * @return 关联的 GID 字符串。如果尚未解析或解析失败，则返回 null。
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 评估该元数据是否具有持久化价值。
     * 只有当内部的 notes 字段（承载 JSON 数据）非空时，才认为该元数据有效并允许保存。
     *
     * @return 如果载荷存在返回 true，否则返回 false
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 解析从 Google Task 服务器拉取的远程 JSON 数据节点。
     * 提取其中的 notes 字段内容，并反序列化以恢复本地所需的 mRelatedGid。
     *
     * @param js Google Task API 返回的原始 JSON 任务对象
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 禁用操作：元数据对象不支持从本地通用 JSON 结构反向构建。
     *
     * @param js 本地构造的 JSON 对象
     * @throws UnsupportedOperationException 该类不支持此操作
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // 重构标准：原代码抛出 IllegalAccessError 属于严重的底层错误滥用
        throw new UnsupportedOperationException("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 禁用操作：元数据对象不支持导出为本地通用 JSON 结构。
     *
     * @return 无返回值
     * @throws UnsupportedOperationException 该类不支持此操作
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new UnsupportedOperationException("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 禁用操作：元数据作为系统底层同步记录，不参与常规数据游标的同步动作判定。
     *
     * @param c 数据游标
     * @return 无返回值
     * @throws UnsupportedOperationException 该类不支持此操作
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new UnsupportedOperationException("MetaData:getSyncAction should not be called");
    }

}