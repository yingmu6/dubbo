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
package org.apache.dubbo.registry.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.Invoker;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.registry.Constants.REGISTRY_RECONNECT_PERIOD_KEY;

/**
 * DubboRegistry
 */
public class DubboRegistry extends FailbackRegistry { //dubbo实现的注册中心

    private final static Logger logger = LoggerFactory.getLogger(DubboRegistry.class);

    // Reconnecting detection cycle（重连的检测周期）: 3 seconds (unit:millisecond)
    private static final int RECONNECT_PERIOD_DEFAULT = 3 * 1000;

    // Scheduled executor service
    private final ScheduledExecutorService reconnectTimer = Executors.newScheduledThreadPool(1, new NamedThreadFactory("DubboRegistryReconnectTimer", true));

    // Reconnection timer（重连的定时器）, regular check connection is available. If unavailable, unlimited reconnection（若不可用，无限重连）.
    private final ScheduledFuture<?> reconnectFuture;

    // The lock for client acquisition process, lock the creation process of the client instance to prevent repeated clients（锁定创建过程，防止产生重复的客户端）
    private final ReentrantLock clientLock = new ReentrantLock();

    private final Invoker<RegistryService> registryInvoker; //注册服务对应的Invoker

    private final RegistryService registryService; //注册服务

    /**
     * The time in milliseconds the reconnectTimer will wait
     */
    private final int reconnectPeriod;

    public DubboRegistry(Invoker<RegistryService> registryInvoker, RegistryService registryService) {
        super(registryInvoker.getUrl()); //调用父类FailbackRegistry的构造函数，执行初始化操作
        this.registryInvoker = registryInvoker;
        this.registryService = registryService;
        // Start reconnection timer（启动重连定时器）
        this.reconnectPeriod = registryInvoker.getUrl().getParameter(REGISTRY_RECONNECT_PERIOD_KEY, RECONNECT_PERIOD_DEFAULT);
        reconnectFuture = reconnectTimer.scheduleWithFixedDelay(() -> { //创建重连的定时任务
            // Check and connect to the registry
            try {
                connect();
            } catch (Throwable t) { // Defensive fault tolerance
                logger.error("Unexpected error occur at reconnect, cause: " + t.getMessage(), t);
            }
        }, reconnectPeriod, reconnectPeriod, TimeUnit.MILLISECONDS);
    }

    protected final void connect() {
        try {
            // Check whether or not it is connected
            if (isAvailable()) {
                return;
            }
            if (logger.isInfoEnabled()) {
                logger.info("Reconnect to registry " + getUrl());
            }
            clientLock.lock(); //加锁处理
            try {
                // Double check whether or not it is connected
                if (isAvailable()) { //检查缓存中的registryInvoker是否有效
                    return;
                }
                recover();
            } finally {
                clientLock.unlock();
            }
        } catch (Throwable t) { // Ignore all the exceptions and wait for the next retry
            if (getUrl().getParameter(Constants.CHECK_KEY, true)) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                throw new RuntimeException(t.getMessage(), t);
            }
            logger.error("Failed to connect to registry " + getUrl().getAddress() + " from provider/consumer " + NetUtils.getLocalHost() + " use dubbo " + Version.getVersion() + ", cause: " + t.getMessage(), t);
        }
    }

    @Override
    public boolean isAvailable() { //是否有效
        if (registryInvoker == null) {
            return false;
        }
        return registryInvoker.isAvailable();
    }

    @Override
    public void destroy() {
        super.destroy();
        try {
            // Cancel the reconnection timer
            ExecutorUtil.cancelScheduledFuture(reconnectFuture);
        } catch (Throwable t) {
            logger.warn("Failed to cancel reconnect timer", t);
        }
        registryInvoker.destroy();
        ExecutorUtil.gracefulShutdown(reconnectTimer, reconnectPeriod);
    }

    @Override
    public void doRegister(URL url) {
        registryService.register(url);
    }

    @Override
    public void doUnregister(URL url) {
        registryService.unregister(url);
    }

    @Override
    public void doSubscribe(URL url, NotifyListener listener) {
        registryService.subscribe(url, listener);
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        registryService.unsubscribe(url, listener);
    }

    @Override
    public List<URL> lookup(URL url) {
        return registryService.lookup(url);
    }

}
