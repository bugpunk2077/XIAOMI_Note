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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 这是一个闹钟接收器（广播接收器）。
 * 核心作用是拦截由系统 AlarmManager 在指定时间发出的定时广播事件，
 * 并随即唤起便签应用自己的闹铃提示界面（AlarmAlertActivity）。
 */
public class AlarmReceiver extends BroadcastReceiver {
    
    /**
     * 当接收到便签提醒广播时执行的回调
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 重定向当前 Intent 的目标类为闹钟通知的弹窗 Activity
        intent.setClass(context, AlarmAlertActivity.class);
        
        // 由于当前的执行上下文为 BroadcastReceiver 而不是普通的 Activity（非任务栈内），
        // 需将其设为 FLAG_ACTIVITY_NEW_TASK ，告诉系统新开辟栈帧去启动这个 Activity。
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        // 启动弹窗 Activity
        context.startActivity(intent);
    }
}
