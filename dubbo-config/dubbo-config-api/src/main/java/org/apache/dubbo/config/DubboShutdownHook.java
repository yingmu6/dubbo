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
package org.apache.dubbo.config;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.lang.ShutdownHookCallbacks;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.event.DubboServiceDestroyedEvent;
import org.apache.dubbo.config.event.DubboShutdownHookRegisteredEvent;
import org.apache.dubbo.config.event.DubboShutdownHookUnregisteredEvent;
import org.apache.dubbo.event.Event;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.registry.support.AbstractRegistryFactory;
import org.apache.dubbo.rpc.Protocol;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The shutdown hook thread to do the clean up stuff. (停机的钩子线程，用于做清除处理)
 * This is a singleton in order to ensure there is only one shutdown hook registered.
 * Because {@link ApplicationShutdownHooks} use {@link java.util.IdentityHashMap}
 * to store the shutdown hooks.
 */
public class DubboShutdownHook extends Thread { //dubbo停机的钩子线程（单例模式）

    private static final Logger logger = LoggerFactory.getLogger(DubboShutdownHook.class);

    private static final DubboShutdownHook DUBBO_SHUTDOWN_HOOK = new DubboShutdownHook("DubboShutdownHook"); //创建单实例（饿汉模式）

    private final ShutdownHookCallbacks callbacks = ShutdownHookCallbacks.INSTANCE;

    /**
     * Has it already been registered or not?
     */
    private final AtomicBoolean registered = new AtomicBoolean(false); //是否已经注册了钩子函数

    /**
     * Has it already been destroyed or not?
     */
    private static final AtomicBoolean destroyed = new AtomicBoolean(false); //是否已经销毁

    private final EventDispatcher eventDispatcher = EventDispatcher.getDefaultExtension();

    private DubboShutdownHook(String name) { //构造方法是私有的，提供的对象是单实例
        super(name);
    }

    public static DubboShutdownHook getDubboShutdownHook() {
        return DUBBO_SHUTDOWN_HOOK;
    }

    @Override
    public void run() { //钩子线程的执行逻辑（做停机时的清除处理）
        if (logger.isInfoEnabled()) {
            logger.info("Run shutdown hook now.");
        }

        callback(); //执行所有回调接口的逻辑
        doDestroy(); //派发销毁事件
    }

    /**
     * For testing purpose
     */
    void clear() {
        callbacks.clear();
    }

    private void callback() {
        callbacks.callback();
    }

    /**
     * Register the ShutdownHook
     */
    public void register() { //注册停机钩子线程
        if (registered.compareAndSet(false, true)) {
            DubboShutdownHook dubboShutdownHook = getDubboShutdownHook();
            Runtime.getRuntime().addShutdownHook(dubboShutdownHook);
            dispatch(new DubboShutdownHookRegisteredEvent(dubboShutdownHook)); //发布停机钩子线程注册的事件
        }
    }

    /**
     * Unregister the ShutdownHook
     */
    public void unregister() {
        if (registered.compareAndSet(true, false)) {
            DubboShutdownHook dubboShutdownHook = getDubboShutdownHook();
            Runtime.getRuntime().removeShutdownHook(dubboShutdownHook);
            dispatch(new DubboShutdownHookUnregisteredEvent(dubboShutdownHook));
        }
    }

    /**
     * Destroy all the resources, including registries and protocols.
     */
    public void doDestroy() {
        // dispatch the DubboDestroyedEvent @since 2.7.5
        dispatch(new DubboServiceDestroyedEvent(this));
    }

    private void dispatch(Event event) {
        eventDispatcher.dispatch(event);
    }

    public boolean getRegistered() {
        return registered.get();
    }

    public static void destroyAll() { //销毁所有内容
        if (destroyed.compareAndSet(false, true)) {
            AbstractRegistryFactory.destroyAll();
            destroyProtocols();
        }
    }

    /**
     * Destroy all the protocols.
     */
    public static void destroyProtocols() {
        ExtensionLoader<Protocol> loader = ExtensionLoader.getExtensionLoader(Protocol.class);
        for (String protocolName : loader.getLoadedExtensions()) {
            try {
                Protocol protocol = loader.getLoadedExtension(protocolName);
                if (protocol != null) {
                    protocol.destroy(); //各个协议进行销毁操作
                }
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }
        }
    }
}
