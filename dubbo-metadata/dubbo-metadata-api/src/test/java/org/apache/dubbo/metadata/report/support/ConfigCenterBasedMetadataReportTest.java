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
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.metadata.URLRevisionResolver;
import org.apache.dubbo.metadata.definition.ServiceDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.metadata.report.support.file.FileSystemMetadataReportFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.service.EchoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.metadata.report.support.Constants.SYNC_REPORT_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ConfigCenterBasedMetadataReport} Test-Cases
 *
 * @since 2.7.8
 */
public class ConfigCenterBasedMetadataReportTest {

    private static final URL REPORT_SERVER_URL = URL.valueOf("file://")
            .addParameter(APPLICATION_KEY, "test")
            .addParameter(SYNC_REPORT_KEY, "true");

    private static final Class<EchoService> INTERFACE_CLASS = EchoService.class;

    private static final String INTERFACE_NAME = INTERFACE_CLASS.getName();

    private static final String APP_NAME = "test-service";

    private static final URL BASE_URL = URL
            .valueOf("dubbo://127.0.0.1:20880")
            .setPath(INTERFACE_NAME)
            .addParameter(APPLICATION_KEY, APP_NAME)
            .addParameter(SIDE_KEY, "provider");

    private ConfigCenterBasedMetadataReport metadataReport;

    @BeforeEach
    public void init() { //在每个测试用例执行前，做初始化操作
        ApplicationModel.getConfigManager().setApplication(new ApplicationConfig("test-service"));
        this.metadataReport = new FileSystemMetadataReportFactory().getMetadataReport(REPORT_SERVER_URL); //创建ConfigCenterBasedMetadataReport实例，其中也包含DynamicConfiguration实例的创建
    }

    @AfterEach
    public void reset() throws Exception { //在每个测试用例执行后，做相关销毁工作
        ApplicationModel.reset();
        this.metadataReport.close();
    }

    /**
     * Test {@link MetadataReport#storeProviderMetadata(MetadataIdentifier, ServiceDefinition)} and
     * {@link MetadataReport#getServiceDefinition(MetadataIdentifier)}
     */
    @Test
    public void testStoreProviderMetadataAndGetServiceDefinition() { //已测，测试本地文件存储元数据以及从本地文件中获取到元数据
        MetadataIdentifier metadataIdentifier = new MetadataIdentifier(BASE_URL);
        ServiceDefinition serviceDefinition = ServiceDefinitionBuilder.buildFullDefinition(INTERFACE_CLASS, BASE_URL.getParameters());
        // 提供者的元数据信息最终通过FileSystemDynamicConfiguration.doPublishConfig写到本地配置文件中
        metadataReport.storeProviderMetadata(metadataIdentifier, serviceDefinition); //metadataReport实例是在当前init()方法中进行创建的

        // 从配置文件中去获取存储的提供者元数据信息
        String serviceDefinitionJSON = metadataReport.getServiceDefinition(metadataIdentifier); //此处为啥获取到的值是JSON字符串？提供者元数据是按json字符串存储的
        assertEquals(serviceDefinitionJSON, new Gson().toJson(serviceDefinition));
    }

    /**
     * Test {@link MetadataReport#storeConsumerMetadata(MetadataIdentifier, Map)} and
     * {@link MetadataReport#getServiceDefinition(MetadataIdentifier)}
     */
    @Test
    public void testStoreConsumerMetadata() { //已测，存储消费者元数据和获取消费者元数据（两者只是存储的元数据不同，操作逻辑是一样的）
        MetadataIdentifier metadataIdentifier = new MetadataIdentifier(BASE_URL);

        // 存储url参数Map对应的json字符串
        metadataReport.storeConsumerMetadata(metadataIdentifier, BASE_URL.getParameters());

        // 获取元数据标识符对应存储的内容（获取提供者、消费者的元数据，都用getServiceDefinition()方法 ）
        String parametersJSON = metadataReport.getServiceDefinition(metadataIdentifier);
        assertEquals(parametersJSON, new Gson().toJson(BASE_URL.getParameters()));
    }

    /**
     * Test {@link MetadataReport#saveServiceMetadata(ServiceMetadataIdentifier, URL)} and
     * {@link MetadataReport#removeServiceMetadata(ServiceMetadataIdentifier)}
     */
    @Test
    public void testSaveServiceMetadataAndRemoveServiceMetadata() { //已测，存储服务元数据（操作逻辑与存储提供者、消费者元数据相同，只是数据不一样）
        ServiceMetadataIdentifier metadataIdentifier = new ServiceMetadataIdentifier(BASE_URL);

        // saveServiceMetadata中存储的内容是url编码后的字符串
        metadataReport.saveServiceMetadata(metadataIdentifier, BASE_URL);

        String metadata = metadataReport.getMetadata(metadataIdentifier);
        assertEquals(URL.encode(BASE_URL.toFullString()), metadata);

        // 此处移除服务元数据，会把本地元数据存储文件删掉
        metadataReport.removeServiceMetadata(metadataIdentifier);
        assertNull(metadataReport.getMetadata(metadataIdentifier));
    }

    /**
     * Test {@link MetadataReport#saveSubscribedData(SubscriberMetadataIdentifier, Collection)} and
     * {@link MetadataReport#getSubscribedURLs(SubscriberMetadataIdentifier)}
     */
    @Test
    public void testSaveSubscribedDataAndGetSubscribedURLs() { //已测，测试订阅的元数据存储和获取
        SubscriberMetadataIdentifier metadataIdentifier = new SubscriberMetadataIdentifier(BASE_URL);
        Set<String> urls = singleton(BASE_URL).stream().map(URL::toIdentityString).collect(toSet()); //构建带有身份信息的url集合

        // 存储订阅的元数据（数据为订阅url集合对应的字符串）
        metadataReport.saveSubscribedData(metadataIdentifier, urls);

        // 获取订阅的元数据（数据为订阅的url集合）
        Collection<String> subscribedURLs = metadataReport.getSubscribedURLs(metadataIdentifier);

        assertEquals(1, subscribedURLs.size());
        assertEquals(urls, subscribedURLs);
    }

    /**
     * Test {@link MetadataReport#saveExportedURLs(SortedSet)},
     * {@link MetadataReport#getExportedURLsContent(String, String)} and
     * {@link MetadataReport#getExportedURLs(String, String)}
     */
    @Test
    public void testSaveExportedURLsAndGetExportedURLs() { //已测，暴露服务url对应的元数据
        SortedSet<String> urls = singleton(BASE_URL).stream().map(URL::toIdentityString).collect(TreeSet::new, Set::add, Set::addAll);

        // 存储的元数据内容，是暴露的url列表
        metadataReport.saveExportedURLs(urls);

        URLRevisionResolver urlRevisionResolver = URLRevisionResolver.INSTANCE;
        String revision = urlRevisionResolver.resolve(urls);
        assertEquals(urls, metadataReport.getExportedURLs(APP_NAME, revision));
    }
}
