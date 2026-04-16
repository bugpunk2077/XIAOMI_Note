package net.micode.notes;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
/**
 * 本地单元测试示例（仅在电脑 JVM 上运行，不依赖 Android 设备）
 */
public class ExampleUnitTest {

    /**
     * 示例测试方法：验证加法计算是否正确
     * 作用：测试框架是否能正常工作、环境是否可用
     */
    @Test
    public void addition_isCorrect() {
        // 断言：判断 2+2 的结果是否等于 4
        assertEquals(4, 2 + 2);
    }
}