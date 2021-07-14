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
package org.apache.dubbo.rpc.filter.tps;

import java.util.concurrent.atomic.LongAdder;

/**
 * Judge whether a particular（特别的） invocation of service provider method should be allowed within a configured time interval.
 * （判断是否应该在配置的时间间隔内允许对服务提供者方法的特定调用）
 * As a state it contain name of key ( e.g. method), last invocation time, interval and rate count.
 */
class StatItem { //@csy-P3 该类的功能、用途是什么？解：使用令牌桶进行限流  https://blog.csdn.net/cbhyk/article/details/86064725

    private String name;

    private long lastResetTime;

    private long interval; //interval:间隔

    private LongAdder token;

    private int rate;

    StatItem(String name, int rate, long interval) {
        this.name = name;
        this.rate = rate;
        this.interval = interval;
        this.lastResetTime = System.currentTimeMillis();
        this.token = buildLongAdder(rate);
    }

    public boolean isAllowable() { //判断是否还有剩余令牌，并把令牌数减1
        long now = System.currentTimeMillis();
        if (now > lastResetTime + interval) { //没有超过指定时间间隔
            token = buildLongAdder(rate);
            lastResetTime = now;
        }

        if (token.sum() < 0) {
            return false;
        }
        token.decrement(); //todo @csy-021-P3 了解漏桶算法

        /**
         * 2.5.6的处理方式
         * int value = token.get();
         * boolean flag = false;
         * while (value > 0 && !flag) {
         *    flag = token.compareAndSet(value, value - 1);
         *    value = token.get();
         * }
         * return flag;
         */
        return true;
    }

    public long getInterval() {
        return interval;
    }


    public int getRate() {
        return rate;
    }


    long getLastResetTime() {
        return lastResetTime;
    }

    long getToken() {
        return token.sum();
    }

    @Override
    public String toString() {
        return new StringBuilder(32).append("StatItem ")
                .append("[name=").append(name).append(", ")
                .append("rate = ").append(rate).append(", ")
                .append("interval = ").append(interval).append("]")
                .toString();
    }

    private LongAdder buildLongAdder(int rate) {
        LongAdder adder = new LongAdder();
        adder.add(rate);
        return adder;
    }

}
