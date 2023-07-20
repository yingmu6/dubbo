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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DUMP_DIRECTORY;
import static org.apache.dubbo.common.constants.QosConstants.ACCEPT_FOREIGN_IP;
import static org.apache.dubbo.common.constants.QosConstants.QOS_ENABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class ApplicationConfigTest {
    @Test
    public void testName() throws Exception { //已测（设置应用的名称）
        ApplicationConfig application = new ApplicationConfig();
        application.setName("app");
        assertThat(application.getName(), equalTo("app"));
        application = new ApplicationConfig("app2");
        assertThat(application.getName(), equalTo("app2"));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry(APPLICATION_KEY, "app2")); //getName()方法上声明为@Parameter(key = APPLICATION_KEY, required = true, useKeyAsProperty = false)
    }

    @Test
    public void testVersion() throws Exception { //已测（设置应用的版本号）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setVersion("1.0.0");
        assertThat(application.getVersion(), equalTo("1.0.0"));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry("application.version", "1.0.0"));
    }

    @Test
    public void testOwner() throws Exception { //已测（设置应用负责人）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setOwner("owner");
        assertThat(application.getOwner(), equalTo("owner"));
    }

    @Test
    public void testOrganization() throws Exception { //已测（设置应用所属组织）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setOrganization("org");
        assertThat(application.getOrganization(), equalTo("org"));
    }

    @Test
    public void testArchitecture() throws Exception { //已测（设置架构分层）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setArchitecture("arch");
        assertThat(application.getArchitecture(), equalTo("arch"));
    }

    @Test
    public void testEnvironment1() throws Exception { //已测（设置应用环境，值只能是develop/test/product）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setEnvironment("develop"); //开发环境
        assertThat(application.getEnvironment(), equalTo("develop"));
        application.setEnvironment("test"); //测试环境
        assertThat(application.getEnvironment(), equalTo("test"));
        application.setEnvironment("product"); //生产环境
        assertThat(application.getEnvironment(), equalTo("product"));
    }

    @Test
    public void testEnvironment2() throws Exception { //已测（设置应用环境时，不是对应的develop/test/product，即抛出异常）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            ApplicationConfig application = new ApplicationConfig("app");
            application.setEnvironment("illegal-env");
        });
    }

    @Test
    public void testRegistry() throws Exception { //已测（设置注册中心配置，一个应用可对应多个注册中心）
        ApplicationConfig application = new ApplicationConfig("app");
        RegistryConfig registry = new RegistryConfig();
        application.setRegistry(registry); //可按单个设置
        assertThat(application.getRegistry(), sameInstance(registry));
        application.setRegistries(Collections.singletonList(registry)); //可按列表设置
        assertThat(application.getRegistries(), contains(registry));
        assertThat(application.getRegistries(), hasSize(1));
    }

    @Test
    public void testMonitor() throws Exception { //已测（设置监控中心配置）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setMonitor(new MonitorConfig("monitor-addr")); //设置监控中心配置
        assertThat(application.getMonitor().getAddress(), equalTo("monitor-addr"));
        application.setMonitor("monitor-addr"); //设置监控中心名称
        assertThat(application.getMonitor().getAddress(), equalTo("monitor-addr"));
    }

    @Test
    public void testLogger() throws Exception { //已测（设置日志输出方式）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setLogger("log4j");
        assertThat(application.getLogger(), equalTo("log4j"));
    }

    @Test
    public void testDefault() throws Exception { //已测（设置默认应用）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setDefault(true);
        assertThat(application.isDefault(), is(true));
    }

    @Test
    public void testDumpDirectory() throws Exception { //已测（设置线程dump的文件路径）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setDumpDirectory("/dump");
        assertThat(application.getDumpDirectory(), equalTo("/dump"));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry(DUMP_DIRECTORY, "/dump"));
    }

    @Test
    public void testQosEnable() throws Exception { //已测（是否启用qos运维端口）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosEnable(true);
        assertThat(application.getQosEnable(), is(true));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry(QOS_ENABLE, "true"));
    }

    @Test
    public void testQosPort() throws Exception { //已测（设置监听的qos端口）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosPort(8080);
        assertThat(application.getQosPort(), equalTo(8080));
    }

    @Test
    public void testQosAcceptForeignIp() throws Exception { //已测（是否接受外部的ip）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosAcceptForeignIp(true);
        assertThat(application.getQosAcceptForeignIp(), is(true));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry(ACCEPT_FOREIGN_IP, "true"));
    }

    @Test
    public void testParameters() throws Exception { //已测（读取config属性，添加到参数Map中）
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosAcceptForeignIp(true);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("k1", "v1");
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry("k1", "v1"));
        assertThat(parameters, hasEntry(ACCEPT_FOREIGN_IP, "true"));
    }

    @Test
    public void testAppendEnvironmentProperties() { //已测（应用中添加环境变量值）
        try {
            ApplicationConfig application = new ApplicationConfig("app");
            System.setProperty("dubbo.labels", "tag1=value1;tag2=value2 ; tag3 = value3"); //解析参数值时，会按";"和"="进行分隔
            application.refresh();
            Map<String, String> parameters = application.getParameters();
            Assertions.assertEquals("value1", parameters.get("tag1"));
            Assertions.assertEquals("value2", parameters.get("tag2"));
            Assertions.assertEquals("value3", parameters.get("tag3"));
            /**
             * 调试问题点：
             * 1）此处的application.getParameters()，值是设定的？
             * 解答：application.refresh()会调用两处：
             *      a）AbstractConfig#refresh()
             *         会根据config的属性或get方法上声明的@Parameter中的key，去从各个配置中去取值。因为ApplicationConfig没有这个dubbo.labels属性，所以就不会去提取对应的值
             *
             *      b）ApplicationConfig#appendEnvironmentProperties()
             *         会找到InfraAdapter对应的SPI扩展实例，然后去提取额外的配置值getExtraAttributes()，最终会调用ApplicationConfig#appendEnvironmentProperties()
             *         里面会查找两个属性的值，如"dubbo.labels"、"dubbo.env.keys"
             */

            ApplicationConfig application1 = new ApplicationConfig("app");
            System.setProperty("dubbo.env.keys", "tag1, tag2,tag3"); //按逗号分隔出参数key列表
            // mock environment variables
            System.setProperty("tag1", "value1"); //依次设置参数的值
            System.setProperty("tag2", "value2");
            System.setProperty("tag3", "value3");
            application1.refresh();
            Map<String, String> parameters1 = application1.getParameters();
            Assertions.assertEquals("value1", parameters1.get("tag1"));
            Assertions.assertEquals("value2", parameters1.get("tag2"));
            Assertions.assertEquals("value3", parameters1.get("tag3"));

            Map<String, String> urlParameters = new HashMap<>();
            ApplicationConfig.appendParameters(urlParameters, application1);
            Assertions.assertEquals("value1", urlParameters.get("tag1"));
            Assertions.assertEquals("value2", urlParameters.get("tag2"));
            Assertions.assertEquals("value3", urlParameters.get("tag3"));
        } finally {
            System.clearProperty("dubbo.labels");
            System.clearProperty("dubbo.keys");
        }
    }
}
