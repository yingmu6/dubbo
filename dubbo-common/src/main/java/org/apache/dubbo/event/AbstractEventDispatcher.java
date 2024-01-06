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
package org.apache.dubbo.event;

import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableList;
import static org.apache.dubbo.event.EventListener.findEventType;

/**
 * The abstract {@link EventDispatcher} providers the common implementation.
 *
 * @see EventDispatcher
 * @see Listenable
 * @see ServiceLoader
 * @see EventListener
 * @see Event
 * @since 2.7.5
 */
public abstract class AbstractEventDispatcher implements EventDispatcher {

    private final Object mutex = new Object(); //mutex：互斥（当前类中doInListener()方法中使用synchronize加锁时用到）

    // 事件与事件监听器列表的映射关系（事件与监听器关系 = 1：n）
    private final ConcurrentMap<Class<? extends Event>, List<EventListener>> listenersCache = new ConcurrentHashMap<>();

    private final Executor executor; //用于事件派发时的线程池

    /**
     * Constructor with an instance of {@link Executor}
     *
     * @param executor {@link Executor}
     * @throws NullPointerException <code>executor</code> is <code>null</code>
     */
    protected AbstractEventDispatcher(Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor must not be null");
        }
        this.executor = executor;
        this.loadEventListenerInstances(); //注意点：会将SPI配置的监听器实例加载到当前缓存中
    }

    @Override
    public void addEventListener(EventListener<?> listener) throws NullPointerException, IllegalArgumentException { //将事件与监听器列表添加到本地缓存listenersCache中
        Listenable.assertListener(listener);
        doInListener(listener, listeners -> { //将事件监听器添加到监听器列表中（第2个参数是按函数式接口传递的）
            addIfAbsent(listeners, listener); //把listener加入到集合中（此处相当于Consumer中的accept方法，定义了函数式接口中的操作，其它变量的值如listener会先保存起来，函数式接口回调时能使用）
        });
    }

    @Override
    public void removeEventListener(EventListener<?> listener) throws NullPointerException, IllegalArgumentException {
        Listenable.assertListener(listener);
        doInListener(listener, listeners -> listeners.remove(listener));
    }

    @Override
    public List<EventListener<?>> getAllEventListeners() {
        List<EventListener<?>> listeners = new LinkedList<>();

        sortedListeners().forEach(listener -> {
            addIfAbsent(listeners, listener);
        });

        return unmodifiableList(listeners); //将原有的监听器列表，加到新的集合中，避免对原来集合变更
    }

    protected Stream<EventListener> sortedListeners() {
        return sortedListeners(e -> true);
    }

    // 筛选出缓存中的事件监听器，并进行排序
    protected Stream<EventListener> sortedListeners(Predicate<Map.Entry<Class<? extends Event>, List<EventListener>>> predicate) {
        return listenersCache
                .entrySet()
                .stream()
                .filter(predicate) //将缓存的内容进行过滤，保留key为Event类型的值（predicate函数的具体行为，回看传入的地方）
                .map(Map.Entry::getValue) //获取到事件监听器EventListener列表
                .flatMap(Collection::stream)
                .sorted();
    }

    private <E> void addIfAbsent(Collection<E> collection, E element) { //在元素不存在于集合中时，添加到集合中
        if (!collection.contains(element)) {
            collection.add(element);
        }
    }

    @Override
    public void dispatch(Event event) { //进行事件派发（从本地缓存listenersCache中查找到事件与监听器列表，并依次调用监听器进行事件处理）

        Executor executor = getExecutor();

        // execute in sequential or parallel execution model
        executor.execute(() -> { //将事件处理使用线程执行
            sortedListeners(entry -> entry.getKey().isAssignableFrom(event.getClass())) //过滤出符合条件的监听器列表，并进行排序（把缓存listenersCache进行过滤，只获取事件类型Event对应缓存值，最后将监听器列表排序）
                    .forEach(listener -> { //todo @csy 此处不按具体事件派发吗？还是一个事件触发，其它事件的监听器也会被触发吗？
                        if (listener instanceof ConditionalEventListener) { //ConditionalEventListener与普通EventListener的执行方法不一样，使用的是accept()方法，而不是onEvent()，所以特殊判断下
                            ConditionalEventListener predicateEventListener = (ConditionalEventListener) listener;
                            if (!predicateEventListener.accept(event)) { // No accept（判断事件是否能被当前监听器处理）
                                return;
                            }
                        }
                        // Handle the event
                        listener.onEvent(event); //回调事件监听器的处理方法（接口回调）
                    });
        });
    }

    /**
     * @return the non-null {@link Executor}
     */
    @Override
    public final Executor getExecutor() {
        return executor;
    }

    protected void doInListener(EventListener<?> listener, Consumer<Collection<EventListener>> consumer) { //添加监听器（Consumer使用：函数接口传递，封装好业务逻辑传递，调用accept()方法时，回调逻辑）
        Class<? extends Event> eventType = findEventType(listener); //找到监听器对应的事件类型
        if (eventType != null) {
            synchronized (mutex) { //加锁处理
                List<EventListener> listeners = listenersCache.computeIfAbsent(eventType, e -> new LinkedList<>()); //查找到指定事件类型对应的监听器列表
                // consume
                consumer.accept(listeners); //将监听器listener加入到listeners监听器列表中
                // sort
                sort(listeners);
            }
        }
    }

    /**
     * Default, load the instances of {@link EventListener event listeners} by {@link ServiceLoader}
     * <p>
     * It could be override by the sub-class
     *
     * @see EventListener
     * @see ServiceLoader#load(Class)
     */
    protected void loadEventListenerInstances() { //加载所有事件监听器对应的实例
        ExtensionLoader<EventListener> loader = ExtensionLoader.getExtensionLoader(EventListener.class);
        loader.getSupportedExtensionInstances().forEach(this::addEventListener);
    }
}
