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
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.MetadataReportFactory;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.config.configcenter.TreePathDynamicConfiguration.CONFIG_ROOT_PATH_PARAM_NAME;
import static org.apache.dubbo.common.utils.StringUtils.SLASH;
import static org.apache.dubbo.common.utils.StringUtils.isBlank;
import static org.apache.dubbo.metadata.report.identifier.KeyTypeEnum.PATH;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * The abstract implementation of {@link MetadataReportFactory} based on
 * {@link DynamicConfiguration the config-center infrastructure}
 *
 * @see MetadataReportFactory
 * @see MetadataReport
 * @see DynamicConfiguration
 * @since 2.7.8
 */
public abstract class ConfigCenterBasedMetadataReportFactory implements MetadataReportFactory { //配置中心基础元数据上报工厂

    /**
     * org.apache.dubbo.metadata.report.MetadataReport
     */
    private static final String URL_PATH = MetadataReport.class.getName();

    // Registry Collection Map<metadataAddress, MetadataReport>
    private static final Map<String, ConfigCenterBasedMetadataReport> metadataReportCache = new ConcurrentHashMap();

    private final KeyTypeEnum keyType;

    public ConfigCenterBasedMetadataReportFactory(KeyTypeEnum keyType) {
        if (keyType == null) {
            throw new NullPointerException("The keyType argument must not be null!");
        }
        this.keyType = keyType;
    }

    @Override
    public ConfigCenterBasedMetadataReport getMetadataReport(URL url) { //获取配置中心元数据上报实例
        url = url.setPath(URL_PATH); //更换url的路径path，值为："org.apache.dubbo.metadata.report.MetadataReport"
        final URL actualURL = resolveURLParameters(url);
        String key = actualURL.toServiceString();
        // Lock the metadata access process to ensure a single instance of the metadata instance（锁定元数据访问进程，保证元数据实例的单一实例）
        return metadataReportCache.computeIfAbsent(key, k -> new ConfigCenterBasedMetadataReport(actualURL, keyType)); //设置元数据上报缓存，会根据Map的computeIfAbsent确保key不重复，来保证单实例创建
    }

    private URL resolveURLParameters(URL url) { //todo @csy 待调试
        URL resolvedURL = url.removeParameters(EXPORT_KEY, REFER_KEY); //移除URL中参数export, refer
        if (PATH.equals(getKeyType())) { // Only handles for "PATH" type
            if (isBlank(resolvedURL.getParameter(CONFIG_ROOT_PATH_PARAM_NAME))) {
                resolvedURL = resolvedURL.addParameter(CONFIG_ROOT_PATH_PARAM_NAME, SLASH); //设置url的CONFIG_ROOT_PATH_PARAM_NAME参数对应的值
            }
        }
        return resolvedURL;
    }

    /**
     * Get {@link KeyTypeEnum the key type}
     *
     * @return non-null
     */
    protected KeyTypeEnum getKeyType() {
        return keyType;
    }
}
