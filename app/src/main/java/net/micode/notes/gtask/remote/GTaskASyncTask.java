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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * Google Task 异步同步任务控制器。
 * 负责在后台线程执行云端与本地的数据同步逻辑，通过系统的 NotificationManager 实时更新同步进度，
 * 并将最终的同步结果回传给监听器 (Listener) 或广播给服务 (Service)。
 * * <p>注意：{@link AsyncTask} 在现代 Android 开发中已被标记为废弃（Deprecated），
 * 生产环境中建议逐步迁移至 Kotlin Coroutines 或 WorkManager 以获得更好的生命周期管理。</p>
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    /** * 同步状态通知的全局唯一 ID。
     * 用于在状态更新时覆盖旧通知，而不是在状态栏产生多个通知条目。
     */
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步任务完成的回调接口。
     */
    public interface OnCompleteListener {
        /**
         * 当 AsyncTask 的执行流程结束（无论是成功、失败还是取消）时被调用。
         */
        void onComplete();
    }

    private Context mContext;

    /** 系统通知服务管理器 */
    private NotificationManager mNotifiManager;

    /** 真正的 Google Task 同步业务引擎 */
    private GTaskManager mTaskManager;

    /** 生命周期回调监听器 */
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造函数。
     * 初始化任务执行所需的系统服务与回调接口。
     *
     * @param context  应用程序上下文（建议传入 ApplicationContext 防止泄漏）
     * @param listener 任务执行完毕后的回调接口，可为 null
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 主动阻断当前的同步流程。
     * 通过调用底层引擎的标志位来实现平滑取消，避免强杀线程造成数据不一致。
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 对外暴露的进度更新接口。
     * 允许内部的 GTaskManager 在同步过程中将状态文本推送到主线程。
     *
     * @param message 需要展示给用户的进度提示信息
     */
    public void publishProgess(String message) {
        // 调用 AsyncTask 底层的受保护方法，触发 onProgressUpdate
        publishProgress(new String[] {
                message
        });
    }

    /**
     * 构造并推送系统级状态栏通知。
     *
     * @param tickerId 状态栏顶部闪过的提示语的 String 资源 ID，同时也作为判定状态的依据
     * @param content  通知抽屉内显示的详细文本内容
     */
    private void showNotification(int tickerId, String content) {
        PendingIntent pendingIntent;

        // 交互逻辑：如果同步成功，点击通知跳转到便签主列表；
        // 如果失败或取消，跳转到设置页以检查账户状态。
        if (tickerId != R.string.ticker_success) {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesPreferenceActivity.class), PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesListActivity.class), PendingIntent.FLAG_IMMUTABLE);
        }

        Notification.Builder builder = new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.notification)
                .setTicker(mContext.getString(tickerId))
                .setWhen(System.currentTimeMillis())
                .setDefaults(Notification.DEFAULT_LIGHTS) // 仅使用呼吸灯，避免频繁震动打扰用户
                .setAutoCancel(true) // 用户点击后自动清除通知
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setContentIntent(pendingIntent);

        // 兼容新版 Android (API 26+) 的 Notification Channel 限制 (为了防闪退简单处理)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String channelId = "sync_channel";
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Sync", NotificationManager.IMPORTANCE_DEFAULT);
            mNotifiManager.createNotificationChannel(channel);
            builder.setChannelId(channelId);
        }

        // 推送通知
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, builder.build());
    }

    /**
     * 核心异步执行体。
     * 该方法在独立的工作线程（Worker Thread）中执行，严禁在此处直接操作 UI 组件。
     *
     * @param unused 无入参需求
     * @return 引擎执行完毕后返回的状态码（参考 {@link GTaskManager} 的 STATE 常量）
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 通知主线程：开始准备登录
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));

        // 阻塞调用引擎进行全量同步
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 进度更新回调。
     * 运行在主线程（UI Thread），负责接收 doInBackground 传来的进度信息并刷新视图。
     *
     * @param progress 包含提示信息的字符串数组（按设计只取索引 0）
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);

        // 【合规性审查批注】: 这种强转 Context 的写法是极不规范的紧耦合设计。
        // 如果 Context 不是 GTaskSyncService，广播机制将失效。生产环境中应采用
        // LocalBroadcastManager 或 EventBus 进行完全解耦的通信。
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 任务收尾回调。
     * 运行在主线程，依据状态码进行不同的业务处理及 UI 反馈。
     *
     * @param result doInBackground 返回的同步结果状态码
     */
    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            // 同步成功，固化当前时间戳以备下次增量对比
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }

        if (mOnCompleteListener != null) {
            // 【合规性审查批注】: 在 AsyncTask 的结束回调（已处于主线程）中，再次 new Thread() 
            // 去执行 listener。如果 listener.onComplete() 中包含 UI 操作（比如 dismiss Dialog），
            // 将直接导致 "CalledFromWrongThreadException" 崩溃。
            // 建议：直接调用 mOnCompleteListener.onComplete()，避免无谓的线程切换风险。
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}