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
package org.apache.dubbo.remoting.zookeeper.curator;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.zookeeper.ChildListener;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.WatchedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

public class CuratorZookeeperClientTest { //@DtY-Doing（Zk客户端Curator测试）

    private TestingServer zkServer;
    private CuratorZookeeperClient curatorClient;
    CuratorFramework client = null;

    @BeforeEach
    public void setUp() throws Exception {
        int zkServerPort = NetUtils.getAvailablePort();
        zkServer = new TestingServer(zkServerPort, true); //使用API的方式启动zk服务端
        curatorClient = new CuratorZookeeperClient(URL.valueOf("zookeeper://127.0.0.1:" +
                zkServerPort + "/org.apache.dubbo.registry.RegistryService"));
        client = CuratorFrameworkFactory.newClient(zkServer.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        client.start();
    }

    @Test
    public void testCheckExists() { //Done_创建节点以及检查节点
        String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";
        curatorClient.create(path, false);
        assertThat(curatorClient.checkExists(path), is(true));
        assertThat(curatorClient.checkExists(path + "/noneexits"), is(false));

        /**
         * 结果分析：
         * 1）创建节点：AbstractZookeeperClient#create创建节点时，会将节点路径进行按"/"分隔，
         *   然后递归创建节点，如"/A/B/C"，会被分为"/A/B/C" -> "/A/B" -> "/A"
         *   最终创建的顺序是从根路径开始创建，如："/A" -> "/A/B" -> "/A/B/C"
         *
         * 2）检查节点：CuratorZookeeperClient#checkExists，最终会调用Zk客户端curator的checkExists进行节点检查
         */
    }

    @Test
    public void testChildrenPath() { //Done_获取节点的子节点列表
        String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";
        curatorClient.create(path, false);
        curatorClient.create(path + "/provider1", false);
        curatorClient.create(path + "/provider2", false);

        List<String> children = curatorClient.getChildren(path);
        assertThat(children.size(), is(2));

        /**
         * 结果分析：
         * 1）getChildren(path);获取指定节点的子节点列表，此处的children值为"provider1"、"provider2"
         */
    }

    @Test
    public void testChildrenListener() throws InterruptedException { //Done_添加子节点的事件监听
        String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";
        curatorClient.create(path, false);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        curatorClient.addTargetChildListener(path, new CuratorZookeeperClient.CuratorWatcherImpl() { //添加path子节点的监听器

            @Override
            public void process(WatchedEvent watchedEvent) throws Exception {
                countDownLatch.countDown(); //监听到子节点的变更，如：type:NodeChildrenChanged
            }
        });
        curatorClient.createPersistent(path + "/provider1"); //创建path的子节点
        countDownLatch.await(); //让当前线程阻塞，直到latch计数减到0（可以代替System.in.read()，使任务完成）

        /**
         * 结果分析：
         * 1）curatorClient.addTargetChildListener为指定路径的节点添加子节点监听器（当子节点有变更时，会回调process方法）
         * 2）curatorClient.createPersistent创建子节点，即path节点的子节点有变更，所以会回调process方法
         */
    }


    @Test
    public void testWithInvalidServer() { //Done_用无效地址连接zk服务端时，会抛出异常
        Assertions.assertThrows(IllegalStateException.class, () -> {
            curatorClient = new CuratorZookeeperClient(URL.valueOf("zookeeper://127.0.0.1:1/service"));
            curatorClient.create("/testPath", true);
        });

        /**
         * 结果分析：
         * 1）创建CuratorZookeeperClient时，会通过curator的客户端连接zk服务端，因为此处
         *    zk服务端地址为127.0.0.1:1，并非有效，所以连接不上zk服务端，就会抛出异常
         */
    }

    @Test
    public void testWithStoppedServer() throws IOException { //Done_停止zk服务
        Assertions.assertThrows(IllegalStateException.class, () -> {
            curatorClient.create("/testPath", true);
            zkServer.stop(); //停止zk服务
            curatorClient.delete("/testPath");
        });

        /**
         * 结果分析：
         * 1）在进行zkServer.stop()后，就停止了zk服务，如果还进行节点操作，
         *    本质会抛出org.apache.zookeeper.KeeperException$ConnectionLossException连接不上的异常
         *    最终对外抛出IllegalStateException异常
         */
    }

    @Test
    public void testRemoveChildrenListener() { //Done_移除子节点监听器
        ChildListener childListener = mock(ChildListener.class); //使用Mock为ChildListener接口创建实例
        curatorClient.addChildListener("/children", childListener);
        curatorClient.removeChildListener("/children", childListener);

        /**
         * 结果分析：
         * 1）addChildListener：为子节点添加监听器
         *   1.1）将监听器添加到缓存AbstractZookeeperClient的childListeners中
         *   1.2）通过zk客户端API，如curator为子节点添加监听器
         *
         * 2）removeChildListener：移除子节点监听器
         *   2.1）将监听器从缓存AbstractZookeeperClient的childListeners移除
         *   2.2）执行CuratorWatcherImpl的unwatch()方法，即this.childListener = null;
         *        因为当子节点有变化时，会回调process()方法，会判断childListener是否非空，若为空则不执行。
         */
    }

    @Test
    public void testCreateExistingPath() { //Done_节点创建时判断是否已存在
        curatorClient.create("/pathOne", false);
        curatorClient.create("/pathOne", false);

        /**
         * 结果分析：
         * 1）在AbstractZookeeperClient#create创建节点时，若是永久节点会判断节点路径是否在集合
         *    若在集合中，则不会进行创建的操作。
         */
    }

    @Test
    public void testConnectedStatus() { //Done_测试连接状态
        curatorClient.createEphemeral("/testPath");
        boolean connected = curatorClient.isConnected();
        assertThat(connected, is(true));

        /**
         * 1）调用zk客户端API直接判断是否处于连接
         */
    }

    @Test
    public void testCreateContent4Persistent() { //Done_获取节点内容
        String path = "/curatorTest4CrContent/content.data";
        String content = "createContentTest";
        curatorClient.delete(path); //先做删除，后添加（清理测试数据）
        assertThat(curatorClient.checkExists(path), is(false));
        assertNull(curatorClient.getContent(path));

        curatorClient.create(path, content, false);
        assertThat(curatorClient.checkExists(path), is(true));
        assertEquals(curatorClient.getContent(path), content);

        /**
         * 结果分析：
         * 1）创建节点时，指定节点数据内容，并可以通过getContent(path)，获取节点内容
         */
    }

    @Test
    public void testCreateContent4Temp() { //Done_创建临时节点
        String path = "/curatorTest4CrContent/content.data";
        String content = "createContentTest";
        curatorClient.delete(path);
        assertThat(curatorClient.checkExists(path), is(false));
        assertNull(curatorClient.getContent(path));

        curatorClient.create(path, content, true);
        assertThat(curatorClient.checkExists(path), is(true));
        assertEquals(curatorClient.getContent(path), content);

        /**
         * 结果分析：
         * 1）curator创建节点时，默认是持久节点类型CreateMode.PERSISTENT，创建临时节点时，
         *    需要指明临时节点类型CreateMode.EPHEMERAL
         */
    }

    @AfterEach
    public void tearDown() throws Exception {
        curatorClient.close();
        zkServer.stop();
    }

    @Test
    public void testAddTargetDataListener() throws Exception { //Doing
        String listenerPath = "/dubbo/service.name/configuration";
        String path = listenerPath + "/dat/data";
        String value = "vav";

        curatorClient.create(path + "/d.json", value, true);
        String valueFromCache = curatorClient.getContent(path + "/d.json");
        Assertions.assertEquals(value, valueFromCache);
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        curatorClient.addTargetDataListener(listenerPath, new CuratorZookeeperClient.CuratorWatcherImpl() {
            @Override
            public void childEvent(CuratorFramework client, TreeCacheEvent event) throws Exception {
                System.out.println("===" + event);
                atomicInteger.incrementAndGet();
            }
        });

        valueFromCache = curatorClient.getContent(path + "/d.json");
        Assertions.assertNotNull(valueFromCache);
        curatorClient.getClient().setData().forPath(path + "/d.json", "sdsdf".getBytes());
        curatorClient.getClient().setData().forPath(path + "/d.json", "dfsasf".getBytes());
        curatorClient.delete(path + "/d.json");
        curatorClient.delete(path);
        valueFromCache = curatorClient.getContent(path + "/d.json");
        Assertions.assertNull(valueFromCache);
        Thread.sleep(2000L);
        Assertions.assertTrue(9L >= atomicInteger.get());
        Assertions.assertTrue(2L <= atomicInteger.get());

        /**
         * 输出结果：
         * ===TreeCacheEvent{type=NODE_ADDED, data=ChildData{path='/dubbo/service.name/configuration', stat=5,5,1716163999671,1716163999671,0,1,0,0,13,1,6
         * , data=[49, 57, 50, 46, 49, 54, 56, 46, 49, 46, 49, 48, 56]}}
         * ===TreeCacheEvent{type=NODE_ADDED, data=ChildData{path='/dubbo/service.name/configuration/dat', stat=6,6,1716163999673,1716163999673,0,1,0,0,13,1,7
         * , data=[49, 57, 50, 46, 49, 54, 56, 46, 49, 46, 49, 48, 56]}}
         * ===TreeCacheEvent{type=NODE_ADDED, data=ChildData{path='/dubbo/service.name/configuration/dat/data', stat=7,7,1716163999674,1716163999674,0,1,0,0,13,1,8
         * , data=[49, 57, 50, 46, 49, 54, 56, 46, 49, 46, 49, 48, 56]}}
         * ===TreeCacheEvent{type=NODE_ADDED, data=ChildData{path='/dubbo/service.name/configuration/dat/data/d.json', stat=8,10,1716163999675,1716163999699,2,0,0,72058087353548800,6,0,8
         * , data=[100, 102, 115, 97, 115, 102]}}
         * ===TreeCacheEvent{type=INITIALIZED, data=null}
         * ===TreeCacheEvent{type=NODE_REMOVED, data=ChildData{path='/dubbo/service.name/configuration/dat/data/d.json', stat=8,10,1716163999675,1716163999699,2,0,0,72058087353548800,6,0,8
         * , data=null}}
         * ===TreeCacheEvent{type=NODE_REMOVED, data=ChildData{path='/dubbo/service.name/configuration/dat/data', stat=7,7,1716163999674,1716163999674,0,2,0,0,13,0,11
         * , data=null}}
         *
         * 结果分析：
         */
    }
}
