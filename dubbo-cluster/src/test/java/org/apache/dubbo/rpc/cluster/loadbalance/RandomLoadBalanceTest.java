/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License")); you may not use this file except in compliance with
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

import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcStatus;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RandomLoadBalance Test
 */
public class RandomLoadBalanceTest extends LoadBalanceBaseTest {
    @Test
    public void testRandomLoadBalanceSelect() { //已测（测试随机算法，未按权重，按invoker列表长度随机）
        int runs = 1000;
        Map<Invoker, AtomicLong> counter = getInvokeCounter(runs, RandomLoadBalance.NAME); //使用加权随机算法（获取各个invoker在1000次负载均衡中选中的次数）
        for (Map.Entry<Invoker, AtomicLong> entry : counter.entrySet()) {
            Long count = entry.getValue().get(); //总次数1000，invoker个数为5，平均值为200，应该选中数在200左右，绝对值不应该超过200，因为超过200了，相当于少了一个invoker
            Assertions.assertTrue(Math.abs(count - runs / (0f + invokers.size())) < runs / (0f + invokers.size()), "abs diff should < avg");
        } //随机算法选中情况（每次不一样，只是大致范围）：218、183、204、205、190

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j <= i; j++) {
                RpcStatus.beginCount(invokers.get(i).getUrl(), invocation.getMethodName()); //todo @csy 此处的作用是？
            }
        }
        counter = getInvokeCounter(runs, LeastActiveLoadBalance.NAME); //使用最少活跃数算法（运算结果一样，invoker1的选中次数都为1000，其它invoker都没选中）
        for (Map.Entry<Invoker, AtomicLong> entry : counter.entrySet()) {
            Long count = entry.getValue().get();
        }
        Assertions.assertEquals(runs, counter.get(invoker1).intValue());
        Assertions.assertEquals(0, counter.get(invoker2).intValue());
        Assertions.assertEquals(0, counter.get(invoker3).intValue());
        Assertions.assertEquals(0, counter.get(invoker4).intValue());
        Assertions.assertEquals(0, counter.get(invoker5).intValue());
    }

    @Test
    public void testSelectByWeight() { //已测（测试随机算法，按权重随机）
        int sumInvoker1 = 0;
        int sumInvoker2 = 0;
        int sumInvoker3 = 0;
        int loop = 10000;

        RandomLoadBalance lb = new RandomLoadBalance();
        for (int i = 0; i < loop; i++) {
            Invoker selected = lb.select(weightInvokers, null, weightTestInvocation);

            if (selected.getUrl().getProtocol().equals("test1")) {
                sumInvoker1++;
            }

            if (selected.getUrl().getProtocol().equals("test2")) {
                sumInvoker2++;
            }

            if (selected.getUrl().getProtocol().equals("test3")) {
                sumInvoker3++;
            }
        }

        // 1 : 9 : 6
        System.out.println(sumInvoker1);
        System.out.println(sumInvoker2);
        System.out.println(sumInvoker3);
        Assertions.assertEquals(sumInvoker1 + sumInvoker2 + sumInvoker3, loop, "select failed!"); //各个节点选择到的次数之和为总循环数

        /**
         * 输出结果：
         *
         * 结果1：
         * 656
         * 5654
         * 3690
         *
         * 结果2：
         * 632
         * 5574
         * 3794
         *
         * 结果3：
         * 620
         * 5644
         * 3736
         *
         * 结果分析:
         * 1）三个节点的权重依次为1、9、6，所以概率依次为 1/16，9/16，6/16，所以命中的次数大概为
         *   (1/16)*1000=625，(9/16)*10000=5625，(6/16)*10000=3750
         */
    }

}
