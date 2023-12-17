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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin load balance.
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "roundrobin";

    /**
     * 背景介绍：
     * 1）所谓轮询是指将请求轮流分配给每台服务器。比如：我们有三台服务器 A、B、C。我们将第一个请求分配给服务器 A，第二个请求分配给服务器 B，第三个请求分配给服务器 C，
     *   第四个请求再次分配给服务器 A。即每次调度执行i = (i + 1) mod n，并选出第i台服务器。这个过程就叫做轮询。轮询是一种无状态负载均衡算法，实现简单，适用于每台服务器性能相近的场景下。
     *
     * 2）现实情况下，我们并不能保证每台服务器性能均相近。如果我们将等量的请求分配给性能较差的服务器，这显然是不合理的。
     *   因此，这个时候我们需要对轮询过程进行加权，以调控每台服务器的负载。
     *   （经过加权后，每台服务器能够得到的请求数比例，接近或等于它们的权重比）
     *
     * 3）平滑加权轮询算法：https://juejin.cn/post/7099424131216572423
     */

    private static final int RECYCLE_PERIOD = 60000; //过期缓存回收的时间

    protected static class WeightedRoundRobin { //加权轮询处理器
        private int weight; //用户设置的服务提供者的权重
        private AtomicLong current = new AtomicLong(0); //用于计算的当前权重
        private long lastUpdate; //最后一次更新时间（用于缓存清除）

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }

        public long increaseCurrent() { //增加当前权重
            return current.addAndGet(weight); //用当前权重current加上设置的权重weight
        }

        public void sel(int total) {
            current.addAndGet(-1 * total);
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

    //methodWeightMap的数据格式：ConcurrentMap<serviceKey+"."+methodName, ConcurrentMap<identifyString, WeightedRoundRobin>>
    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>(); //服务调用方法与加权轮询处理器的缓存

    /**
     * get invoker addr list cached for specified invocation
     * <p>
     * <b>for unit test only</b>
     *
     * @param invokers
     * @param invocation
     * @return
     */
    protected <T> Collection<String> getInvokerAddrList(List<Invoker<T>> invokers, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        Map<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map != null) {
            return map.keySet();
        }
        return null;
    }

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName(); //获取调用方法的key
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>()); //每个SPI接口会缓存对应的一个实例。所以若负载均衡选择了加权轮询，那么多次请求都得到同一个RoundRobinLoadBalance实例
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        Invoker<T> selectedInvoker = null;
        WeightedRoundRobin selectedWRR = null;
        for (Invoker<T> invoker : invokers) {
            String identifyString = invoker.getUrl().toIdentityString();
            int weight = getWeight(invoker, invocation);
            WeightedRoundRobin weightedRoundRobin = map.computeIfAbsent(identifyString, k -> { //第一次负载均衡时，会建立好缓存，后面的请求都是基于缓存中数据来计算
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(weight);
                return wrr;
            });

            if (weight != weightedRoundRobin.getWeight()) {
                //weight changed（invoker的权重发生改变，需要更新）
                weightedRoundRobin.setWeight(weight);
            }
            long cur = weightedRoundRobin.increaseCurrent(); //计算新的当前权重current的值（每次调用选择Invoker前，都会先计算）
            weightedRoundRobin.setLastUpdate(now); //记录更新时间
            if (cur > maxCurrent) { //若找到新的最大current值，则记录invoker以及加权轮询处理器
                maxCurrent = cur;
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            totalWeight += weight; //累加invoker的权重，计算总权重
        }
        if (invokers.size() != map.size()) { //若实际的invoker数与缓存数不等时，即存在过期无效的缓存，则根据回收时间计算并对应删除
            map.entrySet().removeIf(item -> now - item.getValue().getLastUpdate() > RECYCLE_PERIOD);
        }
        if (selectedInvoker != null) {
            selectedWRR.sel(totalWeight); //将选择的Invoker的当前权重currentWeight减去总的权重，参与下一次负载均衡选择
            return selectedInvoker; //返回选择到的Invoker
        }
        // should not happen here
        return invokers.get(0);
    }

}
