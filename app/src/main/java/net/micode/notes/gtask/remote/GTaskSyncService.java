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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Google Task 后台同步服务。
 * <p>继承自 {@link Service}。主要职责是将长耗时的网络同步任务（{@link GTaskASyncTask}）
 * 挂载到 Android 系统的服务组件上，提升进程优先级，防止在同步过程中因 Activity 销毁而导致任务被系统强杀。
 * 同时，它充当了底层的状态广播站，向外部 UI 层（如应用列表、设置界面）推送同步进度。</p>
 */
public class GTaskSyncService extends Service {

    // =========================================================================
    // Intent 指令与广播 Action 常量定义区
    // =========================================================================

    /** Intent Extra 键名：用于指定控制同步服务的动作类型 */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /** 动作指令：启动同步流程 */
    public final static int ACTION_START_SYNC = 0;

    /** 动作指令：取消正在进行的同步流程 */
    public final static int ACTION_CANCEL_SYNC = 1;

    /** 动作指令：无效操作防呆标识 */
    public final static int ACTION_INVALID = 2;

    /** 全局广播 Action 名称：用于向外发送同步进度的广播 */
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    /** 广播 Extra 键名：标识当前是否处于同步状态 (boolean) */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /** 广播 Extra 键名：当前同步流程的文本提示信息 (String) */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    // =========================================================================
    // 全局状态句柄
    // =========================================================================

    /** * 全局静态的异步同步任务实例。
     * 用于确保在同一个应用生命周期内，只存在一个激活的同步线程。
     */
    private static GTaskASyncTask mSyncTask = null;

    /** 全局静态的同步进度缓存文本 */
    private static String mSyncProgress = "";

    /**
     * 内部启动同步逻辑。
     * 若当前无同步任务运行，则实例化新的 AsyncTask 并挂载完成回调。
     */
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                @Override
                public void onComplete() {
                    // 任务执行完毕（无论成功或失败），重置全局句柄并通知 UI 层
                    mSyncTask = null;
                    sendBroadcast("");
                    // 核心生命周期管理：任务结束后主动结束 Service 以释放系统内存
                    stopSelf();
                }
            });
            // 发送初始空状态广播，通知 UI 进入加载态
            sendBroadcast("");
            mSyncTask.execute();
        }
    }

    /**
     * 内部取消同步逻辑。
     * 将中断信号传递给底层正在运行的 AsyncTask。
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    public void onCreate() {
        // 服务首次创建时，确保任务句柄处于干净状态
        mSyncTask = null;
    }

    /**
     * 服务的核心命令分发中心。
     * 当外部组件通过 startService() 唤起本服务时触发。
     *
     * @param intent  包含动作指令的 Intent
     * @param flags   启动请求的附加数据
     * @param startId 启动请求的唯一整型标识
     * @return 系统的重启调度策略（START_STICKY 表示被杀后尝试重启）
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 防呆设计：判断 Intent 及其携带的 Bundle 是否有效
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
                switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                    case ACTION_START_SYNC:
                        startSync();
                        break;
                    case ACTION_CANCEL_SYNC:
                        cancelSync();
                        break;
                    default:
                        break;
                }
                return START_STICKY;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 当系统面临极度内存匮乏时触发。
     * 此时应当主动中断重度消耗内存的同步任务，以保证前台交互的流畅性或避免 OOM。
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 绑定服务接口。
     * 由于本类设计为 Start 模式（即用即走）的后台服务，不提供基于 IPC 的交互接口，故返回 null。
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 封装的内部广播发送器。
     * 将当前的同步状态（运行中标志、进度文本）打包发送到系统广播总线上，供 Activity 监听刷新。
     *
     * @param msg 需要向外广播的进度或提示信息
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    // =========================================================================
    // 对外暴露的静态便捷控制 API
    // =========================================================================

    /**
     * 暴露给外部调用的启动同步入口。
     *
     * @param activity 触发同步的宿主 Activity，用于后续可能的 Google 账户认证弹窗
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 暴露给外部调用的取消同步入口。
     *
     * @param context 调起请求的应用上下文
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 获取当前是否正在进行同步的全局状态。
     *
     * @return 存在运行中的任务时返回 true
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取当前同步任务最新推送到服务层的进度提示文本。
     *
     * @return 进度字符串
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}