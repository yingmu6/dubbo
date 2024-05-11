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
package org.apache.dubbo.configcenter.support.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.NetUtils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO refactor using mockito
 */
public class ZookeeperDynamicConfigurationTest { //@DbT doing
    private static CuratorFramework client;

    private static URL configUrl;
    private static int zkServerPort = NetUtils.getAvailablePort(); //随机产生的服务端有效端口
    private static TestingServer zkServer;
    private static DynamicConfiguration configuration;

    @BeforeAll
    public static void setUp() throws Exception {
        zkServer = new TestingServer(zkServerPort, true); //使用API的方式启动zk服务端（也就是不需要在终端使用命令启动zk服务端）

        client = CuratorFrameworkFactory.newClient("127.0.0.1:" + zkServerPort, 60 * 1000, 60 * 1000,
                new ExponentialBackoffRetry(1000, 3));
        client.start();

        try { //若想连接到zk服务端看节点数据，则在单元测试用例加上System.in.read()，即用例一直处在读，而不终止状态，即可连接zk服务端看到数据
            setData("/dubbo/config/dubbo/dubbo.properties", "The content from dubbo.properties"); //在zk上创建指定的节点，并设置对应的数据
            setData("/dubbo/config/dubbo/service:version:group.configurators", "The content from configurators");
            setData("/dubbo/config/appname", "The content from higer level node");
            setData("/dubbo/config/dubbo/appname.tag-router", "The content from appname tagrouters");
            setData("/dubbo/config/dubbo/never.change.DemoService.configurators", "Never change value from configurators");
        } catch (Exception e) {
            e.printStackTrace();
        }


        configUrl = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort);

        configuration = ExtensionLoader.getExtensionLoader(DynamicConfigurationFactory.class).getExtension(configUrl.getProtocol()).getDynamicConfiguration(configUrl);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        zkServer.stop();
    }

    private static void setData(String path, String data) throws Exception {
        if (client.checkExists().forPath(path) == null) { //通过curator检查节点，若不存在则创建
            client.create().creatingParentsIfNeeded().forPath(path);
        }
        client.setData().forPath(path, data.getBytes()); //设置节点的数据
    }

    @Test
    public void testGetConfig() throws Exception { //done_获取远程配置中心，指定key的值（通过发起远程连接获取值）
        Assertions.assertEquals("The content from dubbo.properties", configuration.getConfig("dubbo.properties", "dubbo"));
        //System.in.read(); //用于一直读，可不让方法结束，从而看到服务端节点数据。

        /**
         * 结果分析：
         * 通过Zookeeper内嵌API方式启动zk服务端，并用zk客户端连接服务端，在zk节点上写入数据，然后通过configuration获取配置内容
         */
    }

    @Test
    public void testAddListener() throws Exception { //doing
        CountDownLatch latch = new CountDownLatch(4);
        TestListener listener1 = new TestListener(latch);
        TestListener listener2 = new TestListener(latch);
        TestListener listener3 = new TestListener(latch);
        TestListener listener4 = new TestListener(latch);
        configuration.addListener("service:version:group.configurators", listener1); //将监听器添加到CacheListener#keyListeners缓存中
        configuration.addListener("service:version:group.configurators", listener2);
        configuration.addListener("appname.tag-router", listener3);
        configuration.addListener("appname.tag-router", listener4);

        setData("/dubbo/config/dubbo/service:version:group.configurators", "new value1"); //在setUp()中有创建路径，setData()方法中做了路径判断，所以直接更新值
        Thread.sleep(100);
        setData("/dubbo/config/dubbo/appname.tag-router", "new value2");
        Thread.sleep(100);
        setData("/dubbo/config/appname", "new value3"); //更新节点的数据值

        Thread.sleep(5000);

        latch.await(); //CountDownLatch：闭锁（等待计数线程都完成了，当前线程才继续执行，即让当前线程阻塞，直到闭锁的计数减为0）
        Assertions.assertEquals(1, listener1.getCount("service:version:group.configurators"));
        Assertions.assertEquals(1, listener2.getCount("service:version:group.configurators"));
        Assertions.assertEquals(1, listener3.getCount("appname.tag-router"));
        Assertions.assertEquals(1, listener4.getCount("appname.tag-router"));

        Assertions.assertEquals("new value1", listener1.getValue());
        Assertions.assertEquals("new value1", listener2.getValue());
        Assertions.assertEquals("new value2", listener3.getValue());
        Assertions.assertEquals("new value2", listener4.getValue());

        /**
         * 结果分析：
         *
         * 问题点答疑：
         * 1）TestListener#countMap的缓存值，是什么时候写入的？
         */
    }

    @Test
    public void testPublishConfig() {
        String key = "user-service";
        String group = "org.apache.dubbo.service.UserService";
        String content = "test";

        assertTrue(configuration.publishConfig(key, group, content));
        assertEquals("test", configuration.getProperties(key, group));
    }

    @Test
    public void testGetConfigKeysAndContents() {

        String group = "mapping";
        String key = "org.apache.dubbo.service.UserService";
        String content = "app1";

        String key2 = "org.apache.dubbo.service.UserService2";

        assertTrue(configuration.publishConfig(key, group, content));
        assertTrue(configuration.publishConfig(key2, group, content));

        Set<String> configKeys = configuration.getConfigKeys(group);

        assertEquals(new TreeSet(asList(key, key2)), configKeys);
    }

    private class TestListener implements ConfigurationListener {
        private CountDownLatch latch;
        private String value;
        private Map<String, Integer> countMap = new HashMap<>();

        public TestListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void process(ConfigChangedEvent event) {
            System.out.println(this + ": " + event);
            Integer count = countMap.computeIfAbsent(event.getKey(), k -> new Integer(0));
            countMap.put(event.getKey(), ++count);

            value = event.getContent();
            latch.countDown();
        }

        public int getCount(String key) {
            return countMap.get(key);
        }

        public String getValue() {
            return value;
        }
    }

}
