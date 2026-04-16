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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 这是一个广播接收器，通常用于在系统开机（例如接收到 BOOT_COMPLETED 广播）或应用启动时，
 * 重新初始化并向系统注册所有未来的便签闹钟提醒。
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    private static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE
    };

    private static final int COLUMN_ID                = 0;
    private static final int COLUMN_ALERTED_DATE      = 1;

    /**
     * 接收到广播时的回调方法。它会从数据库中加载未过期的闹钟数据并重新设置系统服务。
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. 获取当前系统的时间戳
        long currentDate = System.currentTimeMillis();
        
        // 2. 查询位于数据库中、类型为便签（TYPE_NOTE）且设定提醒时间还没有过期的（大于当前时间）数据行
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },
                null);

        if (c != null) {
            // 如果查询结果有数据，将数据库游标移动到第一项
            if (c.moveToFirst()) {
                do {
                    // 获取当前这条便签的提醒时间
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    
                    // 构建一个目标为AlarmReceiver的广播Intent，系统到点触发时将发送它
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 将便签的ID携带在内容URI之后，以供后续AlarmAlertActivity加载对应便签摘要
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));

                    int pendingIntentFlags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ?
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                            PendingIntent.FLAG_UPDATE_CURRENT;
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, pendingIntentFlags);
                    
                    // 获取设备的系统级服务: AlarmManager (闹铃服务)
                    AlarmManager alermManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
                    // 设置一个RTC_WAKEUP的绝对时间闹钟，表示该到点时会唤醒设备，执行指定的PendingIntent
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);
                } while (c.moveToNext()); // 遍历整个结果集，将其余的有效记录一个个添加为系统闹钟
            }
            c.close(); // 3. 必须及时关闭游标以释放底层数据库资源
        }
    }
}
