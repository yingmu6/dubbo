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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This class select one provider from multiple providers randomly.
 * You can define weights for each provider:
 * If the weights are all the same then it will use random.nextInt(number of invokers).
 * If the weights are different then it will use random.nextInt(w1 + w2 + ... + wn)
 * Note that if the performance of the machine is better than others, you can set a larger weight.
 * If the performance is not so good, you can set a smaller weight.
 * （可根据机器的性能好、差，设置大的权重或小的权重）
 */
public class RandomLoadBalance extends AbstractLoadBalance { //加权随机算法

    public static final String NAME = "random";

     /**
     * Select one invoker between a list using a random criteria
     * @param invokers List of possible invokers
     * @param url URL
     * @param invocation Invocation
     * @param <T>
     * @return The selected invoker
     */
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // Every invoker has the same weight?
        boolean sameWeight = true;
        // the weight of every invokers
        int[] weights = new int[length]; //每个invoker对应的权重
        // the first invoker's weight
        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;
        // The sum of weights
        int totalWeight = firstWeight;
        for (int i = 1; i < length; i++) { //遍历invoker列表
            int weight = getWeight(invokers.get(i), invocation);
            // save for later use
            weights[i] = weight;
            // Sum
            totalWeight += weight; //计算总权重
            if (sameWeight && weight != firstWeight) { //将每个invoker的权重依次与第一个invoker的权重进行比较（只要有一个权重不相等，即为不相等）
                sameWeight = false;
            }
        }
        if (totalWeight > 0 && !sameWeight) { //可画出一维坐标轴，画出各个invoker对应的权重值，根据落入的区间选择invoker在数组中的下标
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offset = ThreadLocalRandom.current().nextInt(totalWeight); //基于总的权重计算随机值
            // Return a invoker based on the random value.
            for (int i = 0; i < length; i++) {
                offset -= weights[i];
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(ThreadLocalRandom.current().nextInt(length)); //若invoker的权重都相同，则不使用权重负载，直接根据invoker数量计算随机数
    }

}
