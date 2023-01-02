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
package org.apache.dubbo.monitor.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.monitor.Monitor;
import org.apache.dubbo.monitor.MonitorFactory;
import org.apache.dubbo.monitor.MonitorService;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;

/**
 * AbstractMonitorFactory. (SPI, Singleton, ThreadSafe)
 */
public abstract class AbstractMonitorFactory implements MonitorFactory {
    private static final Logger logger = LoggerFactory.getLogger(AbstractMonitorFactory.class);

    /**
     * The lock for getting monitor center (用于获取监控中心的锁)
     */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /**
     * The monitor centers Map<RegistryAddress, Registry>
     */
    private static final Map<String, Monitor> MONITORS = new ConcurrentHashMap<String, Monitor>(); //服务key与监控器的缓存

    private static final Map<String, CompletableFuture<Monitor>> FUTURES = new ConcurrentHashMap<String, CompletableFuture<Monitor>>();

    /**
     * The monitor create executor
     */
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(0, 10, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new NamedThreadFactory("DubboMonitorCreator", true));

    public static Collection<Monitor> getMonitors() {
        return Collections.unmodifiableCollection(MONITORS.values());
    }

    @Override
    public Monitor getMonitor(URL url) {
        url = url.setPath(MonitorService.class.getName()).addParameter(INTERFACE_KEY, MonitorService.class.getName());
        String key = url.toServiceStringWithoutResolving();
        Monitor monitor = MONITORS.get(key); //URL相同，对应的key也相同
        Future<Monitor> future = FUTURES.get(key);
        if (monitor != null || future != null) { //若缓存中存在，则从缓存中获取监控器
            return monitor;
        }

        LOCK.lock();
        try {
            monitor = MONITORS.get(key); //公共逻辑处理
            future = FUTURES.get(key);
            if (monitor != null || future != null) { //再次尝试从缓存中获取监控器
                return monitor;
            }

            final URL monitorUrl = url; //若缓存中不存在，则创建监控器
            final CompletableFuture<Monitor> completableFuture = CompletableFuture.supplyAsync(() -> AbstractMonitorFactory.this.createMonitor(monitorUrl)); //异步地创建监控器
            FUTURES.put(key, completableFuture); //将创建监控器对应的Future缓存起来
            completableFuture.thenRunAsync(new MonitorListener(key), EXECUTOR); //异步执行给定的动作

            return null; //返回null值，要么等待一会儿从缓存中获取值，要么从缓存中获取CompletableFuture去获取值（因为是异步创建监控器的，此处是快速结束，接口使用Future去获取）
        } finally {
            // unlock
            LOCK.unlock();
        }
    }

    protected abstract Monitor createMonitor(URL url); //创建监控器的具体实现交由子类执行


    class MonitorListener implements Runnable { //监控中心的监听器

        private String key;

        public MonitorListener(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            try {
                CompletableFuture<Monitor> completableFuture = AbstractMonitorFactory.FUTURES.get(key);
                AbstractMonitorFactory.MONITORS.put(key, completableFuture.get()); //异步获取到监听器，并设置到缓存中
                AbstractMonitorFactory.FUTURES.remove(key);
            } catch (InterruptedException e) {
                logger.warn("Thread was interrupted unexpectedly, monitor will never be got.");
                AbstractMonitorFactory.FUTURES.remove(key);
            } catch (ExecutionException e) {
                logger.warn("Create monitor failed, monitor data will not be collected until you fix this problem. ", e);
            }
        }
    }

}
