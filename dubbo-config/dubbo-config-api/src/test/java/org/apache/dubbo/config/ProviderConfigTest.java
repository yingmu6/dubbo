/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class ProviderConfigTest {
    @Test
    public void testProtocol() throws Exception { //已测（设置暴露的协议）
        ProviderConfig provider = new ProviderConfig();
        provider.setProtocol("protocol");
        assertThat(provider.getProtocol().getName(), equalTo("protocol"));
    }

    @Test
    public void testDefault() throws Exception { //已测（设置为默认配置）
        ProviderConfig provider = new ProviderConfig();
        provider.setDefault(true);
        Map<String, String> parameters = new HashMap<String, String>();
        ProviderConfig.appendParameters(parameters, provider);
        assertThat(provider.isDefault(), is(true));
        assertThat(parameters, not(hasKey("default")));
    }

    @Test
    public void testHost() throws Exception { //已测（设置主机号）
        ProviderConfig provider = new ProviderConfig();
        provider.setHost("demo-host");
        Map<String, String> parameters = new HashMap<String, String>();
        ProviderConfig.appendParameters(parameters, provider);
        assertThat(provider.getHost(), equalTo("demo-host"));
        assertThat(parameters, not(hasKey("host")));
    }

    @Test
    public void testPort() throws Exception { //已测（设置端口号）
        ProviderConfig provider = new ProviderConfig();
        provider.setPort(8080);
        Map<String, String> parameters = new HashMap<String, String>();
        ProviderConfig.appendParameters(parameters, provider);
        assertThat(provider.getPort(), is(8080));
        assertThat(parameters, not(hasKey("port")));
    }

    @Test
    public void testPath() throws Exception { //已测（设置上下文路径）
        ProviderConfig provider = new ProviderConfig();
        provider.setPath("/path");
        Map<String, String> parameters = new HashMap<String, String>();
        ProviderConfig.appendParameters(parameters, provider);
        assertThat(provider.getPath(), equalTo("/path"));
        assertThat(provider.getContextpath(), equalTo("/path"));
        assertThat(parameters, not(hasKey("path")));
    }

    @Test
    public void testContextPath() throws Exception { //已测（设置上下文路径）
        ProviderConfig provider = new ProviderConfig();
        provider.setContextpath("/context-path");
        Map<String, String> parameters = new HashMap<String, String>();
        ProviderConfig.appendParameters(parameters, provider);
        assertThat(provider.getContextpath(), equalTo("/context-path"));
        assertThat(parameters, not(hasKey("/context-path")));
    }

    @Test
    public void testThreadpool() throws Exception { //已测（设置线程池类型）
        ProviderConfig provider = new ProviderConfig();
        provider.setThreadpool("mockthreadpool");
        assertThat(provider.getThreadpool(), equalTo("mockthreadpool"));
    }

    @Test
    public void testThreads() throws Exception { //已测（设置线程池线程数）
        ProviderConfig provider = new ProviderConfig();
        provider.setThreads(10);
        assertThat(provider.getThreads(), is(10));
    }

    @Test
    public void testIothreads() throws Exception { //已测（设置io线程数）
        ProviderConfig provider = new ProviderConfig();
        provider.setIothreads(10);
        assertThat(provider.getIothreads(), is(10));
    }

    @Test
    public void testQueues() throws Exception { //已测（设置线程池队列数）
        ProviderConfig provider = new ProviderConfig();
        provider.setQueues(10);
        assertThat(provider.getQueues(), is(10));
    }

    @Test
    public void testAccepts() throws Exception { //已测（设置最大可连接数）
        ProviderConfig provider = new ProviderConfig();
        provider.setAccepts(10);
        assertThat(provider.getAccepts(), is(10));
    }

    @Test
    public void testCharset() throws Exception { //已测（设置序列化编码）
        ProviderConfig provider = new ProviderConfig();
        provider.setCharset("utf-8");
        assertThat(provider.getCharset(), equalTo("utf-8"));
    }

    @Test
    public void testPayload() throws Exception { //已测（设置最大负载）
        ProviderConfig provider = new ProviderConfig();
        provider.setPayload(10);
        assertThat(provider.getPayload(), is(10));
    }

    @Test
    public void testBuffer() throws Exception { //已测（设置网络缓冲区大小）
        ProviderConfig provider = new ProviderConfig();
        provider.setBuffer(10);
        assertThat(provider.getBuffer(), is(10));
    }

    @Test
    public void testServer() throws Exception { //已测（设置协议服务端的实现类型）
        ProviderConfig provider = new ProviderConfig();
        provider.setServer("demo-server");
        assertThat(provider.getServer(), equalTo("demo-server"));
    }

    @Test
    public void testClient() throws Exception { //已测（设置协议客户端的实现类型）
        ProviderConfig provider = new ProviderConfig();
        provider.setClient("client");
        assertThat(provider.getClient(), equalTo("client"));
    }

    @Test
    public void testTelnet() throws Exception { //已测（设置telnet命令）
        ProviderConfig provider = new ProviderConfig();
        provider.setTelnet("mocktelnethandler");
        assertThat(provider.getTelnet(), equalTo("mocktelnethandler"));
    }

    @Test
    public void testPrompt() throws Exception { //已测（设置提示符）
        ProviderConfig provider = new ProviderConfig();
        provider.setPrompt("#");
        Map<String, String> parameters = new HashMap<String, String>();
        ProviderConfig.appendParameters(parameters, provider);
        assertThat(provider.getPrompt(), equalTo("#"));
        assertThat(parameters, hasEntry("prompt", "%23"));

        /**
         * 调试问题点：
         * 1）为什么parameters中会有"prompt", "%23"等参数？
         *    解答：hasEntry("prompt", "%23")并不是判断是否有这两个key，而是判断Map中是否有这个条目Entry
         *         因为prompt提示符@Parameter(escaped=true)，即需要编码的，所以将"#"编码为"%23"
         */
    }

    @Test
    public void testStatus() throws Exception { //已测（设置状态检查）
        ProviderConfig provider = new ProviderConfig();
        provider.setStatus("mockstatuschecker");
        assertThat(provider.getStatus(), equalTo("mockstatuschecker"));
    }

    @Test
    public void testTransporter() throws Exception { //已测（设置传输协议）
        ProviderConfig provider = new ProviderConfig();
        provider.setTransporter("mocktransporter");
        assertThat(provider.getTransporter(), equalTo("mocktransporter"));
    }

    @Test
    public void testExchanger() throws Exception { //已测（设置交换信息）
        ProviderConfig provider = new ProviderConfig();
        provider.setExchanger("mockexchanger");
        assertThat(provider.getExchanger(), equalTo("mockexchanger"));
    }

    @Test
    public void testDispatcher() throws Exception { //已测（设置线程调度方式）
        ProviderConfig provider = new ProviderConfig();
        provider.setDispatcher("mockdispatcher");
        assertThat(provider.getDispatcher(), equalTo("mockdispatcher"));
    }

    @Test
    public void testNetworker() throws Exception { //已测（设置网络使用者）
        ProviderConfig provider = new ProviderConfig();
        provider.setNetworker("networker");
        assertThat(provider.getNetworker(), equalTo("networker"));
    }

    @Test
    public void testWait() throws Exception { //已测（停服务等待时间）
        ProviderConfig provider = new ProviderConfig();
        provider.setWait(10);
        assertThat(provider.getWait(), equalTo(10));
    }
}
