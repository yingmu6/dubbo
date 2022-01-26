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
package org.apache.dubbo.remoting.exchange.support;

import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.remoting.telnet.support.TelnetHandlerAdapter;
import org.apache.dubbo.remoting.transport.ChannelHandlerDispatcher;

import java.util.concurrent.CompletableFuture;

/**
 * ExchangeHandlerDispatcher
 */
public class ExchangeHandlerDispatcher implements ExchangeHandler {
    /**
     * 派发器的具体执行流程是怎样的？
     * 解答：ExchangeHandlerDispatcher继承了ExchangeHandler，持有了处理分发器ChannelHandlerDispatcher的对象，
     * 当调用send方法时会去调用分发器的send方法，触发ChannelHandler对象列表的sent方法，遍历列表去执行，同理比如连接/断开都会逐一调用。
     * 处理器集合会在调用HeaderExchanger的connect/bind的时候进行初始化。
     */

    /**
     * 事件派发器模式
     * 1）在项目开发中，会遇到自己的服务订阅、接收来自消息队列或者客户端的事件和请求，基于不同的事件采取对应的行动，这种情况下适合应用派发器模式
     * 2）XXXEventDispatcher类：核心类，维护事件类型（EventType）到处理器（handler）的映射（存放在ConcurrentHashMap中）；这个类在启动时，
     * 会通过XXXEventHandlerInitializer初始化这个map数据结构；在启动时，需要订阅或监听来自消息队列的事件；当对应的事件到达时，
     * 该类的dispatch方法会负责将事件分发到具体的处理器方法中进行处理。
     * <p>
     * https://segmentfault.com/a/1190000007403661
     */

    private final ReplierDispatcher replierDispatcher;

    private final ChannelHandlerDispatcher handlerDispatcher;

    private final TelnetHandler telnetHandler;

    /**
     * 提供多种构造方法
     */
    public ExchangeHandlerDispatcher() {
        replierDispatcher = new ReplierDispatcher();
        handlerDispatcher = new ChannelHandlerDispatcher();
        telnetHandler = new TelnetHandlerAdapter();
    }

    public ExchangeHandlerDispatcher(Replier<?> replier) {
        replierDispatcher = new ReplierDispatcher(replier);
        handlerDispatcher = new ChannelHandlerDispatcher();
        telnetHandler = new TelnetHandlerAdapter();
    }

    public ExchangeHandlerDispatcher(ChannelHandler... handlers) {
        replierDispatcher = new ReplierDispatcher();
        handlerDispatcher = new ChannelHandlerDispatcher(handlers);
        telnetHandler = new TelnetHandlerAdapter();
    }

    public ExchangeHandlerDispatcher(Replier<?> replier, ChannelHandler... handlers) {
        replierDispatcher = new ReplierDispatcher(replier);
        handlerDispatcher = new ChannelHandlerDispatcher(handlers);
        telnetHandler = new TelnetHandlerAdapter();
    }

    public ExchangeHandlerDispatcher addChannelHandler(ChannelHandler handler) {
        handlerDispatcher.addChannelHandler(handler);
        return this;
    }

    public ExchangeHandlerDispatcher removeChannelHandler(ChannelHandler handler) {
        handlerDispatcher.removeChannelHandler(handler);
        return this;
    }

    public <T> ExchangeHandlerDispatcher addReplier(Class<T> type, Replier<T> replier) {
        replierDispatcher.addReplier(type, replier);
        return this;
    }

    public <T> ExchangeHandlerDispatcher removeReplier(Class<T> type) {
        replierDispatcher.removeReplier(type);
        return this;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public CompletableFuture<Object> reply(ExchangeChannel channel, Object request) throws RemotingException {
        return CompletableFuture.completedFuture(((Replier) replierDispatcher).reply(channel, request));
    }

    @Override
    public void connected(Channel channel) {
        handlerDispatcher.connected(channel);
    }

    @Override
    public void disconnected(Channel channel) {
        handlerDispatcher.disconnected(channel);
    }

    @Override
    public void sent(Channel channel, Object message) {
        handlerDispatcher.sent(channel, message);
    }

    @Override
    public void received(Channel channel, Object message) {
        handlerDispatcher.received(channel, message);
    }

    @Override
    public void caught(Channel channel, Throwable exception) {
        handlerDispatcher.caught(channel, exception);
    }

    @Override
    public String telnet(Channel channel, String message) throws RemotingException {
        return telnetHandler.telnet(channel, message);
    }

}
