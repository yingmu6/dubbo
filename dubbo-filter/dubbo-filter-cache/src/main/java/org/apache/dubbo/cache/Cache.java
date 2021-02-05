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
package org.apache.dubbo.cache;

/**
 * Cache interface to support storing and retrieval（检索） of value against a lookup key（关键值）. It has two operation <b>get</b> and <b>put</b>.
 * <li><b>put</b>-Storing value against a key.</li>
 * <li><b>get</b>-Retrieval of object.</li>
 * @see org.apache.dubbo.cache.support.lru.LruCache
 * @see org.apache.dubbo.cache.support.jcache.JCache
 * @see org.apache.dubbo.cache.support.expiring.ExpiringCache
 * @see org.apache.dubbo.cache.support.threadlocal.ThreadLocalCache
 */
public interface Cache { //@csy 什么场景下会用到缓存？为啥会用到filter包、不用到common包？
    /**
     *  解：通过缓存结果加速访问速度
     *  结果缓存，用于加速热门数据的访问速度，Dubbo 提供声明式缓存，以减少用户加缓存的工作量。
     *  缓存类型:
     *   lru 基于最近最少使用原则删除多余缓存，保持最热的数据被缓存。
     *   threadlocal 当前线程缓存，比如一个页面渲染，用到很多 portal，每个 portal 都要去查用户信息，通过线程缓存，可以减少这种多余访问。
     *   jcache 与 JSR107 集成，可以桥接各种缓存实现
     */

    // todo @csy 缓存没有有效期吗？剔除策略是怎样的？

/**
     * API to store value against a key
     * @param key  Unique identifier（唯一的标识） for the object being store.
     * @param value Value getting store
     */
    void put(Object key, Object value); //@csy 相同的key，value是否会被覆盖 解：会被覆盖了，底层用到map的数据结构

    /**
     * API to return stored value using a key.
     * @param key Unique identifier for cache lookup
     * @return Return stored object against key
     */
    Object get(Object key);

}
