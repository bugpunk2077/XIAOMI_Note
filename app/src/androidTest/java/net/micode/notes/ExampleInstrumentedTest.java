package net.micode.notes;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
// 表示这是一个 Android 仪器化测试（运行在手机/模拟器上）
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    // 标记这是一个测试方法
    @Test
    public void useAppContext() {
        // 获取当前被测试应用的 Context（上下文）
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 断言：检查应用的包名是否等于 net.micode.notes
        // 用来验证测试环境是否正确
        assertEquals("net.micode.notes", appContext.getPackageName());
    }
}