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
package org.apache.dubbo.registry.client.metadata;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.metadata.MetadataService;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.ServiceInstanceMetadataCustomizer;

import java.util.SortedSet;

import static org.apache.dubbo.metadata.MetadataService.toURLs;
import static org.apache.dubbo.metadata.WritableMetadataService.getExtension;
import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.*;

/**
 * An {@link ServiceInstanceMetadataCustomizer} to customize the {@link URL urls} of {@link MetadataService}
 * into {@link ServiceInstance#getMetadata() the service instances' metadata}
 *
 * @see ServiceInstanceMetadataCustomizer
 * @since 2.7.5
 */
public class MetadataServiceURLParamsMetadataCustomizer extends ServiceInstanceMetadataCustomizer { // 元数据url中参数处理的元数据自定器

    @Override
    public String resolveMetadataPropertyName(ServiceInstance serviceInstance) {
        return METADATA_SERVICE_URL_PARAMS_PROPERTY_NAME; //元数据对应的属性名：dubbo.metadata-service.url-params
    }

    @Override
    public String resolveMetadataPropertyValue(ServiceInstance serviceInstance) {

        String metadataStorageType = getMetadataStorageType(serviceInstance); //获取元数据存储类型，即为WritableMetadataService的扩展名

        WritableMetadataService writableMetadataService = getExtension(metadataStorageType); //根据SPI机制获取到WritableMetadataService实例

        String serviceInterface = MetadataService.class.getName(); //提取元数据信息

        String group = serviceInstance.getServiceName();

        String version = MetadataService.VERSION;

        SortedSet<String> urls = writableMetadataService.getExportedURLs(serviceInterface, group, version); //查找serviceInterface, group, version对应关联服务存储的元数据列表

        return getMetadataServiceParameter(toURLs(urls)); //存储的url元数据中参数Map对应的字符串
    }
}
