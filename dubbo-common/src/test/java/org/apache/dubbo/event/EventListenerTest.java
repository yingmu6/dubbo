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

import static org.apache.dubbo.event.EventListener.findEventType;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link EventListener} Test
 *
 * @since 2.7.5
 */
public class EventListenerTest {

    @Test
    public void testFindEventHierarchicalTypes() { //查找事件类型
        assertEquals(EchoEvent.class, findEventType(new EchoEventListener()));
        assertEquals(Event.class, findEventType(new EchoEventListener2())); //1）传入监听器对象实例

        assertEquals(EchoEvent.class, findEventType(EchoEventListener.class)); //2）传入Class对象
        assertEquals(Event.class, findEventType(EchoEventListener2.class));

        /**
         * 结果分析：
         * 1）传入对象实例和Class对象的这两种方式都是可以的，因为内部使用了方法重载，最终都是处理Class对象
         * 2）查找事件类型的处理流程为：先找到事件监听器类中包含泛化参数的Class集合（来自继承和实现的Class集合），再依次遍历这些Class。
         *    找到类型为EventListener，且泛型参数为Event的Class，取泛化参数的实际类型，即为最终找到的事件类型
         *
         *    举例说明：
         *    2.1）EchoEventListener继承和实现的Class集合为：AbstractEventListener<EchoEvent>、Serilizable
         *         a）通过ReflectUtils#findParameterizedTypes找到含有泛化参数的Class为AbstractEventListener<EchoEvent>
         *         b）再通过EventListener#findEventType进行判断处理，由于AbstractEventListener是EventListener，即为事件监听器，
         *            所以取它的泛化参数EchoEvent，因为该类型是Event，符合要求，所以即为最终要找的类型。
         *
         *    2.2）EchoEventListener2 继承和实现的Class集合为：Vector<EventListener<Event>>、Serializable、EventListener<Event>
         *         a）通过ReflectUtils#findParameterizedTypes找到含有泛化参数的Class为Vector<EventListener<Event>>、EventListener<Event>
         *         b）再通过EventListener#findEventType进行判断处理，由于Vector<EventListener<Event>>不是EventListener，所以被排除。
         *            而EventListener<Event>符合要求，继续参与判断，取出它的泛化参数，因为该类型是Event，符合要求，所以即为最终要找的类型。
         */
    }

    @Test
    public void testOnEvent() {
    }

}