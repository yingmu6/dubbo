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

    /**
     * EventDispatcher事件派发：
     * 1）事件派发：涉及到事件Event、事件监听器EventListener
     * 2）事件与监听器，类似设计模式中的观察着模式，当事件触发时，回调与事件关联的所有监听器接口
     * 3）实现步骤：
     *    a）通过注册监听器的方式，将事件与事件监听器的关系建立起来，关系为1:n，如：AbstractEventDispatcher#listenersCache
     *    b）事件派发时，EventDispatcher#dispatch(Event)时，会根据把listenersCache缓存的所有事件对应的监听器查出来，依次执行监听器中的方法
     *
     * 额外的测试场景：
     * 1）自定义事件Event、事件监听器EventListener进行正常功能调试
     * 2）通过两个方法加载事件监听器：
     *    a）通过SPI配置文件
     *    b）通过使用EventDispatcher#dispatch方法
     * 3）调用之前、调用之后、出现异常时，会触发 oninvoke、onreturn、onthrow 三个事件的调试
     *
     * 参考：https://cn.dubbo.apache.org/zh-cn/overview/mannual/java-sdk/advanced-features-and-usage/service/events-notify/ dubbo官网（调用触发事件通知）
     */
    private EventDispatcher defaultInstance = EventDispatcher.getDefaultExtension(); //通过此处debug观察得知，junit单元测试时，在@Test进入方法前，会使用反射机制Constructor的newInstance创建实例，所以当前的成员变量赋值会被执行

    @Test
    public void testDefaultInstance() { //EventDispatcher的默认实例是DirectEventDispatcher（通过SPI机制获取的实例）
        assertEquals(DirectEventDispatcher.class, defaultInstance.getClass());
    }

    @Test
    public void testDefaultMethods() {
        assertEquals(DIRECT_EXECUTOR, defaultInstance.getExecutor()); //DirectEventDispatcher实例创建时，调用super(DIRECT_EXECUTOR)指定的

        defaultInstance.addEventListener(new EventListener<Event>() { //添加事件监听器（添加到AbstractEventDispatcher的缓存Map中）
            @Override
            public void onEvent(Event event) {
                System.out.println("收到事件" + event.getSource());
            }
        });

//        defaultInstance.addEventListener((event) -> { //@csy 此种使用lambda的写法有错吗，维护添加不了监听器？解答：写法是没有问题的，只是后续会查找EventListener的泛型参数作为缓存的Map，用lambda表示泛型类型会认为是Object，不是Event，所以添加不了监听器
//                System.out.println("收到事件" + event.getSource());
//        });

        assertTrue(!defaultInstance.getAllEventListeners().isEmpty());

        defaultInstance.dispatch(new EchoEvent("hhh")); //进行事件派发时，会调用事件关联监听器的onEvent()方法
    }

    @Test
    public void testCustomEventListener() {
        assertEquals(DIRECT_EXECUTOR, defaultInstance.getExecutor());

//        defaultInstance.addEventListener(new CustomEventListener<CustomEvent>() {
//            @Override
//            public void onEvent(CustomEvent event) {
//                System.out.println("自定义事件监听器，收到事件" + event.getSource()); //todo @csy 此处会报cannot find symbol，找不到 event.getSource()
//            }
//        });

        assertTrue(!defaultInstance.getAllEventListeners().isEmpty());
//        defaultInstance.dispatch(new CustomEvent("haha"));// todo @csy 此处为啥 会报实参和形参参数不匹配 “reason: actual and formal argument lists differ in length”
        defaultInstance.dispatch(new EchoEvent("haha"));
    }
}
