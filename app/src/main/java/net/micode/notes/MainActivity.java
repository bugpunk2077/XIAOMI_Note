package net.micode.notes;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // Activity 创建时执行
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 启用全屏/沉浸式布局（EdgeToEdge 库）
        EdgeToEdge.enable(this);

        // 加载页面布局 activity_main.xml
        setContentView(R.layout.activity_main);

        // 设置系统栏（状态栏、导航栏）内边距，防止布局被遮挡
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // 获取系统栏的边距
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 给布局设置 padding，避开状态栏和导航栏
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}