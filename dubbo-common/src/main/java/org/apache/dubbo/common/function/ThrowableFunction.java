/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.function;

import java.util.function.Function;

/**
 * {@link Function} with {@link Throwable}
 *
 * @param <T> the source type
 * @param <R> the return type
 * @see Function
 * @see Throwable
 * @since 2.7.5
 */
@FunctionalInterface
public interface ThrowableFunction<T, R> { //dubbo自定义的函数式接口，可抛出异常的函数式接口

    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     * @throws Throwable if met with any error
     */
    R apply(T t) throws Throwable; //输入一个参数计算后返回值

    /**
     * Executes {@link ThrowableFunction}
     *
     * @param t the function argument
     * @return the function result
     * @throws RuntimeException wrappers {@link Throwable}
     */
    default R execute(T t) throws RuntimeException { //default默认方法
        R result = null;
        try {
            result = apply(t);
        } catch (Throwable e) {
            throw new RuntimeException(e.getCause());
        }
        return result;
    }

    /**
     * Executes {@link ThrowableFunction}
     *
     * @param t        the function argument
     * @param function {@link ThrowableFunction}
     * @param <T>      the source type
     * @param <R>      the return type
     * @return the result after execution
     */
    static <T, R> R execute(T t, ThrowableFunction<T, R> function) { //static静态方法，用函数function接收传入的值t进行处理，并返回处理的值R
        return function.execute(t);
    }
}
