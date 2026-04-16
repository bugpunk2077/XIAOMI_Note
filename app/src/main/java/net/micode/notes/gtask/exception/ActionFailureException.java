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

package net.micode.notes.gtask.exception;

/**
 * 自定义异常类：操作执行失败异常
 * 专门用于 Google Task 同步模块，当同步相关操作执行失败时抛出
 * 继承自 RuntimeException，属于运行时异常，无需强制捕获
 */
public class ActionFailureException extends RuntimeException {
    // 序列化版本ID，用于Java对象序列化，固定值保证兼容性
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 无参构造方法
     * 创建一个不带错误信息的异常对象
     */
    public ActionFailureException() {
        super();
    }

    /**
     * 带错误提示信息的构造方法
     * @param paramString 异常的描述信息（失败原因）
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带错误提示信息 + 异常根源的构造方法
     * @param paramString 异常的描述信息
     * @param paramThrowable 引发该异常的原始异常（用于追踪错误根源）
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}