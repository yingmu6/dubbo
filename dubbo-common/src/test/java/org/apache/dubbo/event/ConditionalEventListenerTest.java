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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ConditionalEventListener} test
 *
 * @since 2.7.5
 */
public class ConditionalEventListenerTest {

    private final EventDispatcher eventDispatcher = EventDispatcher.getDefaultExtension();

    @BeforeEach
    public void init() {
        eventDispatcher.removeAllEventListeners(); //测试前，先把缓存中事件监听器清除，避免有干扰
    }

    @Test
    public void testOnEvent() { //条件事件监听器测试

        OnlyHelloWorldEventListener listener = new OnlyHelloWorldEventListener();

        eventDispatcher.addEventListener(listener); //先将监听器添加到缓存中

        eventDispatcher.dispatch(new EchoEvent("1")); //由于监听器OnlyHelloWorldEventListener是ConditionalEventListener类型，所有会先执行accept()方法，满足条件才进行onEvent()事件处理

        assertNull(listener.getSource()); //事件对象值"1"，不满足accept()中的条件，所以不会进行事件处理

        eventDispatcher.dispatch(new EchoEvent("Hello,World"));

        assertEquals("Hello,World", listener.getSource()); //事件对象值"Hello,World"满足条件，所以就会执行监听器的onEvent()方法

        // fix EventDispatcherTest.testDefaultMethods may contain OnlyHelloWorldEventListener
        // ( ConditionalEventListenerTest and EventDispatcherTest are running together in one suite case )
        eventDispatcher.removeAllEventListeners();
    }

    static class OnlyHelloWorldEventListener implements ConditionalEventListener<EchoEvent> {

        private String source;

        @Override
        public boolean accept(EchoEvent event) {
            return "Hello,World".equals(event.getSource());
        }

        @Override
        public void onEvent(EchoEvent event) { //进行事件处理（处理逻辑根据具体业务场景而定）
            source = (String) event.getSource(); //将事件对象值存储起来
        }

        public String getSource() {
            return source;
        }
    }
}
