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
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.monitor.Monitor;
import org.apache.dubbo.monitor.MonitorFactory;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;
import org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_PROTOCOL;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * DubboMonitorTest
 */
public class DubboMonitorTest {

    private final Invoker<MonitorService> monitorInvoker = new Invoker<MonitorService>() {
        @Override
        public Class<MonitorService> getInterface() {
            return MonitorService.class;
        }

        public URL getUrl() {
            return URL.valueOf("dubbo://127.0.0.1:7070?interval=1000");
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            return null;
        }

        @Override
        public void destroy() {
        }
    };
    private volatile URL lastStatistics;
    private final MonitorService monitorService = new MonitorService() {

        public void collect(URL statistics) {
            DubboMonitorTest.this.lastStatistics = statistics;
        }

        public List<URL> lookup(URL query) {
            return Arrays.asList(DubboMonitorTest.this.lastStatistics);
        }

    };

    @Test
    public void testCount() throws Exception { //测试次数统计
        DubboMonitor monitor = new DubboMonitor(monitorInvoker, monitorService);
        URL statistics = new URLBuilder(DUBBO_PROTOCOL, "10.20.153.10", 0)
                .addParameter(MonitorService.APPLICATION, "morgan")
                .addParameter(MonitorService.INTERFACE, "MemberService")
                .addParameter(MonitorService.METHOD, "findPerson")
                .addParameter(MonitorService.CONSUMER, "10.20.153.11")
                .addParameter(MonitorService.SUCCESS, 1)
                .addParameter(MonitorService.FAILURE, 0)
                .addParameter(MonitorService.ELAPSED, 3)
                .addParameter(MonitorService.MAX_ELAPSED, 3)
                .addParameter(MonitorService.CONCURRENT, 1)
                .addParameter(MonitorService.MAX_CONCURRENT, 1)
                .build(); //构建URL
        monitor.collect(statistics);
        monitor.send();
        while (lastStatistics == null) {
            Thread.sleep(10);
        }
        Assertions.assertEquals("morgan", lastStatistics.getParameter(MonitorService.APPLICATION));
        Assertions.assertEquals("dubbo", lastStatistics.getProtocol());
        Assertions.assertEquals("10.20.153.10", lastStatistics.getHost());
        Assertions.assertEquals("morgan", lastStatistics.getParameter(MonitorService.APPLICATION));
        Assertions.assertEquals("MemberService", lastStatistics.getParameter(MonitorService.INTERFACE));
        Assertions.assertEquals("findPerson", lastStatistics.getParameter(MonitorService.METHOD));
        Assertions.assertEquals("10.20.153.11", lastStatistics.getParameter(MonitorService.CONSUMER));
        Assertions.assertEquals("1", lastStatistics.getParameter(MonitorService.SUCCESS));
        Assertions.assertEquals("0", lastStatistics.getParameter(MonitorService.FAILURE));
        Assertions.assertEquals("3", lastStatistics.getParameter(MonitorService.ELAPSED));
        Assertions.assertEquals("3", lastStatistics.getParameter(MonitorService.MAX_ELAPSED));
        Assertions.assertEquals("1", lastStatistics.getParameter(MonitorService.CONCURRENT));
        Assertions.assertEquals("1", lastStatistics.getParameter(MonitorService.MAX_CONCURRENT));
        monitor.destroy();
    }

    @Test
    public void testMonitorFactory() throws Exception { //测试监控中心工厂创建监控中心
        MockMonitorService monitorService = new MockMonitorService();
        URL statistics = new URLBuilder(DUBBO_PROTOCOL, "10.20.153.10", 0)
                .addParameter(MonitorService.APPLICATION, "morgan")
                .addParameter(MonitorService.INTERFACE, "MemberService")
                .addParameter(MonitorService.METHOD, "findPerson")
                .addParameter(MonitorService.CONSUMER, "10.20.153.11")
                .addParameter(MonitorService.SUCCESS, 1)
                .addParameter(MonitorService.FAILURE, 0)
                .addParameter(MonitorService.ELAPSED, 3)
                .addParameter(MonitorService.MAX_ELAPSED, 3)
                .addParameter(MonitorService.CONCURRENT, 1)
                .addParameter(MonitorService.MAX_CONCURRENT, 1)
                .build();

        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension(); //获取Protocol的自适应类（在调用自适应@Adaptive方法时，才会选择具体的实例）
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        MonitorFactory monitorFactory = ExtensionLoader.getExtensionLoader(MonitorFactory.class).getAdaptiveExtension();

        /**
         * 自适应类的实例选择：
         * 1）proxyFactory.getInvoker()中，因为url参数没有设置proxy参数，所以就会以@SPI参数设置的值，作为默认扩展名，即为JavassistProxyFactory
         * 2）protocol.export()中，因为invoker.getUrl()的url为dubbo://xxx，实例为DubboProtocol
         * 3）monitorFactory.getMonitor()，因为url的protocol的参数为dubbo，所以MonitorFactory的实例为DubboMonitorFactory
         */
        Exporter<MonitorService> exporter = protocol.export(proxyFactory.getInvoker(monitorService, MonitorService.class, URL.valueOf("dubbo://127.0.0.1:17979/" + MonitorService.class.getName())));
        try {
            Monitor monitor = null;
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 60000) { //循环进行60s
                monitor = monitorFactory.getMonitor(URL.valueOf("dubbo://127.0.0.1:17979?interval=10")); //getMonitor() 是DubboMonitorFactory从AbstractMonitorFactory继承的，所以会先进入AbstractMonitorFactory的getMonitor方法
                if (monitor == null) { //若创建的监控中心为空，则进行尝试创建
                    continue;
                }
                try {
                    monitor.collect(statistics); //将获取到的监控对象，进行数据收集
                    int i = 0;
                    while (monitorService.getStatistics() == null && i < 200) { //循环获取统计的url，直到不为null
                        i++;
                        Thread.sleep(10);
                    }
                    URL result = monitorService.getStatistics(); //此处的MockMonitorService.getStatistics()值是从哪里来的？解：因为DubboMonitor都构造方法中会创建周期性任务sendFuture，并周期性执行send()方法，而该方法中monitorService.collect(url);会通过代理方式进入MockMonitorService的collect方法
                    Assertions.assertEquals(1, result.getParameter(MonitorService.SUCCESS, 0));
                    Assertions.assertEquals(3, result.getParameter(MonitorService.ELAPSED, 0));
                } finally {
                    monitor.destroy();
                }
                break;
            }
            Assertions.assertNotNull(monitor);
        } finally {
            exporter.unexport();
        }
    }

    @Test
    public void testAvailable() { //测试MonitorService的isAvailable方法
        Invoker invoker = mock(Invoker.class);
        MonitorService monitorService = mock(MonitorService.class);

        given(invoker.isAvailable()).willReturn(true); //设置Mock值
        given(invoker.getUrl()).willReturn(URL.valueOf("dubbo://127.0.0.1:7070?interval=20"));
        DubboMonitor dubboMonitor = new DubboMonitor(invoker, monitorService);

        assertThat(dubboMonitor.isAvailable(), is(true)); //DubboMonitor的isAvailable的方法，会调用其维护的invoker的isAvailable方法
        verify(invoker).isAvailable();
    }

    @Test
    public void testSum() { //计算多次collect采集，进行求和统计
        URL statistics = new URLBuilder(DUBBO_PROTOCOL, "10.20.153.11", 0)
                .addParameter(MonitorService.APPLICATION, "morgan")
                .addParameter(MonitorService.INTERFACE, "MemberService")
                .addParameter(MonitorService.METHOD, "findPerson")
                .addParameter(MonitorService.CONSUMER, "10.20.153.11")
                .addParameter(MonitorService.SUCCESS, 1)
                .addParameter(MonitorService.FAILURE, 0)
                .addParameter(MonitorService.ELAPSED, 3)
                .addParameter(MonitorService.MAX_ELAPSED, 3)
                .addParameter(MonitorService.CONCURRENT, 1)
                .addParameter(MonitorService.MAX_CONCURRENT, 1)
                .build();
        Invoker invoker = mock(Invoker.class);
        MonitorService monitorService = mock(MonitorService.class);

        given(invoker.getUrl()).willReturn(URL.valueOf("dubbo://127.0.0.1:7070?interval=20"));
        DubboMonitor dubboMonitor = new DubboMonitor(invoker, monitorService);

        dubboMonitor.collect(statistics);
        dubboMonitor.collect(statistics.addParameter(MonitorService.SUCCESS, 3).addParameter(MonitorService.CONCURRENT, 2)
                .addParameter(MonitorService.INPUT, 1).addParameter(MonitorService.OUTPUT, 2)); //同一个统计url，多次调用collect时，会进行累加操作
        dubboMonitor.collect(statistics.addParameter(MonitorService.SUCCESS, 6).addParameter(MonitorService.ELAPSED, 2));

        // 将监控中心中统计的缓存值，发送给具体的监控服务monitorService做处理
        dubboMonitor.send(); //注意：因为DubboMonitor构建时，会创建一个周期性任务，周期的执行send()方法，所以在debug暂停时，可能已经被异步线程执行了send方法，而debug进入时，已经被reset清零了

        ArgumentCaptor<URL> summaryCaptor = ArgumentCaptor.forClass(URL.class);
        verify(monitorService, atLeastOnce()).collect(summaryCaptor.capture());

        List<URL> allValues = summaryCaptor.getAllValues();

        assertThat(allValues, not(nullValue()));
        assertThat(allValues, hasItem(new CustomMatcher<URL>("Monitor count should greater than 1") {
            @Override
            public boolean matches(Object item) {
                URL url = (URL) item;
                return Integer.valueOf(url.getParameter(MonitorService.SUCCESS)) > 1;
            }
        }));
    }
    @Test
    public void testMonitorSend() { //csy自增得测试：目的DubboMonitor的send方法中monitorService实例为DubboMonitor时的场景
        URL statistics = new URLBuilder(DUBBO_PROTOCOL, "10.20.153.11", 0)
                .addParameter(MonitorService.APPLICATION, "morgan")
                .addParameter(MonitorService.INTERFACE, "MemberService")
                .addParameter(MonitorService.METHOD, "findPerson")
                .addParameter(MonitorService.CONSUMER, "10.20.153.11")
                .addParameter(MonitorService.SUCCESS, 1)
                .addParameter(MonitorService.FAILURE, 0)
                .addParameter(MonitorService.ELAPSED, 3)
                .addParameter(MonitorService.MAX_ELAPSED, 3)
                .addParameter(MonitorService.CONCURRENT, 1)
                .addParameter(MonitorService.MAX_CONCURRENT, 1)
                .build();
        Invoker invoker = mock(Invoker.class);
        given(invoker.getUrl()).willReturn(URL.valueOf("dubbo://127.0.0.1:7070?interval=20"));

        ProxyFactory proxyFactory = new JavassistProxyFactory();
        Invoker<MonitorService> monitorInvoker = new DubboProtocol().refer(MonitorService.class, statistics); //目前此处会报错，因为找不到对应的服务，报“java.net.BindException: Cannot assign requested address: connect”
        MonitorService monitorService = proxyFactory.getProxy(monitorInvoker); //获取MonitorService的代理类

        DubboMonitor dubboMonitor = new DubboMonitor(invoker, monitorService);
        dubboMonitor.collect(statistics);
        List<URL> urls = dubboMonitor.lookup(statistics);
        System.out.println("send发送前:" + urls);

        dubboMonitor.send();

        List<URL> urls2 = dubboMonitor.lookup(statistics);
        System.out.println("send发送后:" + urls2);

    }

    @Test
    public void testLookUp() { //测试查询统计数据
        Invoker invoker = mock(Invoker.class);
        MonitorService monitorService = mock(MonitorService.class);

        URL queryUrl = URL.valueOf("dubbo://127.0.0.1:7070?interval=20");
        given(invoker.getUrl()).willReturn(queryUrl);
        DubboMonitor dubboMonitor = new DubboMonitor(invoker, monitorService);

        dubboMonitor.lookup(queryUrl);

        verify(monitorService).lookup(eq(queryUrl)); //此处的比较逻辑待了解

    }
}
