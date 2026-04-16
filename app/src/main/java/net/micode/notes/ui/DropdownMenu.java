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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 这是一个下拉菜单的辅助封装类。
 * 作用是将一个普通的 Button 控件绑定系统原生的 PopupMenu，
 * 使得用户点击按钮时，能够在按钮下方/附近弹出一个下拉菜单。
 */
public class DropdownMenu {
    private Button mButton;
    private PopupMenu mPopupMenu;
    private Menu mMenu;

    /**
     * 构造一个新的下拉菜单控制器。
     * @param context 上下文
     * @param button 触发下拉菜单的按钮对象
     * @param menuId 要加载的菜单资源文件 (R.menu.xxx)
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon); // 设置下拉图标
        mPopupMenu = new PopupMenu(context, mButton); // 实例化弹出菜单
        mMenu = mPopupMenu.getMenu();
        // 从 XML 资源加载菜单项
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        
        // 绑定按钮点击事件，点击时展示弹出菜单
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置菜单项的点击事件监听器。
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据 ID 查找特定的菜单项，有助于在运行时动态修改菜单（如控制可见性等）。
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮上显示的标题文本。
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
