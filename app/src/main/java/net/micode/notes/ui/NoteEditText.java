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
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义的 EditText 组件，针对便签应用的列表编辑模式（清单模式）以及快捷键响应进行了专门的增强。
 *
 * 核心功能：
 * 1. 深度拦截退格键（Del）和回车键（Enter），并通过接口委派给外部，以此实现清单行动态增加与删除（类似 To-Do 列表的联动）。
 * 2. 拦截触摸事件精确定位光标。
 * 3. 拦截长按出现的上下文菜单，增加对电话、邮件、网页链接的快捷操作。
 */
public class NoteEditText extends EditText {
    private static final String TAG = "NoteEditText";
    private int mIndex; // 记录当前输入框在清单模式列表中的索引位置
    private int mSelectionStartBeforeDelete; // 记录在执行删除操作前的光标位置，用于判断能否删除整条输入框

    private static final String SCHEME_TEL = "tel:" ;
    private static final String SCHEME_HTTP = "http:" ;
    private static final String SCHEME_EMAIL = "mailto:" ;

    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * 该监听器主要由包裹它的 {@link NoteEditActivity} 实现，
     * 用于处理编辑框的增、删改操作。
     */
    public interface OnTextViewChangeListener {
        /**
         * 当按下删除键 {@link KeyEvent#KEYCODE_DEL} 时该方法会被触发。
         * 如果当前输入框的光标位置位于最前面（且不是第一行），此回调用以把光标和剩余文本移至上一行并销毁此行。
         *
         * @param index 当前被尝试删除行的索引
         * @param text 被删除行光标后方剩余的被保护文本
         */
        void onEditTextDelete(int index, String text);

        /**
         * 当按下回车键 {@link KeyEvent#KEYCODE_ENTER} 时该方法会被触发。
         * 其目的是在当前输入框所在的行下方插入一个新的、带有复选框（如果需要）的输入行。
         *
         * @param index 应该新插入文本行的索引位置
         * @param text  位于光标后面的新半截文本
         */
        void onEditTextEnter(int index, String text);

        /**
         * 当当前行的内容或者焦点改变时触发，用于提醒外部隐藏或重制当前栏的额外选项
         */
        void onTextChange(int index, boolean hasText);
    }

    private OnTextViewChangeListener mOnTextViewChangeListener;

    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:

                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();

                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);
                Selection.setSelection(getText(), off);
                break;
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                // 如果当前处在清单模式并且有观察者，阻止 Android 原生在 EditText 中直接换行的默认操作
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                // 只要按下删除键，立刻记录光标所在位置。
                // 这个位置留给 onKeyUp(松开按键) 判断当前是否满足合并消除整行的条件
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    // 当松开 Del 键时，如果刚才按键时发现光标正好在最前端，并且不是整篇笔记的第一行
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        // 触发回调，把当前行（index）删掉，并把剩余字符 text 送给上一行合并
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    // 当松开 Enter 回车键时，从当前光标位置处断开文本
                    int selectionStart = getSelectionStart();
                    // 被断开光标后的半截文本
                    String text = getText().subSequence(selectionStart, length()).toString();
                    // 截取光标前半截作为当前行新文本
                    setText(getText().subSequence(0, selectionStart));
                    // 触发换清单行操作新加入下一行（index+1），并带有被隔断出的文本
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // goto a new intent
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}
