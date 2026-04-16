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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 这是一个包含 DateTimePicker（自定义日期时间选择器）的报警/提醒设置对话框。
 * 用于以弹窗的形式让用户选择需要的日期和时间。
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    private Calendar mDate = Calendar.getInstance(); // 内部持有选中的日历实例
    private boolean mIs24HourView; // 是否使用24小时制视图
    private OnDateTimeSetListener mOnDateTimeSetListener; // 回调监听：当用户点击确定时触发
    private DateTimePicker mDateTimePicker; // 对话框实际展示的日期时间选择器视图组件

    /**
     * 对外暴露的接口监听器，当在对话框完成时间选取后被调用。
     */
    public interface OnDateTimeSetListener {
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造函数：初始化并装配 DateTimePicker 组件
     * @param context 当前界面上下文
     * @param date 给定一个初始的毫秒时间戳来初始化组件选中时间
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        mDateTimePicker = new DateTimePicker(context);
        
        // 将自定义的时间视图设为 AlertDialog 的主界面
        setView(mDateTimePicker);
        
        // 绑定组件内部的值变化回调监听，动态同步并更新弹窗的标题
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                updateTitle(mDate.getTimeInMillis()); // 更新上方标题展示的内容
            }
        });
        
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0); // 抹除不可配置的秒数位，归零处理
        
        // 推送给 DateTimePicker 显示初始化时间
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        
        // 为 AlertDialog 挂配底部确认与取消按钮
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
        
        // 默认根据手机系统的配置初始化是否采用24小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        
        // 起始时刻直接利用设定好的时间更新弹窗标题
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 强制改变为12小时制/24小时制视图
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 绑定最后选中时间后的事件回调接口
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 私有方法：用于将时间戳解析成具体的日时分，更新在弹窗的 Title 所属区域，直观显示结果。
     */
    private void updateTitle(long date) {
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |
            DateUtils.FORMAT_SHOW_DATE |
            DateUtils.FORMAT_SHOW_TIME;
        
        // DateUtils.FORMAT_24HOUR 和 FORMAT_12HOUR 是对应的，这里通过 mIs24HourView 控制展示表现
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 处理底部的“确定”按钮点击事件，调用绑定的观察者监听，把确定的日期派发出去
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }

}