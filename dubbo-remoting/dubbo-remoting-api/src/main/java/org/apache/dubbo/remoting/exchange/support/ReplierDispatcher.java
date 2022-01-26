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

import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReplierDispatcher
 */
public class ReplierDispatcher implements Replier<Object> {
    /**
     * 此类的功能用途是什么？
     * 解：响应功能的派发器
     */
    private final Replier<?> defaultReplier;

    private final Map<Class<?>, Replier<?>> repliers = new ConcurrentHashMap<Class<?>, Replier<?>>(); //维护着事件类型（EventType）到处理器（handler）的映射

    public ReplierDispatcher() {
        this(null, null);
    }

    public ReplierDispatcher(Replier<?> defaultReplier) {
        this(defaultReplier, null);
    }

    public ReplierDispatcher(Replier<?> defaultReplier, Map<Class<?>, Replier<?>> repliers) {
        this.defaultReplier = defaultReplier;
        if (repliers != null && repliers.size() > 0) {
            this.repliers.putAll(repliers);
        }
    }

    public <T> ReplierDispatcher addReplier(Class<T> type, Replier<T> replier) { //添加事件类型与处理器的映射
        repliers.put(type, replier);
        return this;
    }

    public <T> ReplierDispatcher removeReplier(Class<T> type) {
        repliers.remove(type);
        return this;
    }

    private Replier<?> getReplier(Class<?> type) {
        for (Map.Entry<Class<?>, Replier<?>> entry : repliers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue();
            }
        }
        if (defaultReplier != null) {
            return defaultReplier;
        }
        throw new IllegalStateException("Replier not found, Unsupported message object: " + type);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object reply(ExchangeChannel channel, Object request) throws RemotingException { //根据不同的请求类型Class，做不同的响应回复reply
        return ((Replier) getReplier(request.getClass())).reply(channel, request);
    }

}
