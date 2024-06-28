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
package org.apache.dubbo.metadata.store.redis;

import org.apache.commons.lang3.SystemUtils;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.metadata.definition.ServiceDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.rpc.RpcException;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.embedded.RedisServer;
import redis.embedded.RedisServerBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.metadata.report.support.Constants.SYNC_REPORT_KEY;

/**
 * 2018/10/9
 */
public class RedisMetadataReportTest { //DtY-Doing

    /**
     * 知识点：Redis做元数据中心
     *
     * 知识点概括：
     * 1）
     *
     *
     * 关联点学习：
     * 1）ScheduledFuture 周期执行任务：功能了解（Doing）
     * 2）SecurityManager功能用途了解（Doing）
     * 3）redis客户端API连接服务端的功能实践（Doing）
     * 4）RandomAccessFile和FileLock了解
     *
     *
     *
     *
     * 问题点答疑：
     * 1）MetadataIdentifier与ServiceDefinition有什么关联？
     * 2）TypeBuilder的SPI接口的功能用途是什么？
     * 3）FileLock与Lock有何不同？
     *
     *
     */

    RedisMetadataReport redisMetadataReport;
    RedisMetadataReport syncRedisMetadataReport;
    RedisServer redisServer; //嵌入的Redis服务端
    URL registryUrl;

    @BeforeEach
    public void constructor(TestInfo testInfo) throws IOException { //TestInfo可以获取到测试方法的信息
        int redisPort = NetUtils.getAvailablePort();
        String methodName = testInfo.getTestMethod().get().getName();
        if ("testAuthRedisMetadata".equals(methodName) || ("testWrongAuthRedisMetadata".equals(methodName))) { //对指定的测试方法处理
            String password = "チェリー";
            RedisServerBuilder builder = RedisServer.builder().port(redisPort).setting("requirepass " + password);
            if (SystemUtils.IS_OS_WINDOWS) {
                // set maxheap to fix Windows error 0x70 while starting redis
                builder.setting("maxheap 128mb");
            }
            redisServer = builder.build();
            registryUrl = URL.valueOf("redis://username:" + password + "@localhost:" + redisPort);
        } else {
            RedisServerBuilder builder = RedisServer.builder().port(redisPort); //构建内嵌的redis服务
            if (SystemUtils.IS_OS_WINDOWS) {
                // set maxheap to fix Windows error 0x70 while starting redis
                builder.setting("maxheap 128mb");
            }
            redisServer = builder.build();
            registryUrl = URL.valueOf("redis://localhost:" + redisPort);
        }

        this.redisServer.start(); //启动redis服务，可以通过lsof -i:xxx 查看到有对应的端口启动了服务
        redisMetadataReport = (RedisMetadataReport) new RedisMetadataReportFactory().createMetadataReport(registryUrl);
        URL asyncRegistryUrl = URL.valueOf("redis://localhost:" + redisPort + "?" + SYNC_REPORT_KEY + "=true"); //SYNC_REPORT_KEY=true表示同步上报，否则为异步上报（此处声明的asyncRegistryUrl没用上，看来并非本意）
        syncRedisMetadataReport = (RedisMetadataReport) new RedisMetadataReportFactory().createMetadataReport(registryUrl);
    }

    @AfterEach
    public void tearDown() throws Exception {
        this.redisServer.stop();
    }

    @Test
    public void testAsyncStoreProvider() throws ClassNotFoundException { //Doing
        testStoreProvider(redisMetadataReport, "1.0.0.redis.md.p1", 3000);

        /**
         * 结果分析：
         *
         *
         *
         *
         * 问题点答疑：
         * 1）与testStoreProvider同步存储元数据有何不同？
         *
         * 2）本地缓存文件dubbo-metadata-null-xxx.cache中都存了什么内容？
         *
         * 3）dubbo-metadata-null-xxx.cache.lock文件的作用是什么？为什么里面没有内容？
         *    解答：用.lock文件来创建文件锁FileLock，然后在写入本地属性文件时，进行加锁处理，不需要文件中有内容
         *         （类似使用synchronized(obj) ，锁住一个对象的操作）
         *
         * 4）ScheduledExecutorService cycleReportExecutor和ExecutorService reportCacheExecutor上报任务有何区别？
         *    解答：同步上报和异步上报元数据的区别（同步上报：就是在当前线程上执行，异步上报：就是在另外的线程执行）
         *
         */
    }

    @Test
    public void testSyncStoreProvider() throws ClassNotFoundException { //Doing
        testStoreProvider(syncRedisMetadataReport, "1.0.0.redis.md.p2", 3);

        /**
         * 结果分析：
         *
         * 问题点答疑：
         */
    }

    private void testStoreProvider(RedisMetadataReport redisMetadataReport, String version, long moreTime) throws ClassNotFoundException {
        String interfaceName = "org.apache.dubbo.metadata.store.redis.RedisMetadata4TstService";
        String group = null;
        String application = "vic.redis.md";
        MetadataIdentifier providerMetadataIdentifier = storePrivider(redisMetadataReport, interfaceName, version, group, application);
        Jedis jedis = null;
        try {
            jedis = redisMetadataReport.pool.getResource();
            String keyTmp = providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY);
            String value = jedis.get(keyTmp);
            if (value == null) {
                Thread.sleep(moreTime);
                value = jedis.get(keyTmp);
            }

            Assertions.assertNotNull(value);

            Gson gson = new Gson();
            FullServiceDefinition fullServiceDefinition = gson.fromJson(value, FullServiceDefinition.class);
            Assertions.assertEquals(fullServiceDefinition.getParameters().get("paramTest"), "redisTest");
        } catch (Throwable e) {
            throw new RpcException("Failed to put to redis . cause: " + e.getMessage(), e);
        } finally {
            if (jedis != null) {
                jedis.del(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
            }
            redisMetadataReport.pool.close();
        }
    }

    @Test
    public void testAsyncStoreConsumer() throws ClassNotFoundException {
        testStoreConsumer(redisMetadataReport, "1.0.0.redis.md.c1", 3000);
    }

    @Test
    public void testSyncStoreConsumer() throws ClassNotFoundException {
        testStoreConsumer(syncRedisMetadataReport, "1.0.0.redis.md.c2", 3);
    }

    private void testStoreConsumer(RedisMetadataReport redisMetadataReport, String version, long moreTime) throws ClassNotFoundException {
        String interfaceName = "org.apache.dubbo.metadata.store.redis.RedisMetadata4TstService";
        String group = null;
        String application = "vic.redis.md";
        MetadataIdentifier consumerMetadataIdentifier = storeConsumer(redisMetadataReport, interfaceName, version, group, application);
        Jedis jedis = null;
        try {
            jedis = redisMetadataReport.pool.getResource();
            String keyTmp = consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY);
            String value = jedis.get(keyTmp);
            if (value == null) {
                Thread.sleep(moreTime);
                value = jedis.get(keyTmp);
            }
            Assertions.assertEquals(value, "{\"paramConsumerTest\":\"redisCm\"}");
        } catch (Throwable e) {
            throw new RpcException("Failed to put to redis . cause: " + e.getMessage(), e);
        } finally {
            if (jedis != null) {
                jedis.del(consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
            }
            redisMetadataReport.pool.close();
        }
    }

    private MetadataIdentifier storePrivider(RedisMetadataReport redisMetadataReport, String interfaceName, String version, String group, String application) throws ClassNotFoundException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?paramTest=redisTest&version=" + version + "&application="
                + application + (group == null ? "" : "&group=" + group));

        MetadataIdentifier providerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, PROVIDER_SIDE, application);
        Class interfaceClass = Class.forName(interfaceName);
        FullServiceDefinition fullServiceDefinition = ServiceDefinitionBuilder.buildFullDefinition(interfaceClass, url.getParameters());

        redisMetadataReport.storeProviderMetadata(providerMetadataIdentifier, fullServiceDefinition);
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return providerMetadataIdentifier;
    }

    private MetadataIdentifier storeConsumer(RedisMetadataReport redisMetadataReport, String interfaceName, String version, String group, String application) throws ClassNotFoundException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?version=" + version + "&application="
                + application + (group == null ? "" : "&group=" + group));

        MetadataIdentifier consumerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, CONSUMER_SIDE, application);
        Class interfaceClass = Class.forName(interfaceName);

        Map<String, String> tmp = new HashMap<>();
        tmp.put("paramConsumerTest", "redisCm");
        redisMetadataReport.storeConsumerMetadata(consumerMetadataIdentifier, tmp);
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return consumerMetadataIdentifier;
    }

    @Test
    public void testAuthRedisMetadata() throws ClassNotFoundException {
        testStoreProvider(redisMetadataReport, "1.0.0.redis.md.p1", 3000);
    }

    @Test
    public void testWrongAuthRedisMetadata() throws ClassNotFoundException {
        registryUrl = registryUrl.setPassword("123456");
        redisMetadataReport = (RedisMetadataReport) new RedisMetadataReportFactory().createMetadataReport(registryUrl);
        try {
            testStoreProvider(redisMetadataReport, "1.0.0.redis.md.p1", 3000);
        } catch (RpcException e) {
            if (e.getCause() instanceof JedisConnectionException && e.getCause().getCause() instanceof JedisDataException) {
                Assertions.assertEquals("ERR invalid password", e.getCause().getCause().getMessage());
            } else {
                Assertions.fail("no invalid password exception!");
            }
        }
    }
}
