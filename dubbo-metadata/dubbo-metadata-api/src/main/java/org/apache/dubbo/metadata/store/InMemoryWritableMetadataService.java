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
package org.apache.dubbo.metadata.store;

import org.apache.dubbo.common.BaseServiceMetadata;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.metadata.MetadataService;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;

import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Collections.*;
import static org.apache.dubbo.common.URL.buildKey;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_KEY;
import static org.apache.dubbo.common.utils.CollectionUtils.isEmpty;

/**
 * The {@link WritableMetadataService} implementation stores the metadata of Dubbo services in memory locally（存储到本地内存） when they
 * exported. It is used by server (provider).
 *
 * @see MetadataService
 * @see WritableMetadataService
 * @since 2.7.5
 */
public class InMemoryWritableMetadataService extends AbstractAbstractWritableMetadataService { //本地内存存储元数据

    private final Lock lock = new ReentrantLock();

    // =================================== Registration =================================== //

    /**
     * All exported {@link URL urls} {@link Map} whose key is the return value of {@link URL#getServiceKey()} method
     * and value is the {@link SortedSet sorted set} of the {@link URL URLs}
     */
    private final ConcurrentNavigableMap<String, SortedSet<URL>> exportedServiceURLs = new ConcurrentSkipListMap<>(); //navigable：[ˈnævɪɡəb(ə)l] adj. 可航行的

    // ==================================================================================== //

    // =================================== Subscription =================================== //

    /**
     * The subscribed {@link URL urls} {@link Map} of {@link MetadataService},
     * whose key is the return value of {@link URL#getServiceKey()} method and value is
     * the {@link SortedSet sorted set} of the {@link URL URLs}
     */
    /**
     * ConcurrentNavigableMap接口是ConcurrentMap接口的子接口，并且支持NavigableMap操作，并且对其可导航子映射和近似匹配进行递归。
     * NavigableMap扩展了SortedMap，具有了针对给定搜索目标返回最接近匹配项的导航方法。如"获取大于/等于某对象的键值对"、“获取小于/等于某对象的键值对”等等
     */
    private final ConcurrentNavigableMap<String, SortedSet<URL>> subscribedServiceURLs = new ConcurrentSkipListMap<>();

    /**
     * The {@link Map} caches the json of {@link ServiceDefinition} with
     * {@link BaseServiceMetadata#buildServiceKey(String, String, String) the service key}
     */
    private final ConcurrentNavigableMap<String, String> serviceDefinitions = new ConcurrentSkipListMap<>(); //服务key和服务json字符串的键值对

    @Override
    public SortedSet<String> getSubscribedURLs() {
        return getAllUnmodifiableServiceURLs(subscribedServiceURLs);
    }

    private SortedSet<String> getAllUnmodifiableServiceURLs(Map<String, SortedSet<URL>> serviceURLs) {//Unmodifiable:无法修改的
        SortedSet<URL> bizURLs = new TreeSet<>(InMemoryWritableMetadataService.URLComparator.INSTANCE);
        for (Map.Entry<String, SortedSet<URL>> entry : serviceURLs.entrySet()) {
            SortedSet<URL> urls = entry.getValue();
            if (urls != null) {
                for (URL url : urls) {
                    if (!MetadataService.class.getName().equals(url.getServiceInterface())) { //若url的"interface"参数值不是MetadataService接口名，则加到集合bizURLs中
                        bizURLs.add(url);
                    }
                }
            }
        }
        return MetadataService.toSortedStrings(bizURLs);
    }

    @Override
    public SortedSet<String> getExportedURLs(String serviceInterface, String group, String version, String protocol) {
        if (ALL_SERVICE_INTERFACES.equals(serviceInterface)) { //所有的服务的实例
            return getAllUnmodifiableServiceURLs(exportedServiceURLs);
        }
        String serviceKey = buildKey(serviceInterface, group, version);
        return unmodifiableSortedSet(getServiceURLs(exportedServiceURLs, serviceKey, protocol));
    }

    @Override
    public boolean exportURL(URL url) { //使用引用传递
        return addURL(exportedServiceURLs, url); //此处是引用传递，方法中对exportedServiceURLs的变更，该值也要变更
    }

    @Override
    public boolean unexportURL(URL url) {
        return removeURL(exportedServiceURLs, url);
    }

    @Override
    public boolean subscribeURL(URL url) {
        return addURL(subscribedServiceURLs, url);
    }

    @Override
    public boolean unsubscribeURL(URL url) { //取消订阅URL：将url从订阅的集合中移除
        return removeURL(subscribedServiceURLs, url);
    }

    @Override
    protected void publishServiceDefinition(String key, String json) { //发布服务定义信息：写到本地缓存中
        serviceDefinitions.put(key, json); //将服务key，与服务定义的json字符串存在本地缓存中
    }

    @Override
    public String getServiceDefinition(String serviceDefinitionKey) {
        return serviceDefinitions.get(serviceDefinitionKey);
    }

    public Map<String, SortedSet<URL>> getExportedServiceURLs() {
        return unmodifiableSortedMap(exportedServiceURLs);
    }

    public Map<String, SortedSet<URL>> getSubscribedServiceURLs() {
        return unmodifiableSortedMap(subscribedServiceURLs);
    }

    public Map<String, String> getServiceDefinitions() {
        return unmodifiableSortedMap(serviceDefinitions);
    }

    boolean addURL(Map<String, SortedSet<URL>> serviceURLs, URL url) { //使用函数传递
        return executeMutually(() -> { //使用线程异步设置Map值，此处是函数传递，把函数的实现用lambda写好后传递
            SortedSet<URL> urls = serviceURLs.computeIfAbsent(url.getServiceKey(), this::newSortedURLs); //会将serviceKey作为Map的key
            // make sure the parameters of tmpUrl is variable
            return urls.add(url); //此处serviceURLs变更，当前类的exportedServiceURLs也对应变更
        });
    }

    boolean removeURL(Map<String, SortedSet<URL>> serviceURLs, URL url) {
        return executeMutually(() -> {
            String key = url.getServiceKey();
            SortedSet<URL> urls = serviceURLs.getOrDefault(key, null);
            if (urls == null) {
                return true;
            }
            boolean r = urls.remove(url);
            // if it is empty
            if (urls.isEmpty()) {
                serviceURLs.remove(key);
            }
            return r;
        });
    }

    private SortedSet<URL> newSortedURLs(String serviceKey) {
        return new TreeSet<>(InMemoryWritableMetadataService.URLComparator.INSTANCE); //获取URLComparator实例，并设置到TreeSet中
    }

    boolean executeMutually(Callable<Boolean> callable) { //mutually：adv. 相互地，共同地
        boolean success = false;
        try {
            lock.lock(); //加锁处理
            try {
                success = callable.call();
            } catch (Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(e);
                }
            }
        } finally {
            lock.unlock(); //释放锁处理
        }
        return success;
    }

    private SortedSet<String> getServiceURLs(Map<String, SortedSet<URL>> exportedServiceURLs, String serviceKey,
                                             String protocol) {

        SortedSet<URL> serviceURLs = exportedServiceURLs.get(serviceKey);

        if (isEmpty(serviceURLs)) {
            return emptySortedSet();
        }

        return MetadataService.toSortedStrings(serviceURLs.stream().filter(url -> isAcceptableProtocol(protocol, url)));
    }

    private boolean isAcceptableProtocol(String protocol, URL url) {
        return protocol == null
                || protocol.equals(url.getParameter(PROTOCOL_KEY))
                || protocol.equals(url.getProtocol());
    }

    static class URLComparator implements Comparator<URL> { //url比较器

        public static final URLComparator INSTANCE = new URLComparator();

        @Override
        public int compare(URL o1, URL o2) {
            return o1.toFullString().compareTo(o2.toFullString());
        }
    }

}
