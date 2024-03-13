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
package org.apache.dubbo.config.utils;

import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ReferenceConfigBase;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.service.Destroyable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A simple util class for cache {@link ReferenceConfigBase}.
 * <p>
 * {@link ReferenceConfigBase} is a heavy Object（比较重的对象）, it's necessary to cache these object
 * for the framework which create {@link ReferenceConfigBase} frequently.（ReferenceConfigBase比较重，有必要进行缓存）
 * <p>
 * You can implement and use your own {@link ReferenceConfigBase} cache if you need use complicate（使复杂） strategy.
 */
public class ReferenceConfigCache {
    public static final String DEFAULT_NAME = "_DEFAULT_";
    /**
     * Create the key with the <b>Group</b>, <b>Interface</b> and <b>version</b> attribute of {@link ReferenceConfigBase}.
     * <p>
     * key example: <code>group1/org.apache.dubbo.foo.FooService:1.0.0</code>.
     */
    public static final KeyGenerator DEFAULT_KEY_GENERATOR = referenceConfig -> { //默认的key产生器（此处等价KeyGenerator匿名类，括号中的实现相当于KeyGenerator#generateKey方法体逻辑）
        String iName = referenceConfig.getInterface();
        if (StringUtils.isBlank(iName)) {
            Class<?> clazz = referenceConfig.getInterfaceClass();
            iName = clazz.getName();
        }
        if (StringUtils.isBlank(iName)) {
            throw new IllegalArgumentException("No interface info in ReferenceConfig" + referenceConfig);
        }

        StringBuilder ret = new StringBuilder();
        if (!StringUtils.isBlank(referenceConfig.getGroup())) {
            ret.append(referenceConfig.getGroup()).append("/");
        }
        ret.append(iName);
        if (!StringUtils.isBlank(referenceConfig.getVersion())) {
            ret.append(":").append(referenceConfig.getVersion());
        }
        return ret.toString();
    };

    static final ConcurrentMap<String, ReferenceConfigCache> CACHE_HOLDER = new ConcurrentHashMap<String, ReferenceConfigCache>();
    private final String name;
    private final KeyGenerator generator;

    private final ConcurrentMap<String, ReferenceConfigBase<?>> referredReferences = new ConcurrentHashMap<>(); //接口key与ReferenceConfig的映射

    private final ConcurrentMap<Class<?>, ConcurrentMap<String, Object>> proxies = new ConcurrentHashMap<>(); //接口Class与接口代理对象的映射，内容为：<Class,<key,proxy@xxx>>，该缓存表明一个引用的接口，可能对应不同group、version的代理对象，体现group或version隔离

    private ReferenceConfigCache(String name, KeyGenerator generator) {
        this.name = name;
        this.generator = generator;
    }

     /**
     * Get the cache use default name and {@link #DEFAULT_KEY_GENERATOR} to generate cache key.
     * Create cache if not existed yet.
     */
    public static ReferenceConfigCache getCache() {
        return getCache(DEFAULT_NAME);
    }

    /**
     * Get the cache use specified name and {@link KeyGenerator}.
     * Create cache if not existed yet.
     */
    public static ReferenceConfigCache getCache(String name) {
        return getCache(name, DEFAULT_KEY_GENERATOR);
    }

    /**
     * Get the cache use specified {@link KeyGenerator}.
     * Create cache if not existed yet.（缓存中不存在，则会创建）
     */
    public static ReferenceConfigCache getCache(String name, KeyGenerator keyGenerator) {
        return CACHE_HOLDER.computeIfAbsent(name, k -> new ReferenceConfigCache(k, keyGenerator));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ReferenceConfigBase<T> referenceConfig) { //获取服务的代理对象（与ReferenceConfig#get效果一样）
        String key = generator.generateKey(referenceConfig);
        Class<?> type = referenceConfig.getInterfaceClass();

        proxies.computeIfAbsent(type, _t -> new ConcurrentHashMap<>());

        ConcurrentMap<String, Object> proxiesOfType = proxies.get(type);
        proxiesOfType.computeIfAbsent(key, _k -> { //设置缓存
            Object proxy = referenceConfig.get();
            referredReferences.put(key, referenceConfig);
            return proxy;
        });

        return (T) proxiesOfType.get(key);
    }

    /**
     * Fetch cache with the specified key. The key is decided by KeyGenerator passed-in. If the default KeyGenerator is
     * used, then the key is in the format of <code>group/interfaceClass:version</code>
     *
     * @param key  cache key
     * @param type object class
     * @param <T>  object type
     * @return object from the cached ReferenceConfigBase
     * @see KeyGenerator#generateKey(ReferenceConfigBase)
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Map<String, Object> proxiesOfType = proxies.get(type); //先按接口type获取
        if (CollectionUtils.isEmptyMap(proxiesOfType)) {
            return null;
        }
        return (T) proxiesOfType.get(key); //再按key获取到缓存
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        ReferenceConfigBase<?> rc = referredReferences.get(key);
        if (rc == null) {
            return null;
        }

        return (T) get(key, rc.getInterfaceClass());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getAll(Class<T> type) {
        Map<String, Object> proxiesOfType = proxies.get(type);
        if (CollectionUtils.isEmptyMap(proxiesOfType)) {
            return Collections.emptyList();
        }

        List<T> proxySet = new ArrayList<>();
        proxiesOfType.values().forEach(obj -> proxySet.add((T) obj));
        return proxySet;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Map<String, Object> proxiesOfType = proxies.get(type);
        if (CollectionUtils.isEmptyMap(proxiesOfType)) {
            return null;
        }

        return (T) proxiesOfType.values().iterator().next(); //取列表中的第一个（没有get(String key, Class<T> type)获取精准）
    }

    public void destroy(String key, Class<?> type) {
        ReferenceConfigBase<?> rc = referredReferences.remove(key);
        if (rc == null) {
            return;
        }

        ApplicationModel.getConfigManager().removeConfig(rc);
        rc.destroy();

        Map<String, Object> proxiesOftype = proxies.get(type);
        if (CollectionUtils.isNotEmptyMap(proxiesOftype)) {
            proxiesOftype.remove(key);
            if (proxiesOftype.isEmpty()) {
                proxies.remove(type);
            }
        }
    }

    public void destroy(Class<?> type) {
        Map<String, Object> proxiesOfType = proxies.remove(type);
        proxiesOfType.forEach((k, v) -> {
            ReferenceConfigBase rc = referredReferences.remove(k);
            rc.destroy();
        });
    }

    /**
     * clear and destroy one {@link ReferenceConfigBase} in the cache.
     *
     * @param referenceConfig use for create key.
     */
    public <T> void destroy(ReferenceConfigBase<T> referenceConfig) {
        String key = generator.generateKey(referenceConfig);
        Class<?> type = referenceConfig.getInterfaceClass();

        destroy(key, type);
    }

    /**
     * clear and destroy all {@link ReferenceConfigBase} in the cache.
     */
    public void destroyAll() {
        if (CollectionUtils.isEmptyMap(referredReferences)) {
            return;
        }

        referredReferences.forEach((_k, referenceConfig) -> {
            referenceConfig.destroy();
            ApplicationModel.getConfigManager().removeConfig(referenceConfig);
        });

        proxies.forEach((_type, proxiesOfType) -> {
            proxiesOfType.forEach((_k, v) -> {
                Destroyable proxy = (Destroyable) v;
                proxy.$destroy();
            });
        });

        referredReferences.clear();
        proxies.clear();
    }

    public ConcurrentMap<String, ReferenceConfigBase<?>> getReferredReferences() {
        return referredReferences;
    }

    public ConcurrentMap<Class<?>, ConcurrentMap<String, Object>> getProxies() {
        return proxies;
    }

    @Override
    public String toString() {
        return "ReferenceConfigCache(name: " + name
                + ")";
    }

    public interface KeyGenerator { //key产生器
        String generateKey(ReferenceConfigBase<?> referenceConfig); //产生服务对应的key
    }
}
