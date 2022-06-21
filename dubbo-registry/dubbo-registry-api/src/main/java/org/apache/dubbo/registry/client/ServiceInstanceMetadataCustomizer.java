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
package org.apache.dubbo.registry.client;

import java.util.Map;

import static org.apache.dubbo.common.utils.StringUtils.isBlank;

/**
 * The abstract class to customize {@link ServiceInstance#getMetadata()}  the service instances' metadata}
 *
 * @see ServiceInstance#getMetadata()
 * @see ServiceInstanceCustomizer
 * @since 2.7.5
 */
public abstract class ServiceInstanceMetadataCustomizer implements ServiceInstanceCustomizer {

    @Override
    public final void customize(ServiceInstance serviceInstance) { //对服务实例的元数据进行处理

        Map<String, String> metadata = serviceInstance.getMetadata();

        // 元数据信息：比如提供者存储的数据为ServiceDefinition对应的字符串，消费者存储的是Map<String, Object>参数Map对应的字符串
        String propertyName = resolveMetadataPropertyName(serviceInstance); //交由具体实现类处理
        String propertyValue = resolveMetadataPropertyValue(serviceInstance);

        if (!isBlank(propertyName) && !isBlank(propertyValue)) {
            String existedValue = metadata.get(propertyName);
            // 若元数据中对应的propertyName值为空，且允许值覆盖时，设置解析的元数据值
            boolean put = existedValue == null || isOverride();
            if (put) {
                metadata.put(propertyName, propertyValue);
            }
        }
    }

    /**
     * Resolve the property name of metadata（解析ServiceInstance对应的元数据属性名）
     *
     * @param serviceInstance the instance of {@link ServiceInstance}
     * @return non-null key
     */
    protected abstract String resolveMetadataPropertyName(ServiceInstance serviceInstance);

    /**
     * Resolve the property value of metadata（解析ServiceInstance对应的元数据属性值）
     *
     * @param serviceInstance the instance of {@link ServiceInstance}
     * @return non-null value
     */
    protected abstract String resolveMetadataPropertyValue(ServiceInstance serviceInstance);

    /**
     * Is override {@link ServiceInstance#getMetadata()}  the service instances' metadata} or not
     *
     * @return default is <code>false</code>
     */
    protected boolean isOverride() {
        return false;
    }
}
