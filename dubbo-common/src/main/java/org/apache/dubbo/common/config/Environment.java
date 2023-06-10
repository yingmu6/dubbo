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

import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.context.LifecycleAdapter;
import org.apache.dubbo.common.extension.DisableInject;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.ConfigCenterConfig;
import org.apache.dubbo.config.context.ConfigConfigurationAdapter;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Environment类是Dubbo的环境信息类，主要的作用是加载配置信息，从配置文件中获取系统参数，从外部配置中心加载配置信息等。
 * https://blog.csdn.net/leisurelen/article/details/107317951
 */
public class Environment extends LifecycleAdapter implements FrameworkExt { //环境信息

    /**
     * Environment也是存储配置信息，与ConfigManager不同的是，
     * Environment主要处理的与系统配置相关，比如Java系统配置，以及配置中心的配置。
     */

    public static final String NAME = "environment";

    private final PropertiesConfiguration propertiesConfiguration; //装载"dubbo.properties"文件的配置信息
    private final SystemConfiguration systemConfiguration;         //装载System的properties配置系信息
    private final EnvironmentConfiguration environmentConfiguration;//装载JVM环境变量的配置信息
    private final InmemoryConfiguration externalConfiguration;      //装载内部的配置信息，分为全局配置和应用级配置
    private final InmemoryConfiguration appExternalConfiguration;   //装载配置中心的配置信息

    private CompositeConfiguration globalConfiguration; //合成的配置信息

    private Map<String, String> externalConfigurationMap = new HashMap<>(); //（额外的配置信息）从配置中心拉取的未按group隔离的配置内容
    private Map<String, String> appExternalConfigurationMap = new HashMap<>(); //按应用名做group隔离的配置内容

    private boolean configCenterFirst = true;

    private DynamicConfiguration dynamicConfiguration; //动态配置实例

    public Environment() { //对象创建时，初始化成员变量
        this.propertiesConfiguration = new PropertiesConfiguration();
        this.systemConfiguration = new SystemConfiguration();
        this.environmentConfiguration = new EnvironmentConfiguration();
        this.externalConfiguration = new InmemoryConfiguration();
        this.appExternalConfiguration = new InmemoryConfiguration();
    }

    @Override
    public void initialize() throws IllegalStateException { //initialize：[ɪˈnɪʃəlaɪz]： 初始化，对当前对象的属性进行初始化
        ConfigManager configManager = ApplicationModel.getConfigManager(); //通过SPI机制获取对象实例
        Optional<Collection<ConfigCenterConfig>> defaultConfigs = configManager.getDefaultConfigCenter();
        defaultConfigs.ifPresent(configs -> { //ifPresent：若值存在时，带着值执行对应的动作，否则什么都不做
            for (ConfigCenterConfig config : configs) {
                this.setExternalConfigMap(config.getExternalConfiguration());
                this.setAppExternalConfigMap(config.getAppExternalConfiguration());
            }
        });

        this.externalConfiguration.setProperties(externalConfigurationMap);
        this.appExternalConfiguration.setProperties(appExternalConfigurationMap);
    }

    @DisableInject
    public void setExternalConfigMap(Map<String, String> externalConfiguration) {
        if (externalConfiguration != null) {
            this.externalConfigurationMap = externalConfiguration;
        }
    }

    @DisableInject
    public void setAppExternalConfigMap(Map<String, String> appExternalConfiguration) {
        if (appExternalConfiguration != null) {
            this.appExternalConfigurationMap = appExternalConfiguration;
        }
    }

    public Map<String, String> getExternalConfigurationMap() {
        return externalConfigurationMap;
    }

    public Map<String, String> getAppExternalConfigurationMap() {
        return appExternalConfigurationMap;
    }

    public void updateExternalConfigurationMap(Map<String, String> externalMap) {
        this.externalConfigurationMap.putAll(externalMap);
    }

    public void updateAppExternalConfigurationMap(Map<String, String> externalMap) {
        this.appExternalConfigurationMap.putAll(externalMap);
    }

    /**
     * At start-up, Dubbo is driven by various（各种各样的） configuration, such as Application, Registry, Protocol, etc.
     * All configurations will be converged（被聚集） into a data bus - URL, and then drive the subsequent（随后的） process. //在启动时，各种配置会被聚集到数据总线URL中，给后面的程序使用
     * <p>
     * At present（目前）, there are many configuration sources, including AbstractConfig (API, XML, annotation), - D, config center, etc.
     * This method helps us to filter out the most priority values from various configuration sources. //配置的数据源有许多，比如：配置对象、JVM输入参数、配置中心等，该方法就是过滤出最高优先级的配
     *
     * @param config
     * @return
     */
    public synchronized CompositeConfiguration getPrefixedConfiguration(AbstractConfig config) { //获取Config对应的合成配置，prefixed [ˈpriːfɪkst] adj. 有前缀的, v. 加……作为前缀；
        CompositeConfiguration prefixedConfiguration = new CompositeConfiguration(config.getPrefix(), config.getId());
        Configuration configuration = new ConfigConfigurationAdapter(config); //AbstractConfig对应的配置对象的实例
        if (this.isConfigCenterFirst()) { //在CompositeConfiguration#getInternalProperty进行取值时，会依次遍历列表中的配置对象的实例，越靠前的配置，越先获取到配置值。
            // The sequence would be: SystemConfiguration -> AppExternalConfiguration -> ExternalConfiguration -> AbstractConfig -> PropertiesConfiguration
            // Config center has the highest priority
            prefixedConfiguration.addConfiguration(systemConfiguration); //systemConfiguration、environmentConfiguration等对象，在Environment构造函数中初始化的
            prefixedConfiguration.addConfiguration(environmentConfiguration);
            prefixedConfiguration.addConfiguration(appExternalConfiguration);
            prefixedConfiguration.addConfiguration(externalConfiguration);
            prefixedConfiguration.addConfiguration(configuration);
            prefixedConfiguration.addConfiguration(propertiesConfiguration);
        } else {
            // The sequence would be: SystemConfiguration -> AbstractConfig -> AppExternalConfiguration -> ExternalConfiguration -> PropertiesConfiguration
            // Config center has the highest priority（配置中心有最高优先级）
            prefixedConfiguration.addConfiguration(systemConfiguration);
            prefixedConfiguration.addConfiguration(environmentConfiguration);
            prefixedConfiguration.addConfiguration(configuration); //相比上面，配置信息加载的位置不一样
            prefixedConfiguration.addConfiguration(appExternalConfiguration);
            prefixedConfiguration.addConfiguration(externalConfiguration);
            prefixedConfiguration.addConfiguration(propertiesConfiguration);
        }
        return prefixedConfiguration;
    }

     /**
     * There are two ways to get configuration during exposure（暴露） / reference or at runtime:
     * 1. URL, The value in the URL is relatively fixed（相对固定的）. we can get value directly.
     * 2. The configuration exposed in this method is convenient（方便的） for us to query the latest values from multiple
     * prioritized sources, it also guarantees that configs changed dynamically can take effect on the fly.（它还保证了动态更改的配置可以即时生效）
     */
    public Configuration getConfiguration() {
        if (globalConfiguration == null) {
            globalConfiguration = new CompositeConfiguration();
            if (dynamicConfiguration != null) {
                globalConfiguration.addConfiguration(dynamicConfiguration); //设置动态配置（将动态配置放在第一个位置，可以保证动态更改的配置及时生效）
            }
            globalConfiguration.addConfiguration(systemConfiguration);
            globalConfiguration.addConfiguration(environmentConfiguration);
            globalConfiguration.addConfiguration(appExternalConfiguration);
            globalConfiguration.addConfiguration(externalConfiguration);
            globalConfiguration.addConfiguration(propertiesConfiguration);
        }
        return globalConfiguration;
    }

    public boolean isConfigCenterFirst() {
        return configCenterFirst;
    }

    @DisableInject
    public void setConfigCenterFirst(boolean configCenterFirst) {
        this.configCenterFirst = configCenterFirst;
    }

    public Optional<DynamicConfiguration> getDynamicConfiguration() {
        return Optional.ofNullable(dynamicConfiguration);
    }

    @DisableInject
    public void setDynamicConfiguration(DynamicConfiguration dynamicConfiguration) {
        this.dynamicConfiguration = dynamicConfiguration;
    }

    @Override
    public void destroy() throws IllegalStateException {
        clearExternalConfigs();
        clearAppExternalConfigs();
    }

    public PropertiesConfiguration getPropertiesConfiguration() {
        return propertiesConfiguration;
    }

    public SystemConfiguration getSystemConfiguration() {
        return systemConfiguration;
    }

    public EnvironmentConfiguration getEnvironmentConfiguration() {
        return environmentConfiguration;
    }

    public InmemoryConfiguration getExternalConfiguration() {
        return externalConfiguration;
    }

    public InmemoryConfiguration getAppExternalConfiguration() {
        return appExternalConfiguration;
    }

    // For test
    public void clearExternalConfigs() {
        this.externalConfiguration.clear();
        this.externalConfigurationMap.clear();
    }

    // For test
    public void clearAppExternalConfigs() {
        this.appExternalConfiguration.clear();
        this.appExternalConfigurationMap.clear();
    }
}
