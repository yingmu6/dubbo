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
package org.apache.dubbo.configcenter.support.apollo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;

import com.ctrip.framework.apollo.mockserver.EmbeddedApollo;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Apollo dynamic configuration mock test.
 * Notice: EmbeddedApollo(apollo mock server) only support < junit5, please not upgrade the junit version in this UT,
 * the junit version in this UT is junit4, and the dependency comes from apollo-mockserver.
 */
public class ApolloDynamicConfigurationTest { //DtY-Doing

    /**
     * 知识点：Apollo配置中心
     *
     * 知识点概括：
     * 1）
     *
     * 关联点学习：
     * 1）ServiceLoader学习&实践：apollo客户端内部有使用到ServiceLoader加载配置文件（Doing）
     * 2）ConcurrentHashMap学习&实践：apollo客户端内部多次多用ConcurrentHashMap
     *
     *
     *
     *
     * 问题点答疑：
     * 1）apollo客户端，是在什么时候发起与apollo服务端的连接的？
     * 2）apollo中的application和namespace有什么区别？在<config-center/>中配置的group、namespace是怎么与apollo对应的？
     *
     *
     */

    private static final String SESSION_TIMEOUT_KEY = "session";
    private static final String DEFAULT_NAMESPACE = "dubbo";
    private static ApolloDynamicConfiguration apolloDynamicConfiguration;
    private static URL url;

    /**
     * The constant embeddedApollo.
     */
    @ClassRule
    public static EmbeddedApollo embeddedApollo = new EmbeddedApollo(); //内嵌的apollo服务端

    /**
     * Sets up.
     */
    @Before
    public void setUp() {
        String apolloUrl = System.getProperty("apollo.configService");
        String urlForDubbo = "apollo://" + apolloUrl.substring(apolloUrl.lastIndexOf("/") + 1) + "/org.apache.dubbo.apollo.testService?namespace=dubbo&check=true";
        url = URL.valueOf(urlForDubbo).addParameter(SESSION_TIMEOUT_KEY, 15000);
    }

//    /**
//     * Embedded Apollo does not work as expected.
//     */
//    @Test
//    public void testProperties() {
//        URL url = this.url.addParameter("GROUP_KEY", "dubbo")
//                .addParameter("namespace", "governance");
//
//        apolloDynamicConfiguration = new ApolloDynamicConfiguration(url);
//        putData("dubbo", "dubbo.registry.address", "zookeeper://127.0.0.1:2181");
//        assertEquals("zookeeper://127.0.0.1:2181", apolloDynamicConfiguration.getProperties(null, "dubbo"));
//
//        putData("governance", "router.tag", "router tag rule");
//        assertEquals("router tag rule", apolloDynamicConfiguration.getConfig("router.tag", "governance"));
//
//    }

    /**
     * Test get rule.
     */
    @Test
    public void testGetRule() { //Doing_@Pause-06/17
        String mockKey = "mockKey1";
        String mockValue = String.valueOf(new Random().nextInt());
        putMockRuleData(mockKey, mockValue, DEFAULT_NAMESPACE);
        apolloDynamicConfiguration = new ApolloDynamicConfiguration(url);
        assertEquals(mockValue, apolloDynamicConfiguration.getConfig(mockKey, DEFAULT_NAMESPACE, 3000L));

        mockKey = "notExistKey";
        assertNull(apolloDynamicConfiguration.getConfig(mockKey, DEFAULT_NAMESPACE, 3000L));

        /**
         * 结果分析：
         * 1）此用例没有发起与apollo服务端的远程连接，而是先把key、value写到target/test-classed/mockdata-dubbo.properties文件中
         *    然后再从该文件中读取到值，也就是实现mock测试
         */
    }

    /**
     * Test get internal property.
     *
     * @throws InterruptedException the interrupted exception
     */
    @Test
    public void testGetInternalProperty() throws InterruptedException {
        String mockKey = "mockKey2";
        String mockValue = String.valueOf(new Random().nextInt());
        putMockRuleData(mockKey, mockValue, DEFAULT_NAMESPACE); //将key、value存储到本地properties文件中
        TimeUnit.MILLISECONDS.sleep(1000);
        apolloDynamicConfiguration = new ApolloDynamicConfiguration(url);
        assertEquals(mockValue, apolloDynamicConfiguration.getInternalProperty(mockKey));

        mockValue = "mockValue2";
        System.setProperty(mockKey, mockValue);
        assertEquals(mockValue, apolloDynamicConfiguration.getInternalProperty(mockKey));

        mockKey = "notExistKey";
        assertNull(apolloDynamicConfiguration.getInternalProperty(mockKey));
    }

    /**
     * Test add listener.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAddListener() throws Exception {
        String mockKey = "mockKey3";
        String mockValue = String.valueOf(new Random().nextInt());

        final SettableFuture<org.apache.dubbo.common.config.configcenter.ConfigChangedEvent> future = SettableFuture.create();

        apolloDynamicConfiguration = new ApolloDynamicConfiguration(url);

        apolloDynamicConfiguration.addListener(mockKey, DEFAULT_NAMESPACE, new ConfigurationListener() {
            @Override
            public void process(org.apache.dubbo.common.config.configcenter.ConfigChangedEvent event) {
                future.set(event);
            }
        });

        putData(mockKey, mockValue);
        org.apache.dubbo.common.config.configcenter.ConfigChangedEvent result = future.get(3000, TimeUnit.MILLISECONDS);
        assertEquals(mockValue, result.getContent());
        assertEquals(mockKey, result.getKey());
        assertEquals(ConfigChangeType.MODIFIED, result.getChangeType());
    }

    private static void putData(String namespace, String key, String value) {
        embeddedApollo.addOrModifyProperty(namespace, key, value);
    }

    private static void putData(String key, String value) {
        embeddedApollo.addOrModifyProperty(DEFAULT_NAMESPACE, key, value);
    }

    private static void putMockRuleData(String key, String value, String group) {
        String fileName = ApolloDynamicConfigurationTest.class.getResource("/").getPath() + "mockdata-" + group + ".properties";
        putMockData(key, value, fileName);
    }

    private static void putMockData(String key, String value, String fileName) {
        Properties pro = new Properties();
        FileOutputStream oFile = null;
        try {
            oFile = new FileOutputStream(fileName);
            pro.setProperty(key, value);
            pro.store(oFile, "put mock data"); //将键值对存储到properties文件中
        } catch (IOException exx) {
            fail(exx.getMessage());

        } finally {
            if (null != oFile) {
                try {
                    oFile.close();
                } catch (IOException e) {
                    fail(e.getMessage());
                }
            }
        }
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {

    }

}