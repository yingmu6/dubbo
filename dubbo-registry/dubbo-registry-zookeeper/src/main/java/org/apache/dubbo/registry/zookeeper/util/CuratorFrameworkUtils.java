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
package org.apache.dubbo.registry.zookeeper.util;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstanceBuilder;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.zookeeper.ZookeeperInstance;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.curator.x.discovery.ServiceInstance.builder;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkParams.*;

/**
 * Curator Framework Utilities Class
 *
 * @since 2.7.5
 */
public abstract class CuratorFrameworkUtils { //curator客户端工具类

    // 创建ServiceDiscovery实例
    public static ServiceDiscovery<ZookeeperInstance> buildServiceDiscovery(CuratorFramework curatorFramework,
                                                                            String basePath) {
        return ServiceDiscoveryBuilder.builder(ZookeeperInstance.class) //通过ServiceDiscoveryBuilder构建器进行构建
                .client(curatorFramework)
                .basePath(basePath)
                .build();
    }

    // 构建Zookeeper客户端
    public static CuratorFramework buildCuratorFramework(URL connectionURL) throws Exception { //CuratorFramework：Zookeeper框架客户端
        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(connectionURL.getIp() + ":" + connectionURL.getPort()) //指定连接的地址
                .retryPolicy(buildRetryPolicy(connectionURL)) //指定重试策略
                .build();
        curatorFramework.start(); //启动客户端
        curatorFramework.blockUntilConnected(BLOCK_UNTIL_CONNECTED_WAIT.getParameterValue(connectionURL),
                BLOCK_UNTIL_CONNECTED_UNIT.getParameterValue(connectionURL)); // 阻塞直到与ZooKeeper连接可用或已超过maxWaitTime
        return curatorFramework;
    }

    public static RetryPolicy buildRetryPolicy(URL connectionURL) { //构建curator的重试策略
        int baseSleepTimeMs = BASE_SLEEP_TIME.getParameterValue(connectionURL);
        int maxRetries = MAX_RETRIES.getParameterValue(connectionURL);
        int getMaxSleepMs = MAX_SLEEP.getParameterValue(connectionURL);
        return new ExponentialBackoffRetry(baseSleepTimeMs, maxRetries, getMaxSleepMs);
    }


    public static List<ServiceInstance> build(Collection<org.apache.curator.x.discovery.ServiceInstance<ZookeeperInstance>>
                                                      instances) {
        return instances.stream().map(CuratorFrameworkUtils::build).collect(Collectors.toList());
    }

    // 将Zookeeper的ServiceInstance转换为Dubbo定义的ServiceInstance
    public static ServiceInstance build(org.apache.curator.x.discovery.ServiceInstance<ZookeeperInstance> instance) {
        String name = instance.getName();
        String host = instance.getAddress();
        int port = instance.getPort();
        ZookeeperInstance zookeeperInstance = instance.getPayload();
        DefaultServiceInstance serviceInstance = new DefaultServiceInstance(instance.getId(), name, host, port);
        serviceInstance.setMetadata(zookeeperInstance.getMetadata());
        return serviceInstance;
    }

    // 将Dubbo定义的ServiceInstance转换为Zookeeper的ServiceInstance转换
    public static org.apache.curator.x.discovery.ServiceInstance<ZookeeperInstance> build(ServiceInstance serviceInstance) {
        ServiceInstanceBuilder builder = null;
        String serviceName = serviceInstance.getServiceName();
        String host = serviceInstance.getHost();
        int port = serviceInstance.getPort();
        Map<String, String> metadata = serviceInstance.getMetadata();
        String id = generateId(host, port);
        ZookeeperInstance zookeeperInstance = new ZookeeperInstance(null, serviceName, metadata);
        try {
            builder = builder()
                    .id(id)
                    .name(serviceName)
                    .address(host)
                    .port(port)
                    .payload(zookeeperInstance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return builder.build();
    }

    public static final String generateId(String host, int port) { //产生服务id
        return host + ":" + port;
    }
}
