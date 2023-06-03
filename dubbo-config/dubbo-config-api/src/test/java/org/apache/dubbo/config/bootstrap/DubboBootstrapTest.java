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
package org.apache.dubbo.config.bootstrap;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.config.AbstractInterfaceConfigTest;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.registry.RegistryService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_SECONDS_KEY;

/**
 * {@link DubboBootstrap} Test
 *
 * @since 2.7.5
 */
public class DubboBootstrapTest {

    private static File dubboProperties;

    @BeforeAll
    public static void setUp(@TempDir Path folder) { //@TempDir junit提供的临时地址
        dubboProperties = folder.resolve(CommonConstants.DUBBO_PROPERTIES_KEY).toFile(); //获取dubbo属性文件对应的File
        System.setProperty(CommonConstants.DUBBO_PROPERTIES_KEY, dubboProperties.getAbsolutePath());
    }

    @AfterEach
    public void tearDown() throws IOException {

    }

    @Test
    public void checkApplication() { //已测试（从设置的系统属性中获取值，写到ApplicationConfig中）
        System.setProperty("dubbo.application.name", "demo");
        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.refresh(); //会通过CompositeConfiguration合成配置，从系统属性、属性文件、内存中等获取到属性值，最后更新到Config对象中
        Assertions.assertEquals("demo", applicationConfig.getName());
        System.clearProperty("dubbo.application.name");
    }

    @Test
    public void compatibleApplicationShutdown() { //已测试（校验ApplicationConfig属性时，兼容系统停机时间）
        try {
            ConfigUtils.setProperties(null);
            System.clearProperty(SHUTDOWN_WAIT_KEY);
            System.clearProperty(SHUTDOWN_WAIT_SECONDS_KEY); //清理属性值

            writeDubboProperties(SHUTDOWN_WAIT_KEY, "105"); //将属性写到属性文件中
            ConfigValidationUtils.validateApplicationConfig(new ApplicationConfig("demo")); //在校验ApplicationConfig的属性值时，会兼容的把系统停机时间写到系统属性中
            Assertions.assertEquals("105", System.getProperty(SHUTDOWN_WAIT_KEY));

            System.clearProperty(SHUTDOWN_WAIT_KEY);
            ConfigUtils.setProperties(null);
            writeDubboProperties(SHUTDOWN_WAIT_SECONDS_KEY, "1000");
            ConfigValidationUtils.validateApplicationConfig(new ApplicationConfig("demo"));
            Assertions.assertEquals("1000", System.getProperty(SHUTDOWN_WAIT_SECONDS_KEY));
        } finally {
            ConfigUtils.setProperties(null);
            System.clearProperty("dubbo.application.name");
            System.clearProperty(SHUTDOWN_WAIT_KEY);
            System.clearProperty(SHUTDOWN_WAIT_SECONDS_KEY);
        }
    }

    @Test
    public void testLoadRegistries() {
        System.setProperty("dubbo.registry.address", "addr1");
        AbstractInterfaceConfigTest.InterfaceConfig interfaceConfig = new AbstractInterfaceConfigTest.InterfaceConfig();
        // FIXME: now we need to check first, then load
        interfaceConfig.setApplication(new ApplicationConfig("testLoadRegistries"));
        interfaceConfig.checkRegistry();
        List<URL> urls = ConfigValidationUtils.loadRegistries(interfaceConfig, true); //todo 此处RegistryConfig中的port是怎么得到的？
        Assertions.assertEquals(1, urls.size()); //此处RegistryConfig中的address属性为addr1，是通过合成配置，在SystemConfiguration取到的值
        URL url = urls.get(0);
        Assertions.assertEquals("registry", url.getProtocol());
        Assertions.assertEquals("addr1:9090", url.getAddress());
        Assertions.assertEquals(RegistryService.class.getName(), url.getPath());
        Assertions.assertTrue(url.getParameters().containsKey("timestamp"));
        Assertions.assertTrue(url.getParameters().containsKey("pid"));
        Assertions.assertTrue(url.getParameters().containsKey("registry"));
        Assertions.assertTrue(url.getParameters().containsKey("dubbo"));
    }


    @Test
    public void testLoadMonitor() {
        System.setProperty("dubbo.monitor.address", "monitor-addr:12080");
        System.setProperty("dubbo.monitor.protocol", "monitor");
        AbstractInterfaceConfigTest.InterfaceConfig interfaceConfig = new AbstractInterfaceConfigTest.InterfaceConfig();
        interfaceConfig.setApplication(new ApplicationConfig("testLoadMonitor"));
        interfaceConfig.setMonitor(new MonitorConfig());
        URL url = ConfigValidationUtils.loadMonitor(interfaceConfig, new URL("dubbo", "addr1", 9090));
        Assertions.assertEquals("monitor-addr:12080", url.getAddress());
        Assertions.assertEquals(MonitorService.class.getName(), url.getParameter("interface"));
        Assertions.assertNotNull(url.getParameter("dubbo"));
        Assertions.assertNotNull(url.getParameter("pid"));
        Assertions.assertNotNull(url.getParameter("timestamp"));
    }

    private void writeDubboProperties(String key, String value) {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(dubboProperties));
            Properties properties = new Properties();
            properties.put(key, value);
            properties.store(os, ""); //将属性值写到输出流对应的属性文件中
            os.close();
        } catch (IOException e) {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }
    }

}
