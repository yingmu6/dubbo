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
package org.apache.dubbo.config.context;

import org.apache.dubbo.common.config.Configuration;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.AbstractConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * This class receives an {@link AbstractConfig} and exposes（暴露） its attributes through {@link Configuration}
 * （这个类用于接收AbstractConfig，并且把它的属性值通过Configuration暴露出来）
 */
public class ConfigConfigurationAdapter implements Configuration {

    private Map<String, String> metaData;

    public ConfigConfigurationAdapter(AbstractConfig config) { //适配Config中的元数据key，带上前缀prefix和id值
        Map<String, String> configMetadata = config.getMetaData();
        metaData = new HashMap<>(configMetadata.size());
        for (Map.Entry<String, String> entry : configMetadata.entrySet()) {
            String prefix = config.getPrefix().endsWith(".") ? config.getPrefix() : config.getPrefix() + "."; //在前缀加上点号
            String id = StringUtils.isEmpty(config.getId()) ? "" : config.getId() + "."; //若id不为空时，拼接上点号
            metaData.put(prefix + id + entry.getKey(), entry.getValue()); //map中存储的key，拼接格式：prefix + id + entry.getKey()，如dubbo.application.qos-host
        }
    }

    @Override
    public Object getInternalProperty(String key) {
        return metaData.get(key);
    }

}
