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
    public void addEventListener(EventListener<?> listener) throws NullPointerException, IllegalArgumentException { //将监听器添加到本地缓存listenersCache中
        Listenable.assertListener(listener);
        doInListener(listener, listeners -> { //将事件监听器添加到监听器列表中（会判断监听器是否存在集合中，不存在才添加）
            addIfAbsent(listeners, listener); //传递的函数块，要在执行Consumer#accept方法时才会回过来执行，并不会提前执行
        });
    }

    @Override
    public void removeEventListener(EventListener<?> listener) throws NullPointerException, IllegalArgumentException {
        Listenable.assertListener(listener);
        doInListener(listener, listeners -> listeners.remove(listener));
    }

    @Override
    public List<EventListener<?>> getAllEventListeners() { //获取缓存中的所有事件监听器
        List<EventListener<?>> listeners = new LinkedList<>();

        sortedListeners().forEach(listener -> {
            addIfAbsent(listeners, listener);
        });

        return unmodifiableList(listeners); //将原有的监听器列表，加到新的集合中，避免对原来集合变更
    }

    protected Stream<EventListener> sortedListeners() {
        return sortedListeners(e -> true); //过滤的Predicate始终为true，即保留所有列表中的元素
    }

    // 筛选出缓存中的事件监听器，并进行排序
    protected Stream<EventListener> sortedListeners(Predicate<Map.Entry<Class<? extends Event>, List<EventListener>>> predicate) {
        return listenersCache
                .entrySet()
                .stream()
                .filter(predicate) //过滤Predicate#test为true的元素（predicate函数的具体行为，回看传入的地方）
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
    public void dispatch(Event event) { //进行事件派发（从本地缓存listenersCache中找到事件关联的监听器列表，并依次回调监听器onEvent方法）

        Executor executor = getExecutor();

        // execute in sequential or parallel execution model
        executor.execute(() -> { //使用线程池进行事件处理（提交任务到线程池中）
            /**
             * 派发时事件类型匹配的逻辑：
             * 1）派发时传入的事件类型，要与缓存中事件监听器维护的事件类型相同或是其子类
             * 2）例如：派发时传入的类型为Event，而添加的事件监听器为EchoEventListener<EchoEvent>，此处传入的Event既不与EchoEvent相同，
             *   也不是EchoEvent的子类，所以就匹配不到事件监听器
             */
            sortedListeners(entry -> entry.getKey().isAssignableFrom(event.getClass())) //过滤出事件关联的监听器列表，并进行排序
                    .forEach(listener -> {
                        if (listener instanceof ConditionalEventListener) { //ConditionalEventListener监听器，先判断监听器是否能接受指定的事件
                            ConditionalEventListener predicateEventListener = (ConditionalEventListener) listener;
                            if (!predicateEventListener.accept(event)) { // No accept（若监听器不能接受指定事件，则不进行后续事件派发）
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

    protected void doInListener(EventListener<?> listener, Consumer<Collection<EventListener>> consumer) { //将事件监听器添加到缓存中（Consumer使用：函数接口传递，将封装好业务逻辑传递，要在执行Consumer#accept方法时才会执行传入的函数块逻辑）
        Class<? extends Event> eventType = findEventType(listener); //找到监听器对应的事件类型
        if (eventType != null) {
            synchronized (mutex) { //加锁处理（加锁范围：为括号中的mutex对象）
                List<EventListener> listeners = listenersCache.computeIfAbsent(eventType, e -> new LinkedList<>());
                // consume
                consumer.accept(listeners); //处理监听器列表（1：此处调用时，会返回执行传入的函数块逻辑，2：此处是引用传递，listeners处理好后，缓存listenersCache中的列表也对应处理了，是同一个引用）
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
