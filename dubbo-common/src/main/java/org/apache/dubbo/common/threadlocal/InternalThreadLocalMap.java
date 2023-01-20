/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.dubbo.common.threadlocal;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The internal data structure that stores the threadLocal variables for Netty and all {@link InternalThread}s. （InternalThread设计思想来自于Netty）
 * （用来存储线程局部变量的数据结构）
 * Note that this class is for internal use only. Use {@link InternalThread}
 * unless you know what you are doing.
 */
public final class InternalThreadLocalMap { //内部的线程局部变量的Map【用于存储线程的局部变量值，存储的结构是一个数组，而不是一个Map（快慢获取的元素，本质在于数组结构的不同）】

    private Object[] indexedVariables; //缓存对象对应的数组（不是static变量，非共享，每个对象各自维护，是线程安全的）

    private static ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = new ThreadLocal<InternalThreadLocalMap>(); //原生的ThreadLocal，每个线程维护各自的线程变量（原生的ThreadLocal使用get()获取值时，会通过计算hashCode进行查找处理）

    private static final AtomicInteger NEXT_INDEX = new AtomicInteger(); //@csy-03-01 该索引的功能用途是什么？解：记录数组可设值的下标（下一个设置的值，对应的下标，static变量，属于公共资源，初始值为0）

    public static final Object UNSET = new Object(); //@csy-03-02 该对象的功能用途是怎样的？解：当未设置值时，给出的默认值（用于填充使用）

    public static InternalThreadLocalMap getIfSet() { //获取InternalThreadLocalMap
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) { //判断当前线程的类型
            return ((InternalThread) thread).threadLocalMap(); //若是内部线程InternalThread，从内部线程中获取InternalThreadLocalMap
        }
        return slowThreadLocalMap.get(); //若不是内部线程，则取ThreadLocal维护的InternalThreadLocalMap
    }

    public static InternalThreadLocalMap get() { //获取InternalThreadLocalMap，返回的值若为空，会初始化对象返回
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            return fastGet((InternalThread) thread); //比较快的获取值
        }
        return slowGet(); //比较慢的获取值
    }

    public static void remove() { //移除InternalThreadLocalMap
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            ((InternalThread) thread).setThreadLocalMap(null);
        } else {
            slowThreadLocalMap.remove();
        }
    }

    public static void destroy() { //销毁Map
        slowThreadLocalMap = null; //置为空
    }

    public static int nextVariableIndex() { //获取下一次的数组下标（每次创建，下标就会加1）
        int index = NEXT_INDEX.getAndIncrement(); //获取原子自增之前的值，并将原子变量自增1
        if (index < 0) {
            NEXT_INDEX.decrementAndGet();
            throw new IllegalStateException("Too many thread-local indexed variables");
        }
        return index;
    }

    public static int lastVariableIndex() {
        return NEXT_INDEX.get() - 1;
    }

    private InternalThreadLocalMap() { //私有的构造函数
        indexedVariables = newIndexedVariableTable(); //初始化维护的数组
    }

    public Object indexedVariable(int index) { //从数组中获取指定下标对应的变量值
        Object[] lookup = indexedVariables;
        return index < lookup.length ? lookup[index] : UNSET; //若索引越界，返回一个默认的对象值
    }

    /**
     * @return {@code true} if and only if a new thread-local variable has been created
     */
    public boolean setIndexedVariable(int index, Object value) { //设置线程局部变量的值（设置成功返回true）
        Object[] lookup = indexedVariables; //引用赋值，lookup数组改变，indexedVariables数组也对应改变
        if (index < lookup.length) {
            Object oldValue = lookup[index];
            lookup[index] = value;
            return oldValue == UNSET; //@csy 此处是何意？解：若老的值为UNSET，说明值已经从UNSET -> value改变了
        } else {
            expandIndexedVariableTableAndSet(index, value); //扩容处理
            return true;
        }
    }

    public Object removeIndexedVariable(int index) { //移除指定下标对应的值，并返回移除前的值
        Object[] lookup = indexedVariables; //使用新的数组接收成员变量的值，避免对成员变量有影响
        if (index < lookup.length) {
            Object v = lookup[index];
            lookup[index] = UNSET; //将元素的值设置为UNSET对象
            return v; //返回元素移除前的值
        } else {
            return UNSET;
        }
    }

    public int size() { //计算所有不为UNSET的元素（需要减掉第一个元素）
        int count = 0;
        for (Object o : indexedVariables) {
            if (o != UNSET) {
                ++count;
            }
        }

        //the fist element in `indexedVariables` is a set to keep all the InternalThreadLocal to remove（第一个元素用于保存所有要删除的InternalThreadLocal元素）
        //look at method `addToVariablesToRemove`
        return count - 1;
    }

    private static Object[] newIndexedVariableTable() {
        Object[] array = new Object[32];
        Arrays.fill(array, UNSET); //初始化时，填充UNSET对象
        return array;
    }

    private static InternalThreadLocalMap fastGet(InternalThread thread) { //比较快的获取InternalThreadLocalMap
        InternalThreadLocalMap threadLocalMap = thread.threadLocalMap(); //从InternalThread直接获取
        if (threadLocalMap == null) {
            thread.setThreadLocalMap(threadLocalMap = new InternalThreadLocalMap());
        }
        return threadLocalMap;
    }

    private static InternalThreadLocalMap slowGet() { //比较慢的获取InternalThreadLocalMap
        ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = InternalThreadLocalMap.slowThreadLocalMap;
        InternalThreadLocalMap ret = slowThreadLocalMap.get(); //使用ThreadLocal获取，内部是通过hashCode去获取值的
        if (ret == null) {
            ret = new InternalThreadLocalMap(); //初始化InternalThreadLocalMap
            slowThreadLocalMap.set(ret); //将值设置set到ThreadLocal中（get时可取到set的值）
        }
        return ret;
    }

    private void expandIndexedVariableTableAndSet(int index, Object value) { //扩容并设置线程变量的值，expand：扩大（扩展维护的数据容量）
        Object[] oldArray = indexedVariables;
        final int oldCapacity = oldArray.length;
        int newCapacity = index;
        newCapacity |= newCapacity >>> 1; //无符号右移
        newCapacity |= newCapacity >>> 2;
        newCapacity |= newCapacity >>> 4;
        newCapacity |= newCapacity >>> 8;
        newCapacity |= newCapacity >>> 16;
        newCapacity++;

        Object[] newArray = Arrays.copyOf(oldArray, newCapacity);
        Arrays.fill(newArray, oldCapacity, newArray.length, UNSET);
        newArray[index] = value;
        indexedVariables = newArray;
    }
}
