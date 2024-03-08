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

import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.lang.Prioritized;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

import static org.apache.dubbo.common.utils.ReflectUtils.findParameterizedTypes;

/**
 * The {@link Event Dubbo Event} Listener that is based on Java standard {@link java.util.EventListener} interface supports
 * the generic（通用的） {@link Event}.
 * <p>
 * The {@link #onEvent(Event) handle method} will be notified when the matched-type {@link Event Dubbo Event} is
 * published, whose priority（优先级） could be changed by {@link #getPriority()} method.
 *
 * @param <E> the concrete class of {@link Event Dubbo Event}
 * @see Event
 * @see java.util.EventListener
 * @since 2.7.5
 */
@SPI
@FunctionalInterface
public interface EventListener<E extends Event> extends java.util.EventListener, Prioritized { //事件监听器（是SPI接口，也是函数式接口，通过事件监听器处理事件）
    /**
     * java中EventListener：所有事件监听器接口都必须扩展的标记接口
     *
     * java.util.EventListener：java事件监听器
     * A tagging interface that all event listener interfaces must extend
     * （EventListener：是所有事件监听器必须继承的标记接口）
     */

    /**
     * Handle a {@link Event Dubbo Event} when it's be published
     * （当dubbo事件被发布时，进行事件处理）
     *
     * @param event a {@link Event Dubbo Event}
     */
    void onEvent(E event); //当前函数式接口的核心方法，可以用lambda表达式处理调用

    /**
     * The priority of {@link EventListener current listener}.
     *
     * @return the value is more greater, the priority is more lower.
     * {@link Integer#MIN_VALUE} indicates the highest priority. The default value is {@link Integer#MAX_VALUE}.
     * The comparison rule , refer to {@link #compareTo}.
     */
    default int getPriority() {
        return NORMAL_PRIORITY;
    }

    /**
     * Find the {@link Class type} {@link Event Dubbo event} from the specified {@link EventListener Dubbo event listener}
     *
     * @param listener the {@link Class class} of {@link EventListener Dubbo event listener}
     * @return <code>null</code> if not found
     */
    static Class<? extends Event> findEventType(EventListener<?> listener) { //从指定的事件监听器中找到对应的事件类型
        return findEventType(listener.getClass());
    }

    /**
     * Find the {@link Class type} {@link Event Dubbo event} from the specified {@link EventListener Dubbo event listener}
     *
     * @param listenerClass the {@link Class class} of {@link EventListener Dubbo event listener}
     * @return <code>null</code> if not found
     */
    static Class<? extends Event> findEventType(Class<?> listenerClass) { //查找监听器实例中对应的事件类型（就是查找 EventListener<E extends Event> 中的泛化类型）
        Class<? extends Event> eventType = null;

        /**
         * 用例说明：
         * 如EventDispatcher#testDefaultMethods方法测试时
         * 1）传入的listenerClass为EventDispatcher$1，即EventListener的匿名实现类
         * 2）经过findParameterizedTypes方法处理时，会查找该类实现的接口和继承的类，找到包含泛化参数类型，此处EventDispatcher$1实现的接口为EventListener<Event>，继承的类为Object
         *   由于EventListener<Event>包含泛化参数，满足条件，所以将EventListener<Event>映射为ParameterizedType返回
         * 3）执行EventListener::findEventType时，会调用ParameterizedType.getActualTypeArguments()，取实际参数并判断是否为Class实例且为Event类型，最终Event符合要求，就返回对应的Class
         */
        if (listenerClass != null && EventListener.class.isAssignableFrom(listenerClass)) { //isAssignableFrom判断当前的类或接口是否与指定类和接口相同，或者是父类和父接口
            eventType = findParameterizedTypes(listenerClass) //1）先找到监听器实例关联的接口和类的ParameterizedType集合
                    .stream()
                    .map(EventListener::findEventType) //2）再从ParameterizedType实际参数中找到为Event类型的Class
                    .filter(Objects::nonNull)
                    .findAny()
                    .orElse((Class) findEventType(listenerClass.getSuperclass())); //若都没找到，则找它的父类对应的事件类型
        }

        return eventType;
    }

    /**
     * Find the type {@link Event Dubbo event} from the specified {@link ParameterizedType} presents
     * a class of {@link EventListener Dubbo event listener}
     *
     * @param parameterizedType the {@link ParameterizedType} presents a class of {@link EventListener Dubbo event listener}
     * @return <code>null</code> if not found
     */
    static Class<? extends Event> findEventType(ParameterizedType parameterizedType) { //从泛化参数中找到事件类型（举例：如parameterizedType为AbstractEventListener<EchoEvent>）
        Class<? extends Event> eventType = null;

        Type rawType = parameterizedType.getRawType(); //返回声明泛化参数的类或接口的Class，rawType为AbstractEventListener.class
        if ((rawType instanceof Class) && EventListener.class.isAssignableFrom((Class) rawType)) { //声明泛化参数的类，需要是EventListener类型
            Type[] typeArguments = parameterizedType.getActualTypeArguments(); //获取泛型中的实际参数，如typeArguments为EchoEvent.class
            for (Type typeArgument : typeArguments) {
                if (typeArgument instanceof Class) {
                    Class argumentClass = (Class) typeArgument;
                    if (Event.class.isAssignableFrom(argumentClass)) { //事件类型，需要是Event类型
                        eventType = argumentClass;
                        break;
                    }
                }
            }
        }

        return eventType;
    }
}