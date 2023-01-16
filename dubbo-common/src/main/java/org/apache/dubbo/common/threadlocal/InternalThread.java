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

package org.apache.dubbo.common.threadlocal;

/**
 * InternalThread
 */
public class InternalThread extends Thread { //内部的线程
    /**
     * InternalThread：内部使用的线程（对线程进行封装）
     * 1）本身是一个线程，继承了Thread
     * 2）使用InternalThreadLocalMap对ThreadLocal做了缓存
     */

    private InternalThreadLocalMap threadLocalMap; //内部的线程局部变量的Map（非static变量，每个InternalThread对象各自维护）

    public InternalThread() {
    }

    // 实现Thread线程的多种构造函数
    public InternalThread(Runnable target) {
        super(target); //super：构造函数不能继承，需要主动调用，默认会在第一行调用super()，若调用带有参数的构造函数，需要显示指定
    }

    public InternalThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public InternalThread(String name) {
        super(name);
    }

    public InternalThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public InternalThread(Runnable target, String name) {
        super(target, name);
    }

    public InternalThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public InternalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    /**
     * Returns the internal data structure that keeps the threadLocal variables bound to this thread.
     * （返回将threadLocal变量绑定到该线程的内部数据结构）
     * Note that this method is for internal use only, and thus（因此） is subject to change at any time.
     * （请注意，此方法仅供内部使用，因此随时可能更改）
     */
    public final InternalThreadLocalMap threadLocalMap() { //返回InternalThreadMap
        return threadLocalMap;
    }

    /**
     * Sets the internal data structure that keeps the threadLocal variables bound to this thread. （设置将线程局部变量绑定到当前线程的内部数据结构）
     * Note that this method is for internal use only, and thus is subject to change at any time.
     */
    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) { //设置InternalThreadMap，当值设置为null，即为清空处理
        this.threadLocalMap = threadLocalMap;
    }
}
