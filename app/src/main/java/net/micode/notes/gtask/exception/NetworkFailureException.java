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
 * 自定义异常类：网络操作失败异常
 * 专门用于 Google Task 同步模块，当网络请求、网络连接失败时抛出
 * 继承自普通 Exception，属于检查型异常，需要显式捕获或抛出
 */
public class NetworkFailureException extends Exception {
    // 序列化版本ID，保证对象序列化与反序列化的兼容性
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 无参构造方法
     * 创建一个不带错误信息的网络异常对象
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 带错误提示信息的构造方法
     * @param paramString 异常描述信息，说明网络失败原因
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带错误信息 + 原始异常的构造方法
     * @param paramString 异常描述信息
     * @param paramThrowable 引发该网络异常的底层异常（用于排查问题）
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}