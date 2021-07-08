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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * URL statistics. (API, Cached, ThreadSafe)
 *
 * @see org.apache.dubbo.rpc.filter.ActiveLimitFilter
 * @see org.apache.dubbo.rpc.filter.ExecuteLimitFilter
 */
public class RpcStatus { //都记录了哪些状态值？后台管理页面有没有展示，解：激活数、总数等

    /**
     * ConcurrentMap：https://www.jianshu.com/p/8f7b2cd34c47
     * ConcurrentMap，它是一个接口，是一个能够支持并发访问的java.util.map集合
     * ConcurrentHashMap是一个线程安全，并且是一个高效的HashMap
     * <p>
     * HashTable与HashMap的结构一致，都是哈希表实现。HashTable线程安全的，HashMap不是线程安全的
     * https://crossoverjie.top/2018/07/23/java-senior/ConcurrentHashMap/
     */
    private static final ConcurrentMap<String, RpcStatus> SERVICE_STATISTICS = new ConcurrentHashMap<String, RpcStatus>(); //服务的调用统计

    private static final ConcurrentMap<String, ConcurrentMap<String, RpcStatus>> METHOD_STATISTICS = new ConcurrentHashMap<String, ConcurrentMap<String, RpcStatus>>(); //方法的调用统计
    private final ConcurrentMap<String, Object> values = new ConcurrentHashMap<String, Object>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicLong total = new AtomicLong();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicLong totalElapsed = new AtomicLong();
    private final AtomicLong failedElapsed = new AtomicLong();
    private final AtomicLong maxElapsed = new AtomicLong();
    private final AtomicLong failedMaxElapsed = new AtomicLong();
    private final AtomicLong succeededMaxElapsed = new AtomicLong();

    private RpcStatus() {
    }

    /**
     * @param url
     * @return status
     */
    public static RpcStatus getStatus(URL url) {// 从缓存中获取指定的值
        String uri = url.toIdentityString();
        return SERVICE_STATISTICS.computeIfAbsent(uri, key -> new RpcStatus()); //在键key不存在时，设置value，存在时直接返回key对应的value
    }

    /**
     * @param url
     */
    public static void removeStatus(URL url) {
        String uri = url.toIdentityString();
        SERVICE_STATISTICS.remove(uri);
    }

    /**
     * @param url
     * @param methodName
     * @return status
     */
    public static RpcStatus getStatus(URL url, String methodName) { //获取方法对应的信息
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.computeIfAbsent(uri, k -> new ConcurrentHashMap<>()); //ConcurrentMap了解，computeIfAbsent方法了解
        return map.computeIfAbsent(methodName, k -> new RpcStatus()); //此处的含义是什么？函数式接口，设置方法名与RpcStatus的关系
    }

    /**
     * @param url
     */
    public static void removeStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.get(uri);
        if (map != null) {
            map.remove(methodName);
        }
    }

    public static void beginCount(URL url, String methodName) {
        beginCount(url, methodName, Integer.MAX_VALUE);
    }

    /**
     * @param url
     * @csy-017-P2 是对应什么数据进行计数？
     * 解：判断是否能开始计数
     * 判断标准： 是否达到整数最大值Integer.MAX_VALUE，是否尝试加1后超过设置的最大值
     * 若满足计数标准，则将计数加1
     */
    public static boolean beginCount(URL url, String methodName, int max) {
        max = (max <= 0) ? Integer.MAX_VALUE : max;
        RpcStatus appStatus = getStatus(url);
        RpcStatus methodStatus = getStatus(url, methodName);
        if (methodStatus.active.get() == Integer.MAX_VALUE) {
            return false;
        }
        for (int i; ; ) {
            i = methodStatus.active.get();
            if (i + 1 > max) {
                return false;
            }
            if (methodStatus.active.compareAndSet(i, i + 1)) { //循环比较值，若当前的值与期望的值相同，则做更新并且跳出循环
                break;
            }
        }
        appStatus.active.incrementAndGet(); //调用时加1，调用后会减1
        return true;
    }

    /**
     * 结束调用时，对调用计数以及耗时做处理
     *
     * @param url
     * @param elapsed
     * @param succeeded
     */
    public static void endCount(URL url, String methodName, long elapsed, boolean succeeded) {
        endCount(getStatus(url), elapsed, succeeded);
        endCount(getStatus(url, methodName), elapsed, succeeded);
    }

    /**
     * 结束统计是怎样的逻辑？
     * 解：激活数active减一，总数加一
     * totalElapsed与elapsed相加
     */
    private static void endCount(RpcStatus status, long elapsed, boolean succeeded) {
        status.active.decrementAndGet(); //调用结束时，正常调用的次数active - 1
        status.total.incrementAndGet();
        status.totalElapsed.addAndGet(elapsed);
        if (status.maxElapsed.get() < elapsed) {
            status.maxElapsed.set(elapsed);
        }
        if (succeeded) { //成功时，记录成功数
            if (status.succeededMaxElapsed.get() < elapsed) {
                status.succeededMaxElapsed.set(elapsed);
            }
        } else {  //失败时，记录失败数
            status.failed.incrementAndGet();
            status.failedElapsed.addAndGet(elapsed);
            if (status.failedMaxElapsed.get() < elapsed) {
                status.failedMaxElapsed.set(elapsed);
            }
        }
    }

    /**
     * set value.
     *
     * @param key
     * @param value
     */
    public void set(String key, Object value) {
        values.put(key, value);
    }

    /**
     * get value.
     *
     * @param key
     * @return value
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * get active.
     *
     * @return active
     */
    public int getActive() {
        return active.get();
    }

    /**
     * get total.
     *
     * @return total
     */
    public long getTotal() {
        return total.longValue();
    }

    /**
     * get total elapsed.
     *
     * @return total elapsed
     */
    public long getTotalElapsed() {
        return totalElapsed.get();
    }

    /**
     * get average elapsed.
     *
     * @return average elapsed
     */
    public long getAverageElapsed() {
        long total = getTotal();
        if (total == 0) {
            return 0;
        }
        return getTotalElapsed() / total;
    }

    /**
     * get max elapsed.
     *
     * @return max elapsed
     */
    public long getMaxElapsed() {
        return maxElapsed.get();
    }

    /**
     * get failed.
     *
     * @return failed
     */
    public int getFailed() {
        return failed.get();
    }

    /**
     * get failed elapsed.
     *
     * @return failed elapsed
     */
    public long getFailedElapsed() {
        return failedElapsed.get();
    }

    /**
     * get failed average elapsed.
     *
     * @return failed average elapsed
     */
    public long getFailedAverageElapsed() {
        long failed = getFailed();
        if (failed == 0) {
            return 0;
        }
        return getFailedElapsed() / failed;
    }

    /**
     * get failed max elapsed.
     *
     * @return failed max elapsed
     */
    public long getFailedMaxElapsed() {
        return failedMaxElapsed.get();
    }

    /**
     * get succeeded.
     *
     * @return succeeded
     */
    public long getSucceeded() {
        return getTotal() - getFailed();
    }

    /**
     * get succeeded elapsed.
     *
     * @return succeeded elapsed
     */
    public long getSucceededElapsed() {
        return getTotalElapsed() - getFailedElapsed();
    }

    /**
     * get succeeded average elapsed.
     *
     * @return succeeded average elapsed
     */
    public long getSucceededAverageElapsed() {
        long succeeded = getSucceeded();
        if (succeeded == 0) {
            return 0;
        }
        return getSucceededElapsed() / succeeded;
    }

    /**
     * get succeeded max elapsed.
     *
     * @return succeeded max elapsed.
     */
    public long getSucceededMaxElapsed() {
        return succeededMaxElapsed.get();
    }

    /**
     * Calculate average TPS (Transaction per second).
     *
     * @return tps
     */
    public long getAverageTps() {
        if (getTotalElapsed() >= 1000L) {
            return getTotal() / (getTotalElapsed() / 1000L);
        }
        return getTotal();
    }


}
