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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

public class ProtocolConfigTest {

    @Test
    public void testName() throws Exception { //已测（设置协议名）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("name");
        Map<String, String> parameters = new HashMap<String, String>();
        ProtocolConfig.appendParameters(parameters, protocol);
        assertThat(protocol.getName(), equalTo("name"));
        assertThat(protocol.getId(), equalTo("name")); //id未设置值时，与name同值
        assertThat(parameters.isEmpty(), is(true));
    }

    @Test
    public void testHost() throws Exception { //已测（设置协议的主机号，即ip地址，参数不对外暴露）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setHost("host");
        Map<String, String> parameters = new HashMap<String, String>();
        ProtocolConfig.appendParameters(parameters, protocol);
        assertThat(protocol.getHost(), equalTo("host"));
        assertThat(parameters.isEmpty(), is(true));
    }

    @Test
    public void testPort() throws Exception { //已测（设置协议的端口号，参数不对外暴露）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setPort(8080);
        Map<String, String> parameters = new HashMap<String, String>();
        ProtocolConfig.appendParameters(parameters, protocol);
        assertThat(protocol.getPort(), equalTo(8080));
        assertThat(parameters.isEmpty(), is(true));
    }

    @Test
    public void testPath() throws Exception { //已测（设置上下文路径）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setContextpath("context-path");
        Map<String, String> parameters = new HashMap<String, String>();
        ProtocolConfig.appendParameters(parameters, protocol);
        assertThat(protocol.getPath(), equalTo("context-path")); //getPath()取的值，是contextpath的值
        assertThat(protocol.getContextpath(), equalTo("context-path"));
        assertThat(parameters.isEmpty(), is(true));
        protocol.setPath("path");
        assertThat(protocol.getPath(), equalTo("path"));
        assertThat(protocol.getContextpath(), equalTo("path"));
    }

    @Test
    public void testCorethreads() throws Exception { //已测（设置线程池核心线程数）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setCorethreads(10);
        assertThat(protocol.getCorethreads(), is(10));
    }

    @Test
    public void testThreads() throws Exception { //已测（设置线程池大小）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setThreads(10);
        assertThat(protocol.getThreads(), is(10));
    }

    @Test
    public void testIothreads() throws Exception { //已测（设置io线程池大小）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setIothreads(10);
        assertThat(protocol.getIothreads(), is(10));
    }

    @Test
    public void testQueues() throws Exception { //已测（设置线程池队列大小）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setQueues(10);
        assertThat(protocol.getQueues(), is(10));
    }

    @Test
    public void testAccepts() throws Exception { //已测（服务提供者最大可接受连接数）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setAccepts(10);
        assertThat(protocol.getAccepts(), is(10));
    }

    @Test
    public void testCodec() throws Exception { //已测（设置协议编码方式）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("dubbo");
        protocol.setCodec("mockcodec");
        assertThat(protocol.getCodec(), equalTo("mockcodec"));
    }

    @Test
    public void testAccesslog() throws Exception { //已测（设置输出访问日志）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setAccesslog("access.log");
        assertThat(protocol.getAccesslog(), equalTo("access.log"));
    }

    @Test
    public void testTelnet() throws Exception { //已测（设置telnet命令）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setTelnet("mocktelnethandler");
        assertThat(protocol.getTelnet(), equalTo("mocktelnethandler"));
    }

    @Test
    public void testRegister() throws Exception { //已测（设置是否注册）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setRegister(true);
        assertThat(protocol.isRegister(), is(true));
    }

    @Test
    public void testTransporter() throws Exception { //已测（设置传输协议）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setTransporter("mocktransporter");
        assertThat(protocol.getTransporter(), equalTo("mocktransporter"));
    }

    @Test
    public void testExchanger() throws Exception { //已测（设置交换信息）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setExchanger("mockexchanger");
        assertThat(protocol.getExchanger(), equalTo("mockexchanger"));
    }

    @Test
    public void testDispatcher() throws Exception { //已测（设置线程调度模式）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setDispatcher("mockdispatcher");
        assertThat(protocol.getDispatcher(), equalTo("mockdispatcher"));
    }

    @Test
    public void testNetworker() throws Exception { //已测（设置网络使用者）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setNetworker("networker");
        assertThat(protocol.getNetworker(), equalTo("networker"));
    }

    @Test
    public void testParameters() throws Exception { //已测（设置自定义参数）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setParameters(Collections.singletonMap("k1", "v1"));
        assertThat(protocol.getParameters(), hasEntry("k1", "v1"));
    }

    @Test
    public void testDefault() throws Exception { //已测（设置为默认配置）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setDefault(true);
        assertThat(protocol.isDefault(), is(true));
    }

    @Test
    public void testKeepAlive() throws Exception { //已测（是否保持连接）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setKeepAlive(true);
        assertThat(protocol.getKeepAlive(), is(true));
    }

    @Test
    public void testOptimizer() throws Exception { //已测（参数含义待确认）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setOptimizer("optimizer");
        assertThat(protocol.getOptimizer(), equalTo("optimizer"));
    }

    @Test
    public void testExtension() throws Exception { //已测（参数含义待确认）
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setExtension("extension");
        assertThat(protocol.getExtension(), equalTo("extension"));
    }
}
