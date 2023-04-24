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

import org.junit.jupiter.api.Test;

import static org.apache.dubbo.event.EventDispatcher.DIRECT_EXECUTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EventDispatcher} Test
 *
 * @see DirectEventDispatcher
 * @since 2.7.5
 */
public class EventDispatcherTest {

    private EventDispatcher defaultInstance = EventDispatcher.getDefaultExtension(); //通过此处debug观察得知，junit单元测试时，在@Test进入方法前，会使用反射机制Constructor的newInstance创建实例，所以当前的成员变量赋值会被执行

    @Test
    public void testDefaultInstance() { //EventDispatcher的默认实例是DirectEventDispatcher（通过SPI机制获取的实例）
        assertEquals(DirectEventDispatcher.class, defaultInstance.getClass());
    }

    @Test
    public void testDefaultMethods() {
        assertEquals(DIRECT_EXECUTOR, defaultInstance.getExecutor()); //DirectEventDispatcher实例创建时，调用super(DIRECT_EXECUTOR);指定的

        defaultInstance.addEventListener(new EventListener<Event>() { //添加事件监听器（EventListener是函数式结构，可以用lambda表示）
            @Override
            public void onEvent(Event event) { //todo @pause
                System.out.println("收到事件" + event.getSource());
            }
        });

        assertTrue(!defaultInstance.getAllEventListeners().isEmpty());

        defaultInstance.dispatch(new EchoEvent("hhh")); //进行事件派发时，会调用事件关联监听器的onEvent()方法
    }
}
