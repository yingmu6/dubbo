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
package org.apache.dubbo.config.utils;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.common.status.StatusChecker;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.utils.*;
import org.apache.dubbo.config.*;
import org.apache.dubbo.monitor.MonitorFactory;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Dispatcher;
import org.apache.dubbo.remoting.Transporter;
import org.apache.dubbo.remoting.exchange.Exchanger;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.rpc.ExporterListener;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.InvokerListener;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.support.MockInvoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.common.constants.RegistryConstants.*;
import static org.apache.dubbo.common.constants.RemotingConstants.BACKUP_KEY;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.utils.UrlUtils.isServiceDiscoveryRegistryType;
import static org.apache.dubbo.config.Constants.*;
import static org.apache.dubbo.monitor.Constants.LOGSTAT_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTER_KEY;
import static org.apache.dubbo.registry.Constants.*;
import static org.apache.dubbo.remoting.Constants.*;
import static org.apache.dubbo.rpc.Constants.*;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

public class ConfigValidationUtils {
    private static final Logger logger = LoggerFactory.getLogger(ConfigValidationUtils.class);
    /**
     * The maximum length of a <b>parameter's value</b>
     */
    private static final int MAX_LENGTH = 200;

    /**
     * The maximum length of a <b>path</b>
     */
    private static final int MAX_PATH_LENGTH = 200;

    /**
     * 正则表达式：
     * 1）正则表达式，又称规则表达式（英语：Regular Expression，在代码中常简写为regex、regexp或RE）计算机科学的一个概念。
     *    正则表达式通常被用来检索、替换那些符合某个模式(规则)的文本。许多程序设计语言都支持利用正则表达式进行字符串操作。
     * 2）正则表达式是对字符串操作的一种逻辑公式，就是用事先定义好的一些特定字符、及这些特定字符的组合，组成一个“规则字符串”，这个“规则字符串”用来表达对字符串的一种过滤逻辑
     * 3）正则表达式由一些普通字符和一些元字符（metacharacters）组成。普通字符包括大小写的字母和数字，而元字符则具有特殊的含义。可查看对照表
     *
     * 百科：https://baike.baidu.com/item/%E6%AD%A3%E5%88%99%E8%A1%A8%E8%BE%BE%E5%BC%8F/1700215
     * 对照表：https://www.cnblogs.com/chenjfblog/p/7839431.html
     * 在线测试：https://tool.oschina.net/regex/
     */

    /**
     * The rule qualification for <b>name</b>
     */
    private static final Pattern PATTERN_NAME = Pattern.compile("[\\-._0-9a-zA-Z]+");

    /**
     * The rule qualification for <b>multiply name</b>
     */
    private static final Pattern PATTERN_MULTI_NAME = Pattern.compile("[,\\-._0-9a-zA-Z]+"); //带上分隔符"," 进行匹配

    /**
     * The rule qualification for <b>method names</b>
     */
    private static final Pattern PATTERN_METHOD_NAME = Pattern.compile("[a-zA-Z][0-9a-zA-Z]*");

    /**
     * The rule qualification for <b>path</b>
     */
    private static final Pattern PATTERN_PATH = Pattern.compile("[/\\-$._0-9a-zA-Z]+");

    /**
     * The pattern matches a value who has a symbol
     */
    private static final Pattern PATTERN_NAME_HAS_SYMBOL = Pattern.compile("[:*,\\s/\\-._0-9a-zA-Z]+");

    /**
     * The pattern matches a property key
     */
    private static final Pattern PATTERN_KEY = Pattern.compile("[*,\\-._0-9a-zA-Z]+");


    public static List<URL> loadRegistries(AbstractInterfaceConfig interfaceConfig, boolean provider) { //构造注册URL
        // check && override if necessary
        List<URL> registryList = new ArrayList<URL>();
        ApplicationConfig application = interfaceConfig.getApplication();
        List<RegistryConfig> registries = interfaceConfig.getRegistries();
        if (CollectionUtils.isNotEmpty(registries)) {
            for (RegistryConfig config : registries) {
                String address = config.getAddress();
                if (StringUtils.isEmpty(address)) {
                    address = ANYHOST_VALUE;
                }
                if (!RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(address)) { //address值为"N/A"，表明不可使用
                    Map<String, String> map = new HashMap<String, String>();
                    AbstractConfig.appendParameters(map, application); //将ApplicationConfig中的属性值，设置到参数Map中
                    AbstractConfig.appendParameters(map, config); //将RegistryConfig中的属性值，设置到参数Map中
                    map.put(PATH_KEY, RegistryService.class.getName()); //path对应接口名，如：path -> org.apache.dubbo.registry.RegistryService
                    AbstractInterfaceConfig.appendRuntimeParameters(map);
                    if (!map.containsKey(PROTOCOL_KEY)) { //未指定协议时，默认设置为dubbo协议
                        map.put(PROTOCOL_KEY, DUBBO_PROTOCOL);
                    }
                    List<URL> urls = UrlUtils.parseURLs(address, map); //构建注册中心对应的URL实例

                    for (URL url : urls) {

                        url = URLBuilder.from(url)
                                .addParameter(REGISTRY_KEY, url.getProtocol())
                                .setProtocol(extractRegistryType(url)) //提取注册类型："service-discovery-registry"或"registry" （设置注册协议名，如registry://xxx）
                                .build();
                        if ((provider && url.getParameter(REGISTER_KEY, true))
                                || (!provider && url.getParameter(SUBSCRIBE_KEY, true))) {
                            registryList.add(url); //将符合要求的url添加到注册url列表中
                        }
                    }
                }
            }
        }
        return registryList;
    }

    public static URL loadMonitor(AbstractInterfaceConfig interfaceConfig, URL registryURL) { //构建监控中心URL
        Map<String, String> map = new HashMap<String, String>();
        map.put(INTERFACE_KEY, MonitorService.class.getName()); //MonitorService.class.getName()的值如：org.apache.dubbo.monitor.MonitorService
        AbstractInterfaceConfig.appendRuntimeParameters(map); //附加运行参数
        //set ip
        String hostToRegistry = ConfigUtils.getSystemProperty(DUBBO_IP_TO_REGISTRY);
        if (StringUtils.isEmpty(hostToRegistry)) {
            hostToRegistry = NetUtils.getLocalHost();
        } else if (NetUtils.isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" +
                    DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        map.put(REGISTER_IP_KEY, hostToRegistry);

        MonitorConfig monitor = interfaceConfig.getMonitor();
        ApplicationConfig application = interfaceConfig.getApplication();
        AbstractConfig.appendParameters(map, monitor);
        AbstractConfig.appendParameters(map, application);
        String address = null;
        String sysaddress = System.getProperty("dubbo.monitor.address");
        if (sysaddress != null && sysaddress.length() > 0) {
            address = sysaddress;
        } else if (monitor != null) {
            address = monitor.getAddress();
        }
        if (ConfigUtils.isNotEmpty(address)) {
            if (!map.containsKey(PROTOCOL_KEY)) {
                if (getExtensionLoader(MonitorFactory.class).hasExtension(LOGSTAT_PROTOCOL)) {
                    map.put(PROTOCOL_KEY, LOGSTAT_PROTOCOL);
                } else {
                    map.put(PROTOCOL_KEY, DUBBO_PROTOCOL);
                }
            }
            return UrlUtils.parseURL(address, map);
        } else if (monitor != null &&
                (REGISTRY_PROTOCOL.equals(monitor.getProtocol()) || SERVICE_REGISTRY_PROTOCOL.equals(monitor.getProtocol()))
                && registryURL != null) {
            return URLBuilder.from(registryURL)
                    .setProtocol(DUBBO_PROTOCOL)
                    .addParameter(PROTOCOL_KEY, monitor.getProtocol())
                    .addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map))
                    .build();
        }
        return null;
    }

    /**
     * Legitimacy check and setup of local simulated operations. The operations can be a string with Simple operation or
     * (合法性检查和设置本地模拟操作)
     * a classname whose {@link Class} implements a particular function
     *
     * @param interfaceClass for provider side, it is the {@link Class} of the service that will be exported; for consumer
     *                       side, it is the {@link Class} of the remote service interface that will be referenced
     */
    public static void checkMock(Class<?> interfaceClass, AbstractInterfaceConfig config) { //
        String mock = config.getMock();
        if (ConfigUtils.isEmpty(mock)) {
            return;
        }

        String normalizedMock = MockInvoker.normalizeMock(mock);
        if (normalizedMock.startsWith(RETURN_PREFIX)) {
            normalizedMock = normalizedMock.substring(RETURN_PREFIX.length()).trim();
            try {
                //Check whether the mock value is legal, if it is illegal, throw exception
                MockInvoker.parseMockValue(normalizedMock);
            } catch (Exception e) {
                throw new IllegalStateException("Illegal mock return in <dubbo:service/reference ... " +
                        "mock=\"" + mock + "\" />");
            }
        } else if (normalizedMock.startsWith(THROW_PREFIX)) {
            normalizedMock = normalizedMock.substring(THROW_PREFIX.length()).trim(); //字符串去除"throw"
            if (ConfigUtils.isNotEmpty(normalizedMock)) {
                try {
                    //Check whether the mock value is legal
                    MockInvoker.getThrowable(normalizedMock);
                } catch (Exception e) {
                    throw new IllegalStateException("Illegal mock throw in <dubbo:service/reference ... " +
                            "mock=\"" + mock + "\" />");
                }
            }
        } else {
            //Check whether the mock class is a implementation of the interfaceClass, and if it has a default constructor
            MockInvoker.getMockObject(normalizedMock, interfaceClass);
        }
    }

    public static void validateAbstractInterfaceConfig(AbstractInterfaceConfig config) {
        checkName(LOCAL_KEY, config.getLocal());
        checkName("stub", config.getStub());
        checkMultiName("owner", config.getOwner());

        checkExtension(ProxyFactory.class, PROXY_KEY, config.getProxy());
        checkExtension(Cluster.class, CLUSTER_KEY, config.getCluster());
        checkMultiExtension(Filter.class, FILE_KEY, config.getFilter());
        checkNameHasSymbol(LAYER_KEY, config.getLayer());

        List<MethodConfig> methods = config.getMethods();
        if (CollectionUtils.isNotEmpty(methods)) {
            methods.forEach(ConfigValidationUtils::validateMethodConfig);
        }
    }

    public static void validateServiceConfig(ServiceConfig config) { //对ServiceConfig属性的名称、长度、扩展接口等按正则表达式进行校验
        checkKey(VERSION_KEY, config.getVersion());
        checkKey(GROUP_KEY, config.getGroup());
        checkName(TOKEN_KEY, config.getToken());
        checkPathName(PATH_KEY, config.getPath());

        checkMultiExtension(ExporterListener.class, "listener", config.getListener()); //校验属性"listener"，对应的多个扩展是否存在

        validateAbstractInterfaceConfig(config);

        List<RegistryConfig> registries = config.getRegistries();
        if (registries != null) {
            for (RegistryConfig registry : registries) {
                validateRegistryConfig(registry);
            }
        }

        List<ProtocolConfig> protocols = config.getProtocols();
        if (protocols != null) {
            for (ProtocolConfig protocol : protocols) {
                validateProtocolConfig(protocol);
            }
        }

        ProviderConfig providerConfig = config.getProvider();
        if (providerConfig != null) {
            validateProviderConfig(providerConfig);
        }
    }

    public static void validateReferenceConfig(ReferenceConfig config) {
        checkMultiExtension(InvokerListener.class, "listener", config.getListener());
        checkKey(VERSION_KEY, config.getVersion());
        checkKey(GROUP_KEY, config.getGroup());
        checkName(CLIENT_KEY, config.getClient());

        validateAbstractInterfaceConfig(config);

        List<RegistryConfig> registries = config.getRegistries();
        if (registries != null) {
            for (RegistryConfig registry : registries) {
                validateRegistryConfig(registry);
            }
        }

        ConsumerConfig consumerConfig = config.getConsumer();
        if (consumerConfig != null) {
            validateConsumerConfig(consumerConfig);
        }
    }

    public static void validateConfigCenterConfig(ConfigCenterConfig config) {
        if (config != null) {
            checkParameterName(config.getParameters());
        }
    }

    public static void validateApplicationConfig(ApplicationConfig config) {
        if (config == null) {
            return;
        }

        if (!config.isValid()) {
            throw new IllegalStateException("No application config found or it's not a valid config! " +
                    "Please add <dubbo:application name=\"...\" /> to your spring config.");
        }

        // backward compatibility（向后兼容）
        String wait = ConfigUtils.getProperty(SHUTDOWN_WAIT_KEY);
        if (wait != null && wait.trim().length() > 0) {
            System.setProperty(SHUTDOWN_WAIT_KEY, wait.trim());
        } else {
            wait = ConfigUtils.getProperty(SHUTDOWN_WAIT_SECONDS_KEY);
            if (wait != null && wait.trim().length() > 0) {
                System.setProperty(SHUTDOWN_WAIT_SECONDS_KEY, wait.trim());
            }
        }

        checkName(NAME, config.getName());
        checkMultiName(OWNER, config.getOwner());
        checkName(ORGANIZATION, config.getOrganization());
        checkName(ARCHITECTURE, config.getArchitecture());
        checkName(ENVIRONMENT, config.getEnvironment());
        checkParameterName(config.getParameters());
    }

    public static void validateModuleConfig(ModuleConfig config) {
        if (config != null) {
            checkName(NAME, config.getName());
            checkName(OWNER, config.getOwner());
            checkName(ORGANIZATION, config.getOrganization());
        }
    }

    public static void validateMetadataConfig(MetadataReportConfig metadataReportConfig) {
        if (metadataReportConfig == null) {
            return;
        }
    }

    public static void validateMetricsConfig(MetricsConfig metricsConfig) {
        if (metricsConfig == null) {
            return;
        }
    }

    public static void validateSslConfig(SslConfig sslConfig) {
        if (sslConfig == null) {
            return;
        }
    }

    public static void validateMonitorConfig(MonitorConfig config) {
        if (config != null) {
            if (!config.isValid()) {
                logger.info("There's no valid monitor config found, if you want to open monitor statistics for Dubbo, " +
                        "please make sure your monitor is configured properly.");
            }

            checkParameterName(config.getParameters());
        }
    }

    public static void validateProtocolConfig(ProtocolConfig config) {
        if (config != null) {
            String name = config.getName();
            checkName("name", name);
            checkName(HOST_KEY, config.getHost());
            checkPathName("contextpath", config.getContextpath());


            if (DUBBO_PROTOCOL.equals(name)) { //检查dubbo协议关联的扩展接口是否正确
                checkMultiExtension(Codec.class, CODEC_KEY, config.getCodec());
                checkMultiExtension(Serialization.class, SERIALIZATION_KEY, config.getSerialization());
                checkMultiExtension(Transporter.class, SERVER_KEY, config.getServer());
                checkMultiExtension(Transporter.class, CLIENT_KEY, config.getClient());
            }

            checkMultiExtension(TelnetHandler.class, TELNET, config.getTelnet());
            checkMultiExtension(StatusChecker.class, "status", config.getStatus());
            checkExtension(Transporter.class, TRANSPORTER_KEY, config.getTransporter());
            checkExtension(Exchanger.class, EXCHANGER_KEY, config.getExchanger());
            checkExtension(Dispatcher.class, DISPATCHER_KEY, config.getDispatcher());
            checkExtension(Dispatcher.class, "dispather", config.getDispather());
            checkExtension(ThreadPool.class, THREADPOOL_KEY, config.getThreadpool());
        }
    }

    public static void validateProviderConfig(ProviderConfig config) {
        checkPathName(CONTEXTPATH_KEY, config.getContextpath());
        checkExtension(ThreadPool.class, THREADPOOL_KEY, config.getThreadpool());
        checkMultiExtension(TelnetHandler.class, TELNET, config.getTelnet());
        checkMultiExtension(StatusChecker.class, STATUS_KEY, config.getStatus());
        checkExtension(Transporter.class, TRANSPORTER_KEY, config.getTransporter());
        checkExtension(Exchanger.class, EXCHANGER_KEY, config.getExchanger());
    }

    public static void validateConsumerConfig(ConsumerConfig config) {
        if (config == null) {
            return;
        }
    }

    public static void validateRegistryConfig(RegistryConfig config) {
        checkName(PROTOCOL_KEY, config.getProtocol());
        checkName(USERNAME_KEY, config.getUsername());
        checkLength(PASSWORD_KEY, config.getPassword());
        checkPathLength(FILE_KEY, config.getFile());
        checkName(TRANSPORTER_KEY, config.getTransporter());
        checkName(SERVER_KEY, config.getServer());
        checkName(CLIENT_KEY, config.getClient());
        checkParameterName(config.getParameters());
    }

    public static void validateMethodConfig(MethodConfig config) {
        checkExtension(LoadBalance.class, LOADBALANCE_KEY, config.getLoadbalance());
        checkParameterName(config.getParameters());
        checkMethodName("name", config.getName());

        String mock = config.getMock();
        if (StringUtils.isNotEmpty(mock)) { //对Mock配置信息进行校验
            if (mock.startsWith(RETURN_PREFIX) || mock.startsWith(THROW_PREFIX + " ")) {
                checkLength(MOCK_KEY, mock);
            } else if (mock.startsWith(FAIL_PREFIX) || mock.startsWith(FORCE_PREFIX)) {
                checkNameHasSymbol(MOCK_KEY, mock);
            } else {
                checkName(MOCK_KEY, mock);
            }
        }
    }

    private static String extractRegistryType(URL url) {
        return isServiceDiscoveryRegistryType(url) ? SERVICE_REGISTRY_PROTOCOL : REGISTRY_PROTOCOL;
    }

    public static void checkExtension(Class<?> type, String property, String value) {
        checkName(property, value);
        if (StringUtils.isNotEmpty(value)
                && !ExtensionLoader.getExtensionLoader(type).hasExtension(value)) {
            throw new IllegalStateException("No such extension " + value + " for " + property + "/" + type.getName());
        }
    }

    /**
     * Check whether there is a <code>Extension</code> who's name (property) is <code>value</code> (special treatment is
     * required)
     *
     * @param type     The Extension type
     * @param property The extension key
     * @param value    The Extension name
     */
    public static void checkMultiExtension(Class<?> type, String property, String value) {
        checkMultiName(property, value);
        if (StringUtils.isNotEmpty(value)) {
            String[] values = value.split("\\s*[,]+\\s*"); //按分隔符拆分出多个扩展名
            for (String v : values) {
                if (v.startsWith(REMOVE_VALUE_PREFIX)) {
                    v = v.substring(1);
                }
                if (DEFAULT_KEY.equals(v)) {
                    continue;
                }
                if (!ExtensionLoader.getExtensionLoader(type).hasExtension(v)) { //依次判断扩展名是否有对应的扩展
                    throw new IllegalStateException("No such extension " + v + " for " + property + "/" + type.getName());
                }
            }
        }
    }

    public static void checkLength(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, null);
    }

    public static void checkPathLength(String property, String value) {
        checkProperty(property, value, MAX_PATH_LENGTH, null);
    }

    public static void checkName(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_NAME);
    }

    public static void checkNameHasSymbol(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_NAME_HAS_SYMBOL);
    }

    public static void checkKey(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_KEY);
    }

    public static void checkMultiName(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_MULTI_NAME);
    }

    public static void checkPathName(String property, String value) {
        checkProperty(property, value, MAX_PATH_LENGTH, PATTERN_PATH);
    }

    public static void checkMethodName(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_METHOD_NAME);
    }

    public static void checkParameterName(Map<String, String> parameters) {
        if (CollectionUtils.isEmptyMap(parameters)) {
            return;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!entry.getKey().equals(BACKUP_KEY)) {
                checkNameHasSymbol(entry.getKey(), entry.getValue()); //按正则表达式，检查属性值
            }
        }
    }

    /**
     * 检查属性值value是否正确
     */
    public static void checkProperty(String property, String value, int maxlength, Pattern pattern) {
        if (StringUtils.isEmpty(value)) { //若值为空，则不做校验
            return;
        }
        if (value.length() > maxlength) { //检查值value是否超过最大长度
            throw new IllegalStateException("Invalid " + property + "=\"" + value + "\" is longer than " + maxlength);
        }
        if (pattern != null) { //检查值value是否与正则表达式匹配
            Matcher matcher = pattern.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid " + property + "=\"" + value + "\" contains illegal " +
                        "character, only digit, letter, '-', '_' or '.' is legal.");
            }
        }
    }

}
