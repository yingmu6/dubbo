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
package org.apache.dubbo.common.config;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.ConfigUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Configuration from system properties and dubbo.properties
 * （从系统属性和dubbo.properties文件中获取配置信息）
 */
public class PropertiesConfiguration implements Configuration {

    public PropertiesConfiguration() {
        ExtensionLoader<OrderedPropertiesProvider> propertiesProviderExtensionLoader = ExtensionLoader.getExtensionLoader(OrderedPropertiesProvider.class);
        Set<String> propertiesProviderNames = propertiesProviderExtensionLoader.getSupportedExtensions();
        if (propertiesProviderNames == null || propertiesProviderNames.isEmpty()) { //OrderedPropertiesProvider目前的实现类只有MockOrderedPropertiesProvider1、MockOrderedPropertiesProvider2用于测试的实现类
            return;
        }
        List<OrderedPropertiesProvider> orderedPropertiesProviders = new ArrayList<>();
        for (String propertiesProviderName : propertiesProviderNames) {
            orderedPropertiesProviders.add(propertiesProviderExtensionLoader.getExtension(propertiesProviderName));
        }

        //order the propertiesProvider according the priority descending（根据priority进行降序排序）
        orderedPropertiesProviders.sort((OrderedPropertiesProvider a, OrderedPropertiesProvider b) -> {
            return b.priority() - a.priority();
        });

        //load the default properties（加载默认的属性值）
        Properties properties = ConfigUtils.getProperties();

        //override（覆盖） the properties. (执行属性的putAll时，若存在相同属性key时，后面key对应的值会覆盖前面key对应的值)
        for (OrderedPropertiesProvider orderedPropertiesProvider :
                orderedPropertiesProviders) {
            properties.putAll(orderedPropertiesProvider.initProperties()); //因为前面是按priority降序排序的，所以priority越小就在列表的越后面，此处循环putAll时，后面的Properties就会覆盖相同的key值（验证了类上的描述，priority越小，优先级越大这句话）
        }

        ConfigUtils.setProperties(properties); //将Properties对象缓存起来
    }

    @Override
    public Object getInternalProperty(String key) {
        return ConfigUtils.getProperty(key);
    }
}
