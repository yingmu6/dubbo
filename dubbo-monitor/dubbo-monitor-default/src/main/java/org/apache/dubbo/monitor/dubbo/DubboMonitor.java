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
package org.apache.dubbo.monitor.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.monitor.Monitor;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.rpc.Invoker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_PROTOCOL;

/**
 * DubboMonitor
 */
public class DubboMonitor implements Monitor {

    private static final Logger logger = LoggerFactory.getLogger(DubboMonitor.class);

    /**
     * The length of the array which is a container of the statistics
     */
    private static final int LENGTH = 10;

    /**
     * The timer for sending statistics（发送统计信息的计时器）
     */
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3, new NamedThreadFactory("DubboMonitorSendTimer", true));

    /**
     * The future that can cancel the <b>scheduledExecutorService</b>
     */
    private final ScheduledFuture<?> sendFuture; //用于发送采集请求的Future

    private final Invoker<MonitorService> monitorInvoker; //监控服务对应的Invoker

    private final MonitorService monitorService; //监控服务

    private final ConcurrentMap<Statistics, AtomicReference<long[]>> statisticsMap = new ConcurrentHashMap<Statistics, AtomicReference<long[]>>(); //统计信息与具体统计项的映射（非static成员变量，属于各个对象私有，而非所有对象公有）

    public DubboMonitor(Invoker<MonitorService> monitorInvoker, MonitorService monitorService) {
        this.monitorInvoker = monitorInvoker;
        this.monitorService = monitorService;
        // The time interval for timer <b>scheduledExecutorService</b> to send data
        final long monitorInterval = monitorInvoker.getUrl().getPositiveParameter("interval", 60000);
        // collect timer for collecting statistics data
        sendFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // collect data
                send(); //定时
            } catch (Throwable t) {
                logger.error("Unexpected error occur at send statistic, cause: " + t.getMessage(), t);
            }
        }, monitorInterval, monitorInterval, TimeUnit.MILLISECONDS); //周期性执行任务
    }

    public void send() { //发送采集请求给监控服务（将监控中心DubboMonitor中统计的缓存信息发送给监控服务MonitorService做信息采集，采集好后将监控中心中对应缓存重置）
        if (logger.isDebugEnabled()) {
            logger.debug("Send statistics to monitor " + getUrl());
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        for (Map.Entry<Statistics, AtomicReference<long[]>> entry : statisticsMap.entrySet()) { //遍历缓存中统计值
            // get statistics data
            Statistics statistics = entry.getKey();
            AtomicReference<long[]> reference = entry.getValue();
            long[] numbers = reference.get();//提取各个元素的统计值
            long success = numbers[0];
            long failure = numbers[1];
            long input = numbers[2];
            long output = numbers[3];
            long elapsed = numbers[4];
            long concurrent = numbers[5];
            long maxInput = numbers[6];
            long maxOutput = numbers[7];
            long maxElapsed = numbers[8];
            long maxConcurrent = numbers[9];
            String protocol = getUrl().getParameter(DEFAULT_PROTOCOL);

            // send statistics data
            URL url = statistics.getUrl() //按键值对的形式设置到URL参数中
                    .addParameters(MonitorService.TIMESTAMP, timestamp,
                            MonitorService.SUCCESS, String.valueOf(success),
                            MonitorService.FAILURE, String.valueOf(failure),
                            MonitorService.INPUT, String.valueOf(input),
                            MonitorService.OUTPUT, String.valueOf(output),
                            MonitorService.ELAPSED, String.valueOf(elapsed),
                            MonitorService.CONCURRENT, String.valueOf(concurrent),
                            MonitorService.MAX_INPUT, String.valueOf(maxInput),
                            MonitorService.MAX_OUTPUT, String.valueOf(maxOutput),
                            MonitorService.MAX_ELAPSED, String.valueOf(maxElapsed),
                            MonitorService.MAX_CONCURRENT, String.valueOf(maxConcurrent),
                            DEFAULT_PROTOCOL, protocol
                    );
            monitorService.collect(url); //收集监控的数据（此处若monitorService的实例为DubboMonitor，理论上collect()会做累加，而后面的逻辑会递减，是没有影响的）

            // reset（交由监控服务采集以后，对当前监控中心中对应的缓存做重置）
            long[] current;
            long[] update = new long[LENGTH];
            do {
                current = reference.get();
                if (current == null) { //若缓存中没有值，直接将值置为0
                    update[0] = 0;
                    update[1] = 0;
                    update[2] = 0;
                    update[3] = 0;
                    update[4] = 0;
                    update[5] = 0;
                } else { // 若缓存的存在值，就将缓存中的值减去当前已经采集计算的值（只处理前5个元素）
                    update[0] = current[0] - success;
                    update[1] = current[1] - failure;
                    update[2] = current[2] - input;
                    update[3] = current[3] - output;
                    update[4] = current[4] - elapsed;
                    update[5] = current[5] - concurrent;
                }
            } while (!reference.compareAndSet(current, update));
        }
    }

    @Override
    public void collect(URL url) { //收集监控数据（最终将统计的值写到缓存statisticsMap中）
        // data to collect from url
        int success = url.getParameter(MonitorService.SUCCESS, 0);
        int failure = url.getParameter(MonitorService.FAILURE, 0);
        int input = url.getParameter(MonitorService.INPUT, 0);
        int output = url.getParameter(MonitorService.OUTPUT, 0);
        int elapsed = url.getParameter(MonitorService.ELAPSED, 0);
        int concurrent = url.getParameter(MonitorService.CONCURRENT, 0);
        // init atomic reference
        Statistics statistics = new Statistics(url);
        AtomicReference<long[]> reference = statisticsMap.computeIfAbsent(statistics, k -> new AtomicReference<>()); //若Map中不存在key，则创建对应的key/value，否则返回原有的值
        // use CompareAndSet to sum
        long[] current;
        long[] update = new long[LENGTH];
        do {
            current = reference.get(); //从内存中获取缓存的值
            if (current == null) { //缓存中不存在统计的值
                update[0] = success;
                update[1] = failure;
                update[2] = input;
                update[3] = output;
                update[4] = elapsed;
                update[5] = concurrent;
                update[6] = input;
                update[7] = output;
                update[8] = elapsed;
                update[9] = concurrent;
            } else {             //缓存中存在统计的值，则将url中的值与缓存中的值处理（不同的元素处理方式不同，分为3个区间，第1~5，第6个，第7~10）
                update[0] = current[0] + success; //第1~5个元素：在多次采集时的计算方式=将缓存中的值与输入的值累加
                update[1] = current[1] + failure;
                update[2] = current[2] + input; //前5个元素是累加操作，后5个元素更倾向于占位操作
                update[3] = current[3] + output;
                update[4] = current[4] + elapsed;
                update[5] = (current[5] + concurrent) / 2; //第6个元素_并发数：在多次采集时的计算方式=（当前缓存中的值+输入的值）/ 2
                update[6] = current[6] > input ? current[6] : input; //第7~10个元素：在多次采集时的计算方式=取缓存中值与输入值的最大值
                update[7] = current[7] > output ? current[7] : output;
                update[8] = current[8] > elapsed ? current[8] : elapsed;
                update[9] = current[9] > concurrent ? current[9] : concurrent;
            }
        } while (!reference.compareAndSet(current, update));//当内存中的实际值与expect预期值不相等时，返回false，否则在相等情况下，可以进行更新操作（判断从缓存中获取的值是否被其它线程更新过，若已被更新，则循环去取最新的内存的值，并做计算，直到成功为止）
    }

    @Override
    public List<URL> lookup(URL query) {
        return monitorService.lookup(query);
    }

    @Override
    public URL getUrl() {
        return monitorInvoker.getUrl();
    }

    @Override
    public boolean isAvailable() {
        return monitorInvoker.isAvailable();
    }

    @Override
    public void destroy() { //在节点销毁时，取消定时任务
        try {
            ExecutorUtil.cancelScheduledFuture(sendFuture);
        } catch (Throwable t) {
            logger.error("Unexpected error occur at cancel sender timer, cause: " + t.getMessage(), t);
        }
        monitorInvoker.destroy();
    }

}
