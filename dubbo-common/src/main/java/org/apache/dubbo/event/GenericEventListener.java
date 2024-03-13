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

import org.apache.dubbo.common.function.ThrowableConsumer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.stream.Stream.of;
import static org.apache.dubbo.common.function.ThrowableFunction.execute;

/**
 * An abstract class of {@link EventListener} for Generic events, the sub class could add more {@link Event event}
 * handle methods, rather than only binds the {@link EventListener#onEvent(Event)} method that is declared to be
 * <code>final</code> the implementation can't override（onEvent被声明为final方法，不能被子类实现）. It's notable（值得注意的是） that all {@link Event event} handle methods must
 * meet following conditions（满足以下条件）:
 * <ul>
 * <li>not {@link #onEvent(Event)} method</li>
 * <li><code>public</code> accessibility</li> public 可见的
 * <li><code>void</code> return type</li> 返回void类型
 * <li>no {@link Exception exception} declaration</li>
 * <li>only one {@link Event} type argument</li>
 * </ul>
 *
 * @see Event
 * @see EventListener
 * @since 2.7.5
 */
public abstract class GenericEventListener implements EventListener<Event> { //通用的事件监听器，Generic：一般的；普通的；通用的

    /**
     * 只要定义满足条件的处理事件方法（即isHandleEventMethod中的逻辑），通过继承GenericEventListener的事件监听器，可以有
     * 多个处理事件的方法被回调，例如：MyGenericEventListener中有两个满足方法onEvent(EchoEvent echoEvent)和event(EchoEvent echoEvent)
     */
    private final Method onEventMethod; //onEvent(Event)方法对应的Method

    private final Map<Class<?>, Set<Method>> handleEventMethods; //维护着事件与事件处理的方法的映射（1:n的关系）

    protected GenericEventListener() {
        this.onEventMethod = findOnEventMethod(); //获取onEvent(Event)方法对应的Method对象（即GenericEventListener声明的onEvent方法）
        this.handleEventMethods = findHandleEventMethods(); //获取GenericEventListener实现类中处理事件对象的方法（不包含onEvent(Event)方法）
    }

    private Method findOnEventMethod() { //查找onEvent(Event)方法对应的Method对象
        return execute(getClass(), listenerClass -> listenerClass.getMethod("onEvent", Event.class));
    }

    private Map<Class<?>, Set<Method>> findHandleEventMethods() {
        // Event class for key, the eventMethods' Set as value
        Map<Class<?>, Set<Method>> eventMethods = new HashMap<>();
        of(getClass().getMethods()) //遍历当前类中的所有方法（包含声明的和继承的所有方法）
                .filter(this::isHandleEventMethod)
                .forEach(method -> {
                    Class<?> paramType = method.getParameterTypes()[0];
                    Set<Method> methods = eventMethods.computeIfAbsent(paramType, key -> new LinkedHashSet<>());
                    methods.add(method);
                });
        return eventMethods;
    }

    public final void onEvent(Event event) { //依次执行处理事件的方法
        Class<?> eventClass = event.getClass();
        handleEventMethods.getOrDefault(eventClass, emptySet()).forEach(method -> {
            ThrowableConsumer.execute(method, m -> {
                m.invoke(this, event);
            });
        });
    }

     /**
     * The {@link Event event} handle methods must meet（遇见） following conditions:
     * （事件处理方法必须满足的条件）
     * <ul>
     * <li>not {@link #onEvent(Event)} method</li>
     * <li><code>public</code> accessibility</li>
     * <li><code>void</code> return type</li>
     * <li>no {@link Exception exception} declaration</li>
     * <li>only one {@link Event} type argument</li>
     * </ul>
     *
     * @param method
     * @return
     */
    private boolean isHandleEventMethod(Method method) { //判断是否是处理事件的方法

        if (onEventMethod.equals(method)) { // not {@link #onEvent(Event)} method （不是onEvent(Event)方法）
            return false;
        }

        if (!Modifier.isPublic(method.getModifiers())) { // not public
            return false;
        }

        if (!void.class.equals(method.getReturnType())) { // void return type
            return false;
        }

        Class[] exceptionTypes = method.getExceptionTypes();

        if (exceptionTypes.length > 0) { // no exception declaration
            return false;
        }

        Class[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != 1) { // not only one argument（只包含一个参数）
            return false;
        }

        if (!Event.class.isAssignableFrom(paramTypes[0])) { // not Event type argument（参数类型为Event或Event子类）
            return false;
        }

        return true;
    }
}
