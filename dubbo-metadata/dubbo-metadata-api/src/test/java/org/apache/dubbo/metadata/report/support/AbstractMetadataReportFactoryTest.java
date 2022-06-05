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

import com.alibaba.fastjson.JSON;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2018/9/14
 */
public class AbstractMetadataReportFactoryTest { //测试抽象元数据工厂中方法

    private AbstractMetadataReportFactory metadataReportFactory = new AbstractMetadataReportFactory() {
        @Override
        protected MetadataReport createMetadataReport(URL url) { //实现抽象类中的抽象方法
            return new MetadataReport() {

                @Override
                public void storeProviderMetadata(MetadataIdentifier providerMetadataIdentifier, ServiceDefinition serviceDefinition) {
                    store.put(providerMetadataIdentifier.getIdentifierKey(), JSON.toJSONString(serviceDefinition));
                }

                @Override
                public void saveServiceMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {

                }

                @Override
                public void removeServiceMetadata(ServiceMetadataIdentifier metadataIdentifier) {

                }

                @Override
                public List<String> getExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
                    return null;
                }

                @Override
                public void saveSubscribedData(SubscriberMetadataIdentifier subscriberMetadataIdentifier,
                                               Collection<String> urls) {

                }

                @Override
                public List<String> getSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
                    return null;
                }

                @Override
                public String getServiceDefinition(MetadataIdentifier consumerMetadataIdentifier) {
                    return null;
                }

                @Override
                public void storeConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, Map serviceParameterMap) {
                    store.put(consumerMetadataIdentifier.getIdentifierKey(), JSON.toJSONString(serviceParameterMap));
                }

                @Override
                public void close() throws Exception {

                }

                Map<String, String> store = new ConcurrentHashMap<>();


            };
        }
    };

    @Test
    public void testGetOneMetadataReport() { //已测，一个url对应一个MetadataReport实例，不管取多少次，都只返回一个实例
        URL url = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url); //会以url为key从缓存Map查找MetadataReport，url相同则获取的内容相同
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url);
        Assertions.assertEquals(metadataReport1, metadataReport2);
    }

    @Test
    public void testGetOneMetadataReportForIpFormat() { //已测
        InetAddress inetAddress = NetUtils.getLocalAddress();
        String hostName = inetAddress.getHostName(); //hostName如：192.168.3.16
        String ip = NetUtils.getIpByHost(hostName);
        String ip2 = inetAddress.getHostAddress();
        System.out.println("ip=" + ip + ", ip2=" + ip2); //一样的结果
        URL url1 = URL.valueOf("zookeeper://" + hostName + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        URL url2 = URL.valueOf("zookeeper://" + ip + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url1); //本地IP没有绑定域名，所以hostName与Ip是相同的
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url2);
        Assertions.assertEquals(metadataReport1, metadataReport2);
    }

    @Test
    public void testGetForDiffService() { //已测
        URL url1 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService1?version=1.0.0&application=vic");
        URL url2 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService2?version=1.0.0&application=vic");
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url1);
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url2);
        Assertions.assertEquals(metadataReport1, metadataReport2);
        // 因为url中的path都会被替换为org.apache.dubbo.metadata.report.MetadataReport，
        // 所以上面两个url只是path不同，其它参数都相同的情况下，转换后的url是相等的，所以MetadataReport是相等的
    }

    @Test
    public void testGetForDiffGroup() { //已测
        URL url1 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic&group=aaa");
        URL url2 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic&group=bbb");
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url1); //因为此处是group参数不一样，该参数在获取MetadataReport实例时，不会被处理，所以最终两个url不同，获取到的实例就不同
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url2);
        Assertions.assertNotEquals(metadataReport1, metadataReport2);
    }
}
