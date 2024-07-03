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
package org.apache.dubbo.metadata.report.support;

import com.google.gson.Gson;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.metadata.definition.ServiceDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptySet;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class AbstractMetadataReportTest { //@DtY-Doing

    /**
     * 知识点：
     *
     * 知识点概括：
     *
     */

    private NewMetadataReport abstractMetadataReport;


    @BeforeEach
    public void before() {
        URL url = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        abstractMetadataReport = new NewMetadataReport(url); //做初始化操作，AbstractMetadataReport的数据结构比较复杂，所以需要初始化的内容比较多
        // set the simple name of current class as the application name
        ApplicationModel.getConfigManager().setApplication(new ApplicationConfig(getClass().getSimpleName()));
    }

    @AfterEach
    public void reset() {
        // reset
        ApplicationModel.reset();
    }

    @Test
    public void testGetProtocol() { //Done_获取MetadataReport的协议
        URL url = URL.valueOf("dubbo://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic&side=provider");
        String protocol = abstractMetadataReport.getProtocol(url); //protocol的值会取url中side或protocol的值
        assertEquals(protocol, "provider"); //此处side有值，且为provider

        URL url2 = URL.valueOf("consumer://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        String protocol2 = abstractMetadataReport.getProtocol(url2);
        assertEquals(protocol2, "consumer"); //此处side无值，取url.getProtocol()的值

        /**
         * 结果分析：
         * 1）元数据上报的协议为url中side参数或protocol的值（表明是来自提供方还是消费方的元数据）
         */
    }

    @Test
    public void testStoreProviderUsual() throws ClassNotFoundException, InterruptedException { //Doing_测试存储提供者元数据
        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        //abstractMetadataReport对应的实例为NewMetadataReport，NewMetadataReport中会将提供者元数据存在在缓存map中，即abstractMetadataReport.store中
        MetadataIdentifier providerMetadataIdentifier = storePrivider(abstractMetadataReport, interfaceName, version, group, application);
        Thread.sleep(1500);
        // 由于提供者元数据已存入abstractMetadataReport.store，所以按MetadataIdentifier的唯一键能够取出值
        Assertions.assertNotNull(abstractMetadataReport.store.get(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY)));

        /**
         * 结果分析：
         * 1）
         */
    }

    @Test
    public void testStoreProviderSync() throws ClassNotFoundException, InterruptedException { //已测，同步方式上报元数据信息
        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        abstractMetadataReport.syncReport = true; //同步上报元数据信息
        MetadataIdentifier providerMetadataIdentifier = storePrivider(abstractMetadataReport, interfaceName, version, group, application);
        Assertions.assertNotNull(abstractMetadataReport.store.get(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY)));
    }

    @Test
    public void testFileExistAfterPut() throws InterruptedException, ClassNotFoundException { //已测，测试文件在元数据保存后是否存在
        //just for one method
        URL singleUrl = URL.valueOf("redis://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.metadata.store.InterfaceNameTestService?version=1.0.0&application=singleTest");
        NewMetadataReport singleMetadataReport = new NewMetadataReport(singleUrl); //构建对象时，对创建缓存文件的File对象，但还没有创建文件，在要存储元数据到本地文件中时，才创建文件。

        Assertions.assertFalse(singleMetadataReport.localCacheFile.exists()); //目前只有File对象，但文件还不存在

        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        // 会在AbstractMetadataReport.doSaveProperties方法中，将属性对象的值存入缓存文件中（存储元数据后，就会创建File对象的）
        MetadataIdentifier providerMetadataIdentifier = storePrivider(singleMetadataReport, interfaceName, version, group, application);

        Thread.sleep(2000); //暂停2s，因为写文件是异步执行的，给出足够的时间保存文件
        assertTrue(singleMetadataReport.localCacheFile.exists());
        assertTrue(singleMetadataReport.properties.containsKey(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY)));
    }

    @Test
    public void testRetry() throws InterruptedException, ClassNotFoundException { //已测，测试重试
        String interfaceName = "org.apache.dubbo.metadata.store.RetryTestService";
        String version = "1.0.0.retry";
        String group = null;
        String application = "vic.retry";
        URL storeUrl = URL.valueOf("retryReport://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestServiceForRetry?version=1.0.0.retry&application=vic.retry");

        /**
         * 在未上报元数据前，测试RetryMetadataReport成员属性的初始值
         */
        RetryMetadataReport retryReport = new RetryMetadataReport(storeUrl, 2);
        retryReport.metadataReportRetry.retryPeriod = 400L; //指定重试周期，此处是400ms
        URL url = URL.valueOf("dubbo://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        Assertions.assertNull(retryReport.metadataReportRetry.retryScheduledFuture); //AbstractMetadataReport中的内部类MetadataReportRetry在构建时，没有初始化retryScheduledFuture，所以取成员变量的默认值了
        assertEquals(0, retryReport.metadataReportRetry.retryCounter.get());
        assertTrue(retryReport.store.isEmpty());
        assertTrue(retryReport.failedReports.isEmpty()); //failedReports初始时为空的Map

        /**
         * 存储提供者元数据
         */
        storePrivider(retryReport, interfaceName, version, group, application);
        Thread.sleep(150);

        assertTrue(retryReport.store.isEmpty()); //因为RetryMetadataReport在存储时doStoreProviderMetadata() 做了限制，在执行次数小于等于重试次数时，抛出异常，进入重试逻辑，所以一开始是进入重试逻辑的，所以store此时还为空

        Assertions.assertFalse(retryReport.failedReports.isEmpty()); //失败的集合failedReports不为空，因为进入了失败重试逻辑，未成功的数据暂存在失败的集合了
        Assertions.assertNotNull(retryReport.metadataReportRetry.retryScheduledFuture);
        Thread.sleep(2000L); //当前线程暂停期间，程序有在执行重试任务
        assertTrue(retryReport.metadataReportRetry.retryCounter.get() != 0);
        assertTrue(retryReport.metadataReportRetry.retryCounter.get() >= 3); //重试次数有在递增，因为上面指定的周期是400ms，重试次数是2，所以在暂停2s期间，次数至少为3次（超过设置RetryMetadataReport设置的2次）
        Assertions.assertFalse(retryReport.store.isEmpty()); //重试成功后，就会正常存储到store，所以不为空
        assertTrue(retryReport.failedReports.isEmpty()); //正常重试后，是会将对应的数据从失败缓存中移除的
    }

    @Test
    public void testRetryCancel() throws InterruptedException, ClassNotFoundException { //已测，测试取消重试任务
        String interfaceName = "org.apache.dubbo.metadata.store.RetryTestService";
        String version = "1.0.0.retrycancel";
        String group = null;
        String application = "vic.retry";
        URL storeUrl = URL.valueOf("retryReport://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestServiceForRetryCancel?version=1.0.0.retrycancel&application=vic.retry");
        RetryMetadataReport retryReport = new RetryMetadataReport(storeUrl, 2);
        retryReport.metadataReportRetry.retryPeriod = 150L;
        retryReport.metadataReportRetry.retryTimesIfNonFail = 2;

        storePrivider(retryReport, interfaceName, version, group, application);
        Thread.sleep(80);

        Assertions.assertFalse(retryReport.metadataReportRetry.retryScheduledFuture.isCancelled()); //此时重试任务还没有执行完，所以是不是取消的。重试任务至少执行3次，即executeTimes > needRetryTimes
        Assertions.assertFalse(retryReport.metadataReportRetry.retryExecutor.isShutdown());
        Thread.sleep(1000L); //睡眠1秒后，重试任务已经执行完了，已经能正常存储了
        assertTrue(retryReport.metadataReportRetry.retryScheduledFuture.isCancelled()); //取消任务成功（为何前后都是isCancelled()，结果却不一样？解：在于重试任务是有有执行完成）
        assertTrue(retryReport.metadataReportRetry.retryExecutor.isShutdown());

    }

    // 存储了以后返回元数据标识符MetadataIdentifier，相当于保存信息成功后，返回一个id，就可以通过这个id查找到保存的信息了
    private MetadataIdentifier storePrivider(AbstractMetadataReport abstractMetadataReport, String interfaceName, String version, String group, String application) throws ClassNotFoundException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?version=" + version + "&application="
                + application + (group == null ? "" : "&group=" + group) + "&testPKey=8989");

        MetadataIdentifier providerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, PROVIDER_SIDE, application); //构建MetadataIdentifier
        Class interfaceClass = Class.forName(interfaceName);
        FullServiceDefinition fullServiceDefinition = ServiceDefinitionBuilder.buildFullDefinition(interfaceClass, url.getParameters()); //构建FullServiceDefinition

        // 提供端与消费端存储的元数据不一样的，提供端存储的是FullServiceDefinition，消费端存储的url.getParameters()
        abstractMetadataReport.storeProviderMetadata(providerMetadataIdentifier, fullServiceDefinition); //当前abstractMetadataReport的实现类是NewMetadataReport，该对象是将服务元数据存储在NewMetadataReport.store缓存中的

        return providerMetadataIdentifier;
    }

    private MetadataIdentifier storeConsumer(AbstractMetadataReport abstractMetadataReport, String interfaceName, String version, String group, String application, Map<String, String> tmp) throws ClassNotFoundException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?version=" + version + "&application="
                + application + (group == null ? "" : "&group=" + group) + "&testPKey=9090");

        tmp.putAll(url.getParameters());
        MetadataIdentifier consumerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, CONSUMER_SIDE, application);

        abstractMetadataReport.storeConsumerMetadata(consumerMetadataIdentifier, tmp);

        return consumerMetadataIdentifier;
    }

    @Test
    public void testPublishAll() throws ClassNotFoundException, InterruptedException { //已测，测试提供端元数据、消费端元数据发送以及数据差异，以及publishAll()发布全部数据

        assertTrue(abstractMetadataReport.store.isEmpty()); //还没有存储元数据，所以是空的
        assertTrue(abstractMetadataReport.allMetadataReports.isEmpty());
        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        MetadataIdentifier providerMetadataIdentifier1 = storePrivider(abstractMetadataReport, interfaceName, version, group, application);
        Thread.sleep(1000); //为啥要暂存1s？因为默认存储元数据是采集异步处理的，所以需要等一段时间，等到数据存储好
        assertEquals(abstractMetadataReport.allMetadataReports.size(), 1); //此处的成员变量是protected，同一个包可以访问，可以直接访问而不用方法，但通常一般设置为private，用方法进行访问，不然破坏成员的封装性了
        assertTrue(((FullServiceDefinition) abstractMetadataReport.allMetadataReports.get(providerMetadataIdentifier1)).getParameters().containsKey("testPKey"));

        // 接口名相同，但version、group不同，对应的MetadataIdentifier就不同（可以认为存储Map的key不同），会作为不同的ServiceDefinition进行存储
        MetadataIdentifier providerMetadataIdentifier2 = storePrivider(abstractMetadataReport, interfaceName, version + "_2", group + "_2", application);
        Thread.sleep(1000);
        assertEquals(abstractMetadataReport.allMetadataReports.size(), 2);
        assertTrue(((FullServiceDefinition) abstractMetadataReport.allMetadataReports.get(providerMetadataIdentifier2)).getParameters().containsKey("testPKey"));
        assertEquals(((FullServiceDefinition) abstractMetadataReport.allMetadataReports.get(providerMetadataIdentifier2)).getParameters().get("version"), version + "_2");

        Map<String, String> tmpMap = new HashMap<>();
        tmpMap.put("testKey", "value");
        MetadataIdentifier consumerMetadataIdentifier = storeConsumer(abstractMetadataReport, interfaceName, version + "_3", group + "_3", application, tmpMap);
        Thread.sleep(1000);
        assertEquals(abstractMetadataReport.allMetadataReports.size(), 3);

        //此处为啥是Map类型？allMetadataReports Map存的值应该是ServiceDefinition吧？
        //因为提供端和消费端提供的数据不一样，提供端存储的是FullServiceDefinition，消费端存储的是url的parameters
        Map tmpMapResult = (Map) abstractMetadataReport.allMetadataReports.get(consumerMetadataIdentifier); //取到的是消费端存储的元数据，所以直接转换为Map
        assertEquals(tmpMapResult.get("testPKey"), "9090");
        assertEquals(tmpMapResult.get("testKey"), "value");
        assertEquals(3, abstractMetadataReport.store.size());

        abstractMetadataReport.store.clear(); //清空缓存Map，只是清空实现类NewMetadataReport的缓存store，但是父类AbstractMetadataReport的缓存是没有清除的

        assertEquals(0, abstractMetadataReport.store.size());

        abstractMetadataReport.publishAll(); //会基于父类AbstractMetadataReport中的allMetadataReports缓存，来发布元数据
        Thread.sleep(200);

        assertEquals(3, abstractMetadataReport.store.size()); //因为做了重新发布publishAll()，所以abstractMetadataReport.store的又有内容了

        //元数据的唯一key：会以指定的分隔符将interface、version、group、side、application、parameters等依次拼接唯一字符串，如 org.apache.dubbo.metadata.store.InterfaceNameTestService:1.0.0::provider:vic
        String v = abstractMetadataReport.store.get(providerMetadataIdentifier1.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
        Gson gson = new Gson();
        FullServiceDefinition data = gson.fromJson(v, FullServiceDefinition.class); //将json字符串转换对应的对象
        checkParam(data.getParameters(), application, version); //检查参数

        String v2 = abstractMetadataReport.store.get(providerMetadataIdentifier2.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
        gson = new Gson();
        data = gson.fromJson(v2, FullServiceDefinition.class); //提供端存储的是FullServiceDefinition
        checkParam(data.getParameters(), application, version + "_2");

        String v3 = abstractMetadataReport.store.get(consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
        gson = new Gson();
        Map v3Map = gson.fromJson(v3, Map.class); //消费端存储的是参数Map
        checkParam(v3Map, application, version + "_3");
    }

    @Test
    public void testCalculateStartTime() { //已测，计算具体2~6点之间的时间间隔
        for (int i = 0; i < 300; i++) {
            long time = abstractMetadataReport.calculateStartTime(); //具体2点到6点的时间间隔
            long t = time + System.currentTimeMillis();
//            System.out.println("距离凌晨2~6点时间间隔=" + time);  //此处由于2~6点
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t);
            assertTrue(c.get(Calendar.HOUR_OF_DAY) >= 2);
            assertTrue(c.get(Calendar.HOUR_OF_DAY) <= 6);
        }
    }

    /**
     * Test {@link MetadataReport#saveExportedURLs(String, String, String)} method
     *
     * @since 2.7.8
     */
    @Test
    public void testSaveExportedURLs() { //已测，测试保存暴露的url
        // 存储暴露url对应的，如ZookeeperMetadataReport.saveExportedURLs中，即把构建url，并在远程对应创建一个节点
        String serviceName = null;
        String exportedServiceRevision = null;
        String exportedURLsContent = null;
        SortedSet<String> exportedURLs = null;
        // Default methods return true
        // 因为NewMetadataReport没有实现MetadataReport中的saveExportedURLs方法，所以会调用接口中默认方法，该默认方法会返回true
        assertTrue(abstractMetadataReport.saveExportedURLs(exportedURLs));
        assertTrue(abstractMetadataReport.saveExportedURLs(exportedServiceRevision, exportedURLs));
        assertTrue(abstractMetadataReport.saveExportedURLs(serviceName, exportedServiceRevision, exportedURLs));
        assertTrue(abstractMetadataReport.saveExportedURLs(serviceName, exportedServiceRevision, exportedURLsContent));
    }

    /**
     * Test {@link MetadataReport#getExportedURLs(String, String)} method
     *
     * @since 2.7.8
     */
    @Test
    public void testGetExportedURLs() { //已测，获取暴露的url
        String serviceName = null;
        String exportedServiceRevision = null;
        assertEquals(emptySet(), abstractMetadataReport.getExportedURLs(serviceName, exportedServiceRevision));
    }

    /**
     * Test {@link MetadataReport#getExportedURLsContent(String, String)} method
     *
     * @since 2.7.8
     */
    @Test
    public void testGetExportedURLsContent() { //已测
        String serviceName = null;
        String exportedServiceRevision = null;
        assertNull(abstractMetadataReport.getExportedURLsContent(serviceName, exportedServiceRevision));
    }

    private FullServiceDefinition toServiceDefinition(String v) {
        Gson gson = new Gson();
        FullServiceDefinition data = gson.fromJson(v, FullServiceDefinition.class);
        return data;
    }

    private void checkParam(Map<String, String> map, String application, String version) {
        assertEquals(map.get("application"), application);
        assertEquals(map.get("version"), version);
    }

    private Map<String, String> queryUrlToMap(String urlQuery) {
        if (urlQuery == null) {
            return Collections.emptyMap();
        }
        String[] pairs = urlQuery.split("&");
        Map<String, String> map = new HashMap<>();
        for (String pairStr : pairs) {
            String[] pair = pairStr.split("=");
            map.put(pair[0], pair[1]);
        }
        return map;
    }


    private static class NewMetadataReport extends AbstractMetadataReport { //自定义的MetadataReport类

        Map<String, String> store = new ConcurrentHashMap<>();

        public NewMetadataReport(URL metadataReportURL) {
            super(metadataReportURL); //调用父类的构造函数进行初始化
        }

        @Override
        protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
            store.put(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceDefinitions); //做本地缓存存储，没有发起远程调用
        }

        @Override
        protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
            store.put(consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceParameterString);
        }

        @Override
        protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) { //未实现的方法，抛出异常
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urls) {

        }

        @Override
        protected String doGetSubscribedURLs(SubscriberMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        public String getServiceDefinition(MetadataIdentifier consumerMetadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }
    }

    private static class RetryMetadataReport extends AbstractMetadataReport { //自定义的重试元数据上报实例：静态内部类

        Map<String, String> store = new ConcurrentHashMap<>();
        int needRetryTimes; //需要重试的次数
        int executeTimes = 0; //执行的次数

        public RetryMetadataReport(URL metadataReportURL, int needRetryTimes) {
            super(metadataReportURL); //初始化父类对象
            this.needRetryTimes = needRetryTimes;
        }

        @Override
        protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
            ++executeTimes; //执行存储时，执行次数自增1
            System.out.println("***" + executeTimes + ";" + System.currentTimeMillis());
            if (executeTimes <= needRetryTimes) { //用于模拟重试的逻辑，就是在执行次数小于重试次数时，都跑出异常，那么执行逻辑就会进去到失败重试的逻辑了
                throw new RuntimeException("must retry:" + executeTimes);
            }
            // 执行次数需要大于重试次数，才能执行业务逻辑
            store.put(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceDefinitions);
        }

        @Override
        protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
            ++executeTimes;
            if (executeTimes <= needRetryTimes) {
                throw new RuntimeException("must retry:" + executeTimes);
            }
            store.put(consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceParameterString);
        }

        @Override
        protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urls) {

        }

        @Override
        protected String doGetSubscribedURLs(SubscriberMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        public String getServiceDefinition(MetadataIdentifier consumerMetadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

    }


}
