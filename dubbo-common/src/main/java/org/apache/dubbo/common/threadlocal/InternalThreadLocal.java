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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * InternalThreadLocal
 * A special variant of {@link ThreadLocal} that yields higher access performance when accessed from a
 * {@link InternalThread}.
 * <p></p>
 * Internally, a {@link InternalThread} uses a constant index in an array（使用数组索引）, instead of（替代） using hash code and hash table,
 * to look for a variable.  Although seemingly very subtle, it yields slight performance advantage over using a hash
 * table（性能优于使用hash table表）, and it is useful when accessed frequently（频繁地）.
 * <p></p>
 * This design is learning from {@see io.netty.util.concurrent.FastThreadLocal} which is in Netty.
 * <p>
 * <p>
 * <p>
 * InternalThreadLocal
 * 解答：
 * 1）对ThreadLocal的封装处理，内部使用的数据结构是数组，而ThreadLocal是使用hashCode来计算处理的，
 * 多了一步计算，还得解决hash冲突，所以InternalThreadLocal的访问性能更高
 * 2）原理来自于Netty的FastThreadLocal
 * 3）相关参考资料
 * a）https://www.cnblogs.com/thisiswhy/p/13839741.html（非常有趣的讲解）
 * b）https://blog.csdn.net/dbqb007/article/details/95243660
 * c）https://icode9.com/content-4-1054690.html
 */
public class InternalThreadLocal<V> { //内部的线程局部变量（与ThreadLocal具有相似的功能，都是维护线程局部变量的值）

    /**
     * 在Java中，ThreadLocal是实现线程安全的一种手段，它的作用是对于同一个ThreadLocal变量，在每一个线程中都有一个副本，当修改任何一个线程的变量时，不会影响到其他线程。
     * 它通过在每一个Thread中存储一个类似于map的结构，以ThreadLocal变量为key，变量值为value。
     * <p>
     * Dubbo在RPC调用的上下文中，需要借助ThreadLocal保存上下文。通过ThreadLocal，可用于传递参数
     * InternalThreadLocal 是 ThreadLocal 的增强版，所以他们的用途都是一样的，一言蔽之就是：传递信息。
     */

    private static final int VARIABLES_TO_REMOVE_INDEX = InternalThreadLocalMap.nextVariableIndex(); //用于总预览的下标，因为该下标对应的元素存储了当前线程所有的InternalThreadLocal

    private final int index; //指的是当前InternalThreadLocal对象在InternalThreadLocalMap对应的下标（final修饰的变量为常量，表明一旦赋值后，就不能再改动，所以可以看出是一个对象一个index值（所以同一个InternalThreadLocal对象多次设值时，是会出现覆盖的）

    public InternalThreadLocal() {
        index = InternalThreadLocalMap.nextVariableIndex(); //设置下一个游标值，每使用一个InternalThreadLocal，游标就会+1
    }

    /**
     * Removes all {@link InternalThreadLocal} variables bound to the current thread.  This operation is useful when you
     * are in a container environment, and you don't want to leave the thread local variables in the threads you do not
     * manage.
     */
    @SuppressWarnings("unchecked")
    public static void removeAll() { //移除所有绑定在当前线程的InternalThreadLocal变量值
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return;
        }

        try {
            Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX); //拿到InternalThreadLocalMap的第一个元素，即包含InternalThreadLocal实例列表的集合
            if (v != null && v != InternalThreadLocalMap.UNSET) {
                Set<InternalThreadLocal<?>> variablesToRemove = (Set<InternalThreadLocal<?>>) v;
                InternalThreadLocal<?>[] variablesToRemoveArray =
                        variablesToRemove.toArray(new InternalThreadLocal[variablesToRemove.size()]); //将集合Set转换为Map
                for (InternalThreadLocal<?> tlv : variablesToRemoveArray) { //将每个InternalThreadLocal做移除操作
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            InternalThreadLocalMap.remove();
        }
    }

    /**
     * Returns the number of thread local variables（线程局部变量） bound to the current thread.
     */
    public static int size() { //返回绑定在当前线程绑定的InternalThreadLocal数量
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return 0;
        } else {
            return threadLocalMap.size();
        }
    }

    public static void destroy() {
        InternalThreadLocalMap.destroy();
    }

    @SuppressWarnings("unchecked")
    private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, InternalThreadLocal<?> variable) {//添加InternalThreadLocal变量到集合变量中（即处理第一个元素）
        Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX); //获取总预览下标对应的值，即当前线程维护的所有InternalThreadLocal的值
        Set<InternalThreadLocal<?>> variablesToRemove; //表示包含了多少个InternalThreadLocal实例
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<InternalThreadLocal<?>, Boolean>()); //将Map值转换为Set
            threadLocalMap.setIndexedVariable(VARIABLES_TO_REMOVE_INDEX, variablesToRemove);
        } else {
            variablesToRemove = (Set<InternalThreadLocal<?>>) v; //进行类型强转
        }

        variablesToRemove.add(variable); //InternalThreadLocalMap中的第一个元素是集合类型，如indexedVariables[0]为Collections$SetFromMap@1751
    }

    @SuppressWarnings("unchecked")
    private static void removeFromVariablesToRemove(InternalThreadLocalMap threadLocalMap, InternalThreadLocal<?> variable) { //从集合中移除指定的InternalThreadLocal对象值

        Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX);

        if (v == InternalThreadLocalMap.UNSET || v == null) {
            return;
        }

        Set<InternalThreadLocal<?>> variablesToRemove = (Set<InternalThreadLocal<?>>) v;
        variablesToRemove.remove(variable); //从set集合移除指定的InternalThreadLocal对象值
    }

    /**
     * Returns the current value for the current thread
     */
    @SuppressWarnings("unchecked")
    public final V get() { //从当前线程中获取当前维护的值（若没有查找到值，会进行初始化）
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get(); //与set()获取InternalThreadLocalMap方式一致
        Object v = threadLocalMap.indexedVariable(index); //获取当前对象对应index对应的值
        if (v != InternalThreadLocalMap.UNSET) { //若值不为UNSET，则直接返回
            return (V) v;
        }

        return initialize(threadLocalMap); //若不存在值，则进行初始化操作
    }

    private V initialize(InternalThreadLocalMap threadLocalMap) { //做初始化，并返回初始化后的值
        V v = null;
        try {
            v = initialValue(); //调用子类重写的方法，若子类没有重写，则值为null
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        threadLocalMap.setIndexedVariable(index, v); //此处设置的逻辑，和set()的内部逻辑一致
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    /**
     * Sets the value for the current thread.（为当前线程设置值）
     */
    public final void set(V value) {
        if (value == null || value == InternalThreadLocalMap.UNSET) {
            remove(); //设置的值为空时，做移除处理（与调用remove()方法是等价的）
        } else {
            InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
            if (threadLocalMap.setIndexedVariable(index, value)) { //将值设置到InternalThreadLocalMap维护的数组中
                addToVariablesToRemove(threadLocalMap, this); //将当前的InternalThreadLocal对象，添加到总预览对应的集合中
            }
        }
    }

    /**
     * Sets the value to uninitialized; a proceeding call to get() will trigger a call to initialValue().
     */
    @SuppressWarnings("unchecked")
    public final void remove() {
        remove(InternalThreadLocalMap.getIfSet()); //获取当前线程绑定的InternalThreadLocalMap，并做移除操作
    }

    /**
     * Sets the value to uninitialized（未初始化的） for the specified thread local map;
     * a proceeding call to get() will trigger a call to initialValue().
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final void remove(InternalThreadLocalMap threadLocalMap) { //从InternalThreadLocalMap中移除指定下标index对应的值（此处语义上不太好理解，就是没有通过方法参数传递index，而是通过操作成员变量的方式）
        if (threadLocalMap == null) {
            return;
        }

        Object v = threadLocalMap.removeIndexedVariable(index); // 1）移除指定下标对应的值，并返回移除前的值
        removeFromVariablesToRemove(threadLocalMap, this); // 2）将当前InternalThreadLocal从集合中移除

        if (v != InternalThreadLocalMap.UNSET) { //当前InternalThreadLocal有设置过值，则对应回调子类方法

            try {
                onRemoval((V) v); // 3）看子类的具体移除实现（回调子类的方法）
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns the initial value for this thread-local variable.
     */
    protected V initialValue() throws Exception { //初始值操作，交由具体子类实现
        return null;
    }

    /**
     * Invoked when this thread local variable is removed by {@link #remove()}.
     */
    protected void onRemoval(@SuppressWarnings("unused") V value) throws Exception { //清理值操作，交由具体子类实现
    }
}
