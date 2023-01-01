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
package org.apache.dubbo.monitor.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.monitor.Monitor;
import org.apache.dubbo.monitor.MonitorFactory;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MonitorFilterTest
 */
public class MonitorFilterTest {

    private volatile URL lastStatistics;

    private volatile Invocation lastInvocation;

    private final Invoker<MonitorService> serviceInvoker = new Invoker<MonitorService>() {
        @Override
        public Class<MonitorService> getInterface() { //指明调用的接口
            return MonitorService.class;
        }

        public URL getUrl() {
            try {
                return URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880?" + APPLICATION_KEY + "=abc&" + SIDE_KEY + "=" + CONSUMER_SIDE + "&" + MONITOR_KEY + "=" + URLEncoder.encode("dubbo://" + NetUtils.getLocalHost() + ":7070", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            lastInvocation = invocation;
            return AsyncRpcResult.newDefaultAsyncResult(invocation);
        }

        @Override
        public void destroy() {
        }
    };

    private MonitorFactory monitorFactory = new MonitorFactory() {
        @Override
        public Monitor getMonitor(final URL url) {
            return new Monitor() {
                public URL getUrl() {
                    return url;
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public void destroy() {
                }

                public void collect(URL statistics) {
                    MonitorFilterTest.this.lastStatistics = statistics;
                }

                public List<URL> lookup(URL query) {
                    return Arrays.asList(MonitorFilterTest.this.lastStatistics); //返回一个测试url（测试时使用）
                }
            };
        }
    };

    @Test
    public void testFilter() throws Exception { //测试MonitorFilter使用
        MonitorFilter monitorFilter = new MonitorFilter();
        monitorFilter.setMonitorFactory(monitorFactory);
        Invocation invocation = new RpcInvocation("aaa", MonitorService.class.getName(), new Class<?>[0], new Object[0]);
        RpcContext.getContext().setRemoteAddress(NetUtils.getLocalHost(), 20880).setLocalAddress(NetUtils.getLocalHost(), 2345);
        Result result = monitorFilter.invoke(serviceInvoker, invocation);
        result.whenCompleteWithContext((r, t) -> { //在完成调用时，主动进行方法回调
            if (t == null) {
                monitorFilter.onResponse(r, serviceInvoker, invocation);
            } else {
                monitorFilter.onError(t, serviceInvoker, invocation);
            }
        });
        while (lastStatistics == null) { //lastStatistics是在哪里设置的值？ 解答：调用monitorFilter.onResponse时，会调用MonitorFilter#collect方法，最终会调用当前匿名类MonitorFactory中的collect方法进行url赋值
            Thread.sleep(10); //lastStatistics 此处可能为空，是因为调用可能还没有完成，还没有回调onResponse()、onError()
        }
        Assertions.assertEquals("abc", lastStatistics.getParameter(MonitorService.APPLICATION)); //URL信息是通过serviceInvoker的url信息构建的
        Assertions.assertEquals(MonitorService.class.getName(), lastStatistics.getParameter(MonitorService.INTERFACE));
        Assertions.assertEquals("aaa", lastStatistics.getParameter(MonitorService.METHOD));
        Assertions.assertEquals(NetUtils.getLocalHost() + ":20880", lastStatistics.getParameter(MonitorService.PROVIDER));
        Assertions.assertEquals(NetUtils.getLocalHost(), lastStatistics.getAddress());
        Assertions.assertNull(lastStatistics.getParameter(MonitorService.CONSUMER));
        Assertions.assertEquals(1, lastStatistics.getParameter(MonitorService.SUCCESS, 0)); //此处成功的次数为啥为1？解：来自于MonitorFilter#createStatisticsUrl(invoker, invocation, result, remoteHost, start, error);
        Assertions.assertEquals(0, lastStatistics.getParameter(MonitorService.FAILURE, 0));
        Assertions.assertEquals(1, lastStatistics.getParameter(MonitorService.CONCURRENT, 0));
        Assertions.assertEquals(invocation, lastInvocation);
    }

    @Test
    public void testSkipMonitorIfNotHasKey() { //测试url没有monitor参数时，跳过监控统计
        MonitorFilter monitorFilter = new MonitorFilter();
        MonitorFactory mockMonitorFactory = mock(MonitorFactory.class); //创建MonitorFactory的Mock对象
        monitorFilter.setMonitorFactory(mockMonitorFactory);
        Invocation invocation = new RpcInvocation("aaa", MonitorService.class.getName(), new Class<?>[0], new Object[0]);
        Invoker invoker = mock(Invoker.class);
        given(invoker.getUrl()).willReturn(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880?" + APPLICATION_KEY + "=abc&" + SIDE_KEY + "=" + CONSUMER_SIDE)); //为Mock对象Invoker的getUrl方法设置返回值

        monitorFilter.invoke(invoker, invocation); //url中没有monitor这个参数，MonitorFilter#concurrents的值

        verify(mockMonitorFactory, never()).getMonitor(any(URL.class));
    }

    @Test
    public void testGenericFilter() throws Exception { //测试泛化调用时的监控统计（与普通调用的统计一样）
        MonitorFilter monitorFilter = new MonitorFilter();
        monitorFilter.setMonitorFactory(monitorFactory);
        Invocation invocation = new RpcInvocation("$invoke", MonitorService.class.getName(), new Class<?>[]{String.class, String[].class, Object[].class}, new Object[]{"xxx", new String[]{}, new Object[]{}});
        RpcContext.getContext().setRemoteAddress(NetUtils.getLocalHost(), 20880).setLocalAddress(NetUtils.getLocalHost(), 2345);
        Result result = monitorFilter.invoke(serviceInvoker, invocation); //此处的invoke调用，最终会调用当前内部类serviceInvoker的invoke方法
        result.whenCompleteWithContext((r, t) -> {
            if (t == null) {
                monitorFilter.onResponse(r, serviceInvoker, invocation);
            } else {
                monitorFilter.onError(t, serviceInvoker, invocation);
            }
        });
        while (lastStatistics == null) {
            Thread.sleep(10);
        }
        Assertions.assertEquals("abc", lastStatistics.getParameter(MonitorService.APPLICATION)); //lastStatistics值与testFilter类似
        Assertions.assertEquals(MonitorService.class.getName(), lastStatistics.getParameter(MonitorService.INTERFACE));
        Assertions.assertEquals("xxx", lastStatistics.getParameter(MonitorService.METHOD));
        Assertions.assertEquals(NetUtils.getLocalHost() + ":20880", lastStatistics.getParameter(MonitorService.PROVIDER));
        Assertions.assertEquals(NetUtils.getLocalHost(), lastStatistics.getAddress());
        Assertions.assertNull(lastStatistics.getParameter(MonitorService.CONSUMER));
        Assertions.assertEquals(1, lastStatistics.getParameter(MonitorService.SUCCESS, 0));
        Assertions.assertEquals(0, lastStatistics.getParameter(MonitorService.FAILURE, 0));
        Assertions.assertEquals(1, lastStatistics.getParameter(MonitorService.CONCURRENT, 0));
        Assertions.assertEquals(invocation, lastInvocation);
    }

    @Test
    public void testSafeFailForMonitorCollectFail() { //测试Monitor的collect进行采集时，发生异常处理（会捕获异常，仅做提示，不终止流程）
        MonitorFilter monitorFilter = new MonitorFilter();
        MonitorFactory mockMonitorFactory = mock(MonitorFactory.class);
        Monitor mockMonitor = mock(Monitor.class);
        Mockito.doThrow(new RuntimeException("test collect")).when(mockMonitor).collect(any(URL.class)); //在调用Mock对象collect方法时，抛出异常（当前只是声明方法，只有在调用时，mock逻辑才会生效）

        monitorFilter.setMonitorFactory(mockMonitorFactory);
        given(mockMonitorFactory.getMonitor(any(URL.class))).willReturn(mockMonitor); //调用MonitorFactory的getMonitor方法时，会返回Mock的Monitor对象
        Invocation invocation = new RpcInvocation("aaa", MonitorService.class.getName(), new Class<?>[0], new Object[0]);

        Result result = monitorFilter.invoke(serviceInvoker, invocation); //此处会在哪里调用Monitor的collect方法？解答：collect方法在MonitorFilter的onResponse和onError方法中，而这些方法要主动设置回调才会调用的
        // csy 新加的测试逻辑
        result.whenCompleteWithContext((r, t) -> { //在完成调用时，主动进行方法回调
            if (t == null) {
                monitorFilter.onResponse(r, serviceInvoker, invocation);
            } else {
                monitorFilter.onError(t, serviceInvoker, invocation);
            }
        });
    }
}
