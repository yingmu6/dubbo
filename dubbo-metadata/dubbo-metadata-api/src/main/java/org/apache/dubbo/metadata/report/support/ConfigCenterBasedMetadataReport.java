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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.identifier.*;

import java.util.List;

import static org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory.getDynamicConfigurationFactory;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.metadata.MetadataConstants.EXPORTED_URLS_TAG;

/**
 * The generic（通用的） implementation of {@link MetadataReport} based on {@link DynamicConfiguration
 * the config-center infrastructure}
 *
 * @see AbstractMetadataReport
 * @since 2.7.8
 */
public class ConfigCenterBasedMetadataReport extends AbstractMetadataReport { //配置中心元数据上报信息
    /**
     * 若子类没有重写抽象类的方法，则会调用抽象类的方法，重写了则调用子类的方法
     */

    private final KeyTypeEnum keyType;

    private final String group;

    private final DynamicConfiguration dynamicConfiguration; //动态配置

    public ConfigCenterBasedMetadataReport(URL reportServerURL, KeyTypeEnum keyTypeEnum) { //构造函数，初始化的操作都放在构造函数中进行。通常若对象比较复杂，都会在构造函数中进行一系列初始化操作
        super(reportServerURL);
        this.keyType = keyTypeEnum;
        this.group = reportServerURL.getParameter(GROUP_KEY, DEFAULT_ROOT);
        String extensionName = reportServerURL.getProtocol(); //将url中的protocol作为扩展名
        DynamicConfigurationFactory dynamicConfigurationFactory = getDynamicConfigurationFactory(extensionName); //DynamicConfigurationFactory是SPI接口，先根据扩展名获取到扩展实例。
        dynamicConfiguration = dynamicConfigurationFactory.getDynamicConfiguration(reportServerURL); //通过工厂创建对象，先明确是用哪个DynamicConfigurationFactory创建，然后就可以看到具体实例
    }


    @Override
    protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) { //存储提供者元数据和消费者元数据，只能内容不一样，操作流程是一致的
        saveMetadata(providerMetadataIdentifier, serviceDefinitions); //保存元数据
    }

    @Override
    protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
        saveMetadata(consumerMetadataIdentifier, serviceParameterString);
    }

    @Override
    protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
        saveMetadata(metadataIdentifier, URL.encode(url.toFullString())); //此处元数据存储的是url编码后的字符串
    }

    @Override
    protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        removeMetadata(metadataIdentifier);
    }

    @Override
    protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        throw new UnsupportedOperationException("doGetExportedURLs method will not be supported!");
    }

    @Override
    protected void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urlListStr) {
        saveMetadata(subscriberMetadataIdentifier, urlListStr);
    }

    @Override
    protected String doGetSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
        return getMetadata(subscriberMetadataIdentifier);
    }

    @Override
    public String getServiceDefinition(MetadataIdentifier metadataIdentifier) {
        return getMetadata(metadataIdentifier);
    }

    @Override
    public boolean saveExportedURLs(String serviceName, String exportedServicesRevision, String exportedURLsContent) {
        String key = buildExportedURLsMetadataKey(serviceName, exportedServicesRevision);
        return dynamicConfiguration.publishConfig(key, group, exportedURLsContent);
    }

    @Override
    public String getExportedURLsContent(String serviceName, String exportedServicesRevision) {
        String key = buildExportedURLsMetadataKey(serviceName, exportedServicesRevision);
        return dynamicConfiguration.getConfig(key, group);
    }

    private String buildExportedURLsMetadataKey(String serviceName, String exportedServicesRevision) {
        return keyType.build(EXPORTED_URLS_TAG, serviceName, exportedServicesRevision);
    }

    protected void saveMetadata(BaseMetadataIdentifier metadataIdentifier, String value) { //saveMetadata()：方法重载
        String key = getKey(metadataIdentifier); //key如：metadata/org.apache.dubbo.rpc.service.EchoService/provider/dubbo/revisionnull
        dynamicConfiguration.publishConfig(key, group, value); //value值如：dubbo%3A%2F%2F127.0.0.1%3A20880%2Forg.apache.dubbo.rpc.service.EchoService%3Fapplication%3Dtest-service%26side%3Dprovider
    }

    protected void saveMetadata(MetadataIdentifier metadataIdentifier, String value) {
        String key = getKey(metadataIdentifier); //key的值如：metadata/org.apache.dubbo.demo.GreetingService/provider/zhangsan
        //要看dynamicConfiguration在构造方法初始化时用哪个工厂创建的，比如用FileSystemDynamicConfigurationFactory创建的，那么实例就为FileSystemDynamicConfiguration
        //因为FileSystemDynamicConfiguration没有声明publishConfig()方法，而是继承父类TreePathDynamicConfiguration的，所以会调用TreePathDynamicConfiguration的publishConfig方法
        dynamicConfiguration.publishConfig(key, group, value);
    }

    protected String getMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        String key = getKey(metadataIdentifier);
        return dynamicConfiguration.getConfig(key, group); //对象使用前不需要进行创建，dynamicConfiguration是在哪里创建的？在当前的构造函数中
    }

    protected String getMetadata(MetadataIdentifier metadataIdentifier) {
        String key = getKey(metadataIdentifier);
        return dynamicConfiguration.getConfig(key, group); //如：当上报url为file://xxx时，此处的实例为FileSystemDynamicConfiguration，会根据协议取创建你工厂，再创建具体实例
    }

    protected String getMetadata(SubscriberMetadataIdentifier metadataIdentifier) {
        String key = getKey(metadataIdentifier);
        return dynamicConfiguration.getConfig(key, group);
    }

    protected void removeMetadata(MetadataIdentifier metadataIdentifier) {
        String key = getKey(metadataIdentifier);
        dynamicConfiguration.removeConfig(key, group);
    }

    protected void removeMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        String key = getKey(metadataIdentifier);
        dynamicConfiguration.removeConfig(key, group);
    }

    protected String getKey(BaseMetadataIdentifier metadataIdentifier) {
        return metadataIdentifier.getUniqueKey(keyType);
    }

    protected String getKey(MetadataIdentifier metadataIdentifier) { //获取元数据对应的唯一键，唯一键的拼接方式按照KeyTypeEnum分为按路径"/"或按分隔符":"进行拼接
        return metadataIdentifier.getUniqueKey(keyType);
    }

    protected void doClose() throws Exception {
        this.dynamicConfiguration.close();
    }
}
