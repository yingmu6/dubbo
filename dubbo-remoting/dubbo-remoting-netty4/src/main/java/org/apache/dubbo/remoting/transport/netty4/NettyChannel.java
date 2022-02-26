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
package org.apache.dubbo.remoting.transport.netty4;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.transport.AbstractChannel;
import org.apache.dubbo.remoting.utils.PayloadDropper;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * NettyChannel maintains the cache of channel.（NettyChannel维持通道Channel的缓存）
 */
final class NettyChannel extends AbstractChannel {

    private static final Logger logger = LoggerFactory.getLogger(NettyChannel.class);
    /**
     * the cache for netty channel and dubbo channel （Netty与Dubbo两者通道的映射）
     */
    private static final ConcurrentMap<Channel, NettyChannel> CHANNEL_MAP = new ConcurrentHashMap<Channel, NettyChannel>();
    /**
     * netty channel
     */
    private final Channel channel;

    private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>(); //通道中的属性值

    private final AtomicBoolean active = new AtomicBoolean(false);

    /**
     * The constructor of NettyChannel.
     * It is private so NettyChannel usually create by {@link NettyChannel#getOrAddChannel(Channel, URL, ChannelHandler)}
     *
     * @param channel netty channel
     * @param url
     * @param handler dubbo handler that contain netty handler
     */
    private NettyChannel(Channel channel, URL url, ChannelHandler handler) { //传入Netty的通道Channel，构建Dubbo的数据接口NettyChannel
        super(url, handler);
        if (channel == null) {
            throw new IllegalArgumentException("netty channel == null;");
        }
        this.channel = channel;
    }

    /**
     * Get dubbo channel by netty channel through channel cache.
     * Put netty channel into it if dubbo channel don't exist in the cache.
     *
     * @param ch      netty channel
     * @param url
     * @param handler dubbo handler that contain netty's handler
     * @return
     */
    static NettyChannel getOrAddChannel(Channel ch, URL url, ChannelHandler handler) {
        if (ch == null) {
            return null;
        }
        NettyChannel ret = CHANNEL_MAP.get(ch); //根据Netty通道查找对应的Dubbo封装的通道
        if (ret == null) {
            NettyChannel nettyChannel = new NettyChannel(ch, url, handler);
            if (ch.isActive()) { //通道是处理激活状态的
                nettyChannel.markActive(true);
                ret = CHANNEL_MAP.putIfAbsent(ch, nettyChannel);
            }
            if (ret == null) {
                ret = nettyChannel;
            }
        }
        return ret;
    }

    /**
     * Remove the inactive（不活跃的、闲置的） channel.
     *
     * @param ch netty channel
     */
    static void removeChannelIfDisconnected(Channel ch) { //todo @csy-02-26 netty是怎样表示通道是否活跃的？
        if (ch != null && !ch.isActive()) {
            NettyChannel nettyChannel = CHANNEL_MAP.remove(ch); //移除缓存中通道的映射，并且将激活标识置为false
            if (nettyChannel != null) {
                nettyChannel.markActive(false);
            }
        }
    }

    static void removeChannel(Channel ch) {
        if (ch != null) {
            NettyChannel nettyChannel = CHANNEL_MAP.remove(ch);
            if (nettyChannel != null) {
                nettyChannel.markActive(false);
            }
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) channel.localAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public boolean isConnected() {
        return !isClosed() && active.get();
    }

    public boolean isActive() {
        return active.get();
    }

    public void markActive(boolean isActive) {
        active.set(isActive);
    }

    /**
     * Send message by netty and whether to wait the completion of the send.
     * (通过Netty发送消息，并且可以选择等待发送)
     *
     * @param message message that need send.
     * @param sent    whether to ack async-sent
     * @throws RemotingException throw RemotingException if wait until timeout or any exception thrown by method body that surrounded by try-catch.
     */
    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        // whether the channel is closed
        super.send(message, sent);

        boolean success = true;
        int timeout = 0;
        try {
            //todo @csy-02-26 netty的writeAndFlush方法，待了解实践
            ChannelFuture future = channel.writeAndFlush(message); //使用Netty的Channel进行发送消息
            if (sent) { //若选择等待，则会等待指定的时间
                // wait timeout ms
                timeout = getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
                success = future.await(timeout);
            }
            Throwable cause = future.cause();
            if (cause != null) {
                throw cause;
            }
        } catch (Throwable e) {
            removeChannelIfDisconnected(channel);
            throw new RemotingException(this, "Failed to send message " + PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress() + ", cause: " + e.getMessage(), e);
        }
        if (!success) {
            throw new RemotingException(this, "Failed to send message " + PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress()
                    + "in timeout(" + timeout + "ms) limit");
        }
    }

    @Override
    public void close() {
        try {
            super.close(); //关闭dubbo的通道（将dubbo中的通道状态置为关闭状态）
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            removeChannelIfDisconnected(channel);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            attributes.clear(); //移除通道中设置的属性值
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Close netty channel " + channel);
            }
            channel.close(); //关闭Netty的通道
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        // The null value is unallowed in the ConcurrentHashMap.
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value); //往缓存中设置属性值
        }
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    public int hashCode() { //todo @csy-02-26 为什么重写equals时，也要重写hashcode方法
        final int prime = 31;
        int result = 1;
        result = prime * result + ((channel == null) ? 0 : channel.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }

        // FIXME: a hack to make org.apache.dubbo.remoting.exchange.support.DefaultFuture.closeChannel work
        if (obj instanceof NettyClient) {
            NettyClient client = (NettyClient) obj;
            return channel.equals(client.getNettyChannel());
        }

        if (getClass() != obj.getClass()) {
            return false;
        }
        NettyChannel other = (NettyChannel) obj;
        if (channel == null) {
            if (other.channel != null) {
                return false;
            }
        } else if (!channel.equals(other.channel)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "NettyChannel [channel=" + channel + "]";
    }

}
