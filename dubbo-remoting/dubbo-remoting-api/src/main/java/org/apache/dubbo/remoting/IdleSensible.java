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
package org.apache.dubbo.remoting;

/**
 * Indicate whether the implementation (for both server and client) has the ability to sense and handle idle connection（空闲连接）.
 * If the server has the ability to handle idle connection, it should close the connection when it happens, and if
 * the client has the ability to handle idle connection, it should send the heartbeat（心跳） to the server.
 * （如果有能力处理空闲连接，它应该发送心跳给服务端）
 */
public interface IdleSensible {
    /**
     * @csy 该接口的功能用途是什么？
     * 解：Dubbo新增该接口，以区分Netty和其它通信组件对空闲连接的处理能力。NettyServer和NettyClient作为Netty通信组件的封装类实现了该接口，
     * 重写了接口中的方法，返回值为true，表示具有处理空闲连接的能力。也就是说，如果使用Netty作为通信组件，那么监控空闲连接就交给Netty底层自己处理。
     * 而其它通信组件暂不支持，因此仍然需要Dubbo框架的心跳设计方案。
     * https://gentryhuang.com/posts/4760cec/index.html
     *
     * Netty对空闲连接的检测提供了天然的支持，使用IdleStateHandler可以很方便的实现空闲检测逻辑。
     * 其内部使用了EventLoop.schedule(task) 来实现定时任务，使用该线程可以保证线程安全。
     */

    /**
     * Whether the implementation can sense and handle the idle connection. By default it's false, the implementation
     * relies on dedicated timer to take care of idle connection.
     *
     * @return whether has the ability to handle idle connection
     */
    default boolean canHandleIdle() { //是否具有处理空闲连接的能力
        return false;
    }
}
