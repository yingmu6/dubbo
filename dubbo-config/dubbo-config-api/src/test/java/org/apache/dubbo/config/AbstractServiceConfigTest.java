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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.EXPORTER_LISTENER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SERVICE_FILTER_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class AbstractServiceConfigTest {
    @Test
    public void testVersion() throws Exception { //已测（设置服务的版本号）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setVersion("version");
        assertThat(serviceConfig.getVersion(), equalTo("version"));
    }

    @Test
    public void testGroup() throws Exception { //已测（设置服务的分组）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setGroup("group");
        assertThat(serviceConfig.getGroup(), equalTo("group"));
    }

    @Test
    public void testDelay() throws Exception { //已测（设置服务延迟暴露的时间）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setDelay(1000);
        assertThat(serviceConfig.getDelay(), equalTo(1000));
    }

    @Test
    public void testExport() throws Exception { //已测（设置是否暴露服务）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setExport(true);
        assertThat(serviceConfig.getExport(), is(true));
    }

    @Test
    public void testWeight() throws Exception { //已测（设置服务的权重）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setWeight(500);
        assertThat(serviceConfig.getWeight(), equalTo(500));
    }

    @Test
    public void testDocument() throws Exception { //已测（设置文档中心链接）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setDocument("http://dubbo.apache.org");
        assertThat(serviceConfig.getDocument(), equalTo("http://dubbo.apache.org"));
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractServiceConfig.appendParameters(parameters, serviceConfig);
        assertThat(parameters, hasEntry("document", "http%3A%2F%2Fdubbo.apache.org")); //因为getDocument()方法上的注解@Parameter(escape=true)，所以会进行URL编码
    }

    @Test
    public void testToken() throws Exception { //已测（设置服务的令牌）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setToken("token");
        assertThat(serviceConfig.getToken(), equalTo("token"));
        serviceConfig.setToken((Boolean) null);
        assertThat(serviceConfig.getToken(), nullValue());
        serviceConfig.setToken(true);
        assertThat(serviceConfig.getToken(), is("true")); //令牌值，可以为"true"
    }

    @Test
    public void testDeprecated() throws Exception { //已测（设置服务是否已经启用）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setDeprecated(true); //目前没看到使用该字段的地方
        assertThat(serviceConfig.isDeprecated(), is(true));
    }

    @Test
    public void testDynamic() throws Exception { //已测（设置服务是否动态注册）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setDynamic(true);
        assertThat(serviceConfig.isDynamic(), is(true));
    }

    @Test
    public void testProtocol() throws Exception { //已测（设置暴露服务的协议列表）
        ServiceConfig serviceConfig = new ServiceConfig();
        assertThat(serviceConfig.getProtocol(), nullValue());
        serviceConfig.setProtocol(new ProtocolConfig());
        assertThat(serviceConfig.getProtocol(), notNullValue());
        serviceConfig.setProtocols(new ArrayList<>(Collections.singletonList(new ProtocolConfig())));
        assertThat(serviceConfig.getProtocols(), hasSize(1));
    }

    @Test
    public void testAccesslog() throws Exception { //已测（设置将向logger中输出访问日志）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setAccesslog("access.log");
        assertThat(serviceConfig.getAccesslog(), equalTo("access.log")); //可以指定日志的路径
        serviceConfig.setAccesslog((Boolean) null);
        assertThat(serviceConfig.getAccesslog(), nullValue());
        serviceConfig.setAccesslog(true);
        assertThat(serviceConfig.getAccesslog(), equalTo("true")); //也可以设置"true"或"false"
    }

    @Test
    public void testExecutes() throws Exception { //已测（最大请求并行数）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setExecutes(10);
        assertThat(serviceConfig.getExecutes(), equalTo(10));
    }

    @Test
    public void testFilter() throws Exception { //已测（设置过滤器名称）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setFilter("mockfilter");
        assertThat(serviceConfig.getFilter(), equalTo("mockfilter")); //设置过滤器名称
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(SERVICE_FILTER_KEY, "prefilter");
        AbstractServiceConfig.appendParameters(parameters, serviceConfig);
        assertThat(parameters, hasEntry(SERVICE_FILTER_KEY, "prefilter,mockfilter")); //有多个过滤器名称，用","分隔
    }

    @Test
    public void testListener() throws Exception { //已测（设置监听器名称）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setListener("mockexporterlistener");
        assertThat(serviceConfig.getListener(), equalTo("mockexporterlistener"));
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(EXPORTER_LISTENER_KEY, "prelistener");
        AbstractServiceConfig.appendParameters(parameters, serviceConfig);
        assertThat(parameters, hasEntry(EXPORTER_LISTENER_KEY, "prelistener,mockexporterlistener")); //有多个监听器名称，用","分隔
    }

    @Test
    public void testRegister() throws Exception { //已测（是否向注册中心注册）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setRegister(true);
        assertThat(serviceConfig.isRegister(), is(true));
    }

    @Test
    public void testWarmup() throws Exception { //已测（设置服务预热时间）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setWarmup(100);
        assertThat(serviceConfig.getWarmup(), equalTo(100));
    }

    @Test
    public void testSerialization() throws Exception { //已测（设置序列化方式）
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setSerialization("serialization");
        assertThat(serviceConfig.getSerialization(), equalTo("serialization"));
    }


    private static class ServiceConfig extends AbstractServiceConfig {

    }
}
