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
package org.apache.dubbo.rpc.cluster.configurator.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONValidator;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.cluster.configurator.parser.model.ConfigItem;
import org.apache.dubbo.rpc.cluster.configurator.parser.model.ConfiguratorConfig;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.RegistryConstants.APP_DYNAMIC_CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.rpc.cluster.Constants.OVERRIDE_PROVIDERS_KEY;

/**
 * Config parser
 */
public class ConfigParser {

    public static List<URL> parseConfigurators(String rawConfig) {
        // compatible url JsonArray, such as [ "override://xxx", "override://xxx" ]
        if (isJsonArray(rawConfig)) { //数组类型，按数组解析
            return parseJsonArray(rawConfig);
        }

        List<URL> urls = new ArrayList<>();
        ConfiguratorConfig configuratorConfig = parseObject(rawConfig);

        String scope = configuratorConfig.getScope();
        List<ConfigItem> items = configuratorConfig.getConfigs(); //获取配置项列表

        if (ConfiguratorConfig.SCOPE_APPLICATION.equals(scope)) { //应用范围处理
            items.forEach(item -> urls.addAll(appItemToUrls(item, configuratorConfig)));
        } else {
            // service scope by default.
            items.forEach(item -> urls.addAll(serviceItemToUrls(item, configuratorConfig)));
        }
        return urls;
    }

    private static List<URL> parseJsonArray(String rawConfig) {
        List<URL> urls = new ArrayList<>();
        List<String> list = JSON.parseArray(rawConfig, String.class);
        if (!CollectionUtils.isEmpty(list)) {
            list.forEach(u -> urls.add(URL.valueOf(u)));
        }
        return urls;
    }

    private static <T> T parseObject(String rawConfig) {
        Constructor constructor = new Constructor(ConfiguratorConfig.class);
        TypeDescription itemDescription = new TypeDescription(ConfiguratorConfig.class);
        itemDescription.addPropertyParameters("items", ConfigItem.class);
        constructor.addTypeDescription(itemDescription);

        Yaml yaml = new Yaml(constructor); //todo @csy-06-22 此处的Yaml功能用途是什么？
        return yaml.load(rawConfig);
    }

    private static List<URL> serviceItemToUrls(ConfigItem item, ConfiguratorConfig config) {
        List<URL> urls = new ArrayList<>();
        List<String> addresses = parseAddresses(item);

        addresses.forEach(addr -> {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("override://").append(addr).append("/");

            urlBuilder.append(appendService(config.getKey()));
            urlBuilder.append(toParameterString(item));

            parseEnabled(item, config, urlBuilder);

            urlBuilder.append("&category=").append(DYNAMIC_CONFIGURATORS_CATEGORY);
            urlBuilder.append("&configVersion=").append(config.getConfigVersion());

            List<String> apps = item.getApplications();
            if (CollectionUtils.isNotEmpty(apps)) {
                apps.forEach(app -> urls.add(URL.valueOf(urlBuilder.append("&application=").append(app).toString())));
            } else {
                urls.add(URL.valueOf(urlBuilder.toString()));
            }
        });

        return urls;
    }

    private static List<URL> appItemToUrls(ConfigItem item, ConfiguratorConfig config) { //将应用配置项转换为对应的url列表
        List<URL> urls = new ArrayList<>();
        List<String> addresses = parseAddresses(item);
        for (String addr : addresses) {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("override://").append(addr).append("/"); //构建override协议
            List<String> services = item.getServices(); //获取配置项中的服务列表
            if (services == null) {
                services = new ArrayList<>();
            }
            if (services.isEmpty()) { //服务列表为空，则使用"*"代替，表明所有服务都支持
                services.add("*");
            }
            for (String s : services) {
                urlBuilder.append(appendService(s));
                urlBuilder.append(toParameterString(item));

                urlBuilder.append("&application=").append(config.getKey());

                parseEnabled(item, config, urlBuilder);

                urlBuilder.append("&category=").append(APP_DYNAMIC_CONFIGURATORS_CATEGORY);
                urlBuilder.append("&configVersion=").append(config.getConfigVersion());

                urls.add(URL.valueOf(urlBuilder.toString()));
            }
        }
        return urls;
    }

    private static String toParameterString(ConfigItem item) { //将配置项参数转换为字符串
        StringBuilder sb = new StringBuilder();
        sb.append("category=");
        sb.append(DYNAMIC_CONFIGURATORS_CATEGORY);
        if (item.getSide() != null) {
            sb.append("&side=");
            sb.append(item.getSide());
        }
        Map<String, String> parameters = item.getParameters();
        if (CollectionUtils.isEmptyMap(parameters)) {
            throw new IllegalStateException("Invalid configurator rule, please specify at least one parameter " +
                    "you want to change in the rule.");
        }

        parameters.forEach((k, v) -> { //循环拼接参数信息
            sb.append("&");
            sb.append(k);
            sb.append("=");
            sb.append(v);
        });

        if (CollectionUtils.isNotEmpty(item.getProviderAddresses())) {
            sb.append("&");
            sb.append(OVERRIDE_PROVIDERS_KEY); //拼接提供者地址信息
            sb.append("=");
            sb.append(CollectionUtils.join(item.getProviderAddresses(), ","));
        }

        return sb.toString();
    }

    private static String appendService(String serviceKey) { //附加服务信息
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isEmpty(serviceKey)) {
            throw new IllegalStateException("service field in configuration is null.");
        }

        String interfaceName = serviceKey;
        int i = interfaceName.indexOf('/');
        if (i > 0) {
            sb.append("group=");
            sb.append(interfaceName, 0, i); //将服务对应的接口名拼接
            sb.append("&");

            interfaceName = interfaceName.substring(i + 1);
        }
        int j = interfaceName.indexOf(':');
        if (j > 0) {
            sb.append("version=");
            sb.append(interfaceName.substring(j + 1));
            sb.append("&");
            interfaceName = interfaceName.substring(0, j);
        }
        sb.insert(0, interfaceName + "?");

        return sb.toString();
    }

    private static void parseEnabled(ConfigItem item, ConfiguratorConfig config, StringBuilder urlBuilder) {
        urlBuilder.append("&enabled=");
        if (item.getType() == null || ConfigItem.GENERAL_TYPE.equals(item.getType())) {
            urlBuilder.append(config.getEnabled());
        } else {
            urlBuilder.append(item.getEnabled());
        }
    }

    private static List<String> parseAddresses(ConfigItem item) {
        List<String> addresses = item.getAddresses();
        if (addresses == null) {
            addresses = new ArrayList<>();
        }
        if (addresses.isEmpty()) {
            addresses.add(ANYHOST_VALUE);
        }
        return addresses;
    }

    private static boolean isJsonArray(String rawConfig) { //校验Json字符串是否是数组类型
        try {
            JSONValidator validator = JSONValidator.from(rawConfig);
            return validator.validate() && validator.getType() == JSONValidator.Type.Array;
        } catch (Exception e) {
            // ignore exception and return false
        }
        return false;
    }
}
