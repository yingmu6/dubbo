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

import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.context.LifecycleAdapter;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.*;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Optional.ofNullable;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.utils.ReflectUtils.getProperty;
import static org.apache.dubbo.common.utils.StringUtils.isNotEmpty;
import static org.apache.dubbo.config.AbstractConfig.getTagName;
import static org.apache.dubbo.config.Constants.PROTOCOLS_SUFFIX;
import static org.apache.dubbo.config.Constants.REGISTRIES_SUFFIX;

public class ConfigManager extends LifecycleAdapter implements FrameworkExt { //配置管理器，继承适配器，有选择的实现方法

    /**
     * ConfigManager存储了所有dubbo的配置对象
     * 类似于一个本地的配置中心，如果要查询配置信息，访问ConfigManager获取对应的配置对象即可，任何配置对象修改了，都要刷新ConfigManager
     */

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    public static final String NAME = "config";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    final Map<String, Map<String, AbstractConfig>> configsCache = newMap(); //配置缓存，key为标签名，如ConfigCenterConfig配置类的标签名为config-center

    public ConfigManager() {
    }

    // ApplicationConfig correlative methods

    public void setApplication(ApplicationConfig application) {
        addConfig(application, true);
    }

    public Optional<ApplicationConfig> getApplication() {
        return ofNullable(getConfig(getTagName(ApplicationConfig.class)));
    }

    public ApplicationConfig getApplicationOrElseThrow() { //若没有获取到ApplicationConfig的应用配置信息，则抛出异常
        return getApplication().orElseThrow(() -> new IllegalStateException("There's no ApplicationConfig specified."));
    }

    // MonitorConfig correlative methods

    public void setMonitor(MonitorConfig monitor) {
        addConfig(monitor, true);
    }

    public Optional<MonitorConfig> getMonitor() {
        return ofNullable(getConfig(getTagName(MonitorConfig.class)));
    }

    // ModuleConfig correlative methods

    public void setModule(ModuleConfig module) {
        addConfig(module, true);
    }

    public Optional<ModuleConfig> getModule() {
        return ofNullable(getConfig(getTagName(ModuleConfig.class)));
    }

    public void setMetrics(MetricsConfig metrics) {
        addConfig(metrics, true);
    }

    public Optional<MetricsConfig> getMetrics() {
        return ofNullable(getConfig(getTagName(MetricsConfig.class)));
    }

    public void setSsl(SslConfig sslConfig) {
        addConfig(sslConfig, true);
    }

    public Optional<SslConfig> getSsl() {
        return ofNullable(getConfig(getTagName(SslConfig.class)));
    }

    // ConfigCenterConfig correlative methods

    public void addConfigCenter(ConfigCenterConfig configCenter) {
        addConfig(configCenter);
    }

    public void addConfigCenters(Iterable<ConfigCenterConfig> configCenters) {
        configCenters.forEach(this::addConfigCenter);
    }

    public Optional<Collection<ConfigCenterConfig>> getDefaultConfigCenter() {
        Collection<ConfigCenterConfig> defaults = getDefaultConfigs(getConfigsMap(getTagName(ConfigCenterConfig.class)));
        if (CollectionUtils.isEmpty(defaults)) { //若没有默认配置，则不进行筛选，返回所有配置实例ConfigCenterConfig
            defaults = getConfigCenters();
        }
        return Optional.ofNullable(defaults); //对空值null进行保护，避免空指针
    }

    public ConfigCenterConfig getConfigCenter(String id) {
        return getConfig(getTagName(ConfigCenterConfig.class), id);
    }

    public Collection<ConfigCenterConfig> getConfigCenters() {
        return getConfigs(getTagName(ConfigCenterConfig.class));
    }

    // MetadataReportConfig correlative methods

    public void addMetadataReport(MetadataReportConfig metadataReportConfig) {
        addConfig(metadataReportConfig);
    }

    public void addMetadataReports(Iterable<MetadataReportConfig> metadataReportConfigs) {
        metadataReportConfigs.forEach(this::addMetadataReport);
    }

    public Collection<MetadataReportConfig> getMetadataConfigs() {
        return getConfigs(getTagName(MetadataReportConfig.class));
    }

    // MetadataReportConfig correlative methods

    public void addProvider(ProviderConfig providerConfig) {
        addConfig(providerConfig);
    }

    public void addProviders(Iterable<ProviderConfig> providerConfigs) {
        providerConfigs.forEach(this::addProvider);
    }

    public Optional<ProviderConfig> getProvider(String id) {
        return ofNullable(getConfig(getTagName(ProviderConfig.class), id));
    }

    /**
     * Only allows one default ProviderConfig
     */
    public Optional<ProviderConfig> getDefaultProvider() {
        List<ProviderConfig> providerConfigs = getDefaultConfigs(getConfigsMap(getTagName(ProviderConfig.class)));
        if (CollectionUtils.isNotEmpty(providerConfigs)) {
            return Optional.of(providerConfigs.get(0));
        }
        return Optional.empty();
    }

    public Collection<ProviderConfig> getProviders() {
        return getConfigs(getTagName(ProviderConfig.class));
    }

    // ConsumerConfig correlative methods

    public void addConsumer(ConsumerConfig consumerConfig) {
        addConfig(consumerConfig);
    }

    public void addConsumers(Iterable<ConsumerConfig> consumerConfigs) {
        consumerConfigs.forEach(this::addConsumer);
    }

    public Optional<ConsumerConfig> getConsumer(String id) {
        return ofNullable(getConfig(getTagName(ConsumerConfig.class), id));
    }

    /**
     * Only allows one default ConsumerConfig
     */
    public Optional<ConsumerConfig> getDefaultConsumer() {
        List<ConsumerConfig> consumerConfigs = getDefaultConfigs(getConfigsMap(getTagName(ConsumerConfig.class)));
        if (CollectionUtils.isNotEmpty(consumerConfigs)) {
            return Optional.of(consumerConfigs.get(0));
        }
        return Optional.empty();
    }

    public Collection<ConsumerConfig> getConsumers() {
        return getConfigs(getTagName(ConsumerConfig.class));
    }

    // ProtocolConfig correlative methods

    public void addProtocol(ProtocolConfig protocolConfig) {
        addConfig(protocolConfig);
    }

    public void addProtocols(Iterable<ProtocolConfig> protocolConfigs) {
        if (protocolConfigs != null) {
            protocolConfigs.forEach(this::addProtocol);
        }
    }

    public Optional<ProtocolConfig> getProtocol(String id) {
        return ofNullable(getConfig(getTagName(ProtocolConfig.class), id));
    }

    public List<ProtocolConfig> getDefaultProtocols() {
        return getDefaultConfigs(getConfigsMap(getTagName(ProtocolConfig.class)));
    }

    public Collection<ProtocolConfig> getProtocols() {
        return getConfigs(getTagName(ProtocolConfig.class));
    }

    public Set<String> getProtocolIds() {
        Set<String> protocolIds = new HashSet<>();
        protocolIds.addAll(getSubProperties(ApplicationModel.getEnvironment()
                .getExternalConfigurationMap(), PROTOCOLS_SUFFIX));
        protocolIds.addAll(getSubProperties(ApplicationModel.getEnvironment()
                .getAppExternalConfigurationMap(), PROTOCOLS_SUFFIX));

        return unmodifiableSet(protocolIds);
    }


    // RegistryConfig correlative methods

    public void addRegistry(RegistryConfig registryConfig) {
        addConfig(registryConfig);
    }

    public void addRegistries(Iterable<RegistryConfig> registryConfigs) {
        if (registryConfigs != null) {
            registryConfigs.forEach(this::addRegistry);
        }
    }

    public Optional<RegistryConfig> getRegistry(String id) {
        return ofNullable(getConfig(getTagName(RegistryConfig.class), id));
    }

    public List<RegistryConfig> getDefaultRegistries() { //先获取标签registry对应缓存map值，然后再获取配置config列表
        return getDefaultConfigs(getConfigsMap(getTagName(RegistryConfig.class)));
    }

    public Collection<RegistryConfig> getRegistries() {
        return getConfigs(getTagName(RegistryConfig.class));
    }

    public Set<String> getRegistryIds() {
        Set<String> registryIds = new HashSet<>();
        registryIds.addAll(getSubProperties(ApplicationModel.getEnvironment().getExternalConfigurationMap(),
                REGISTRIES_SUFFIX));
        registryIds.addAll(getSubProperties(ApplicationModel.getEnvironment().getAppExternalConfigurationMap(),
                REGISTRIES_SUFFIX));

        return unmodifiableSet(registryIds);
    }

    // ServiceConfig correlative methods

    public void addService(ServiceConfigBase<?> serviceConfig) {
        addConfig(serviceConfig);
    }

    public void addServices(Iterable<ServiceConfigBase<?>> serviceConfigs) {
        serviceConfigs.forEach(this::addService);
    }

    public Collection<ServiceConfigBase> getServices() {
        return getConfigs(getTagName(ServiceConfigBase.class));
    }

    public <T> ServiceConfigBase<T> getService(String id) {
        return getConfig(getTagName(ServiceConfigBase.class), id);
    }

    // ReferenceConfig correlative methods

    public void addReference(ReferenceConfigBase<?> referenceConfig) {
        addConfig(referenceConfig);
    }

    public void addReferences(Iterable<ReferenceConfigBase<?>> referenceConfigs) {
        referenceConfigs.forEach(this::addReference);
    }

    public Collection<ReferenceConfigBase<?>> getReferences() {
        return getConfigs(getTagName(ReferenceConfigBase.class));
    }

    public <T> ReferenceConfigBase<T> getReference(String id) {
        return getConfig(getTagName(ReferenceConfigBase.class), id);
    }

    protected static Set<String> getSubProperties(Map<String, String> properties, String prefix) {
        return properties.keySet().stream().filter(k -> k.contains(prefix)).map(k -> { //若key包含指定的前缀，则对key进行处理
            k = k.substring(prefix.length());
            return k.substring(0, k.indexOf(".")); //将属性map的key进行遍历，去掉前缀、再去掉点号
        }).collect(Collectors.toSet());
    }

    public void refreshAll() {
        write(() -> {
            // refresh all configs here,
            getApplication().ifPresent(ApplicationConfig::refresh);
            getMonitor().ifPresent(MonitorConfig::refresh);
            getModule().ifPresent(ModuleConfig::refresh);

            getProtocols().forEach(ProtocolConfig::refresh);
            getRegistries().forEach(RegistryConfig::refresh);
            getProviders().forEach(ProviderConfig::refresh);
            getConsumers().forEach(ConsumerConfig::refresh);
        });

    }

    /**
     * In some scenario,  we may nee to add and remove ServiceConfig or ReferenceConfig dynamically.
     *
     * @param config the config instance to remove.
     */
    public void removeConfig(AbstractConfig config) {
        if (config == null) {
            return;
        }

        Map<String, AbstractConfig> configs = configsCache.get(getTagName(config.getClass()));
        if (CollectionUtils.isNotEmptyMap(configs)) {
            configs.remove(getId(config));
        }
    }

    public void clear() {
        write(() -> {
            this.configsCache.clear();
        });
    }

    /**
     * @throws IllegalStateException
     * @since 2.7.8
     */
    @Override
    public void destroy() throws IllegalStateException {
        clear();
    }

    /**
     * Add the dubbo {@link AbstractConfig config}
     *
     * @param config the dubbo {@link AbstractConfig config}
     */
    public void addConfig(AbstractConfig config) {
        addConfig(config, false);
    }

    protected void addConfig(AbstractConfig config, boolean unique) {
        if (config == null) {
            return;
        }
        write(() -> { //线程执行体
            //computeIfAbsent:判断key对应的value是否存在，不存在是则设置，computeIfAbsent()第2个参数是函数式式接口，进行函数传递；type是入参，newMap()执行方法，并返回值，此处返回new HashMap<>()
            Map<String, AbstractConfig> configsMap = configsCache.computeIfAbsent(getTagName(config.getClass()), type -> newMap()); //configsMap的可以为新加入的config标签名，而值是new HashMap<>()，此处还没计算
            addIfAbsent(config, configsMap, unique);
        });
    }

    protected <C extends AbstractConfig> Map<String, C> getConfigsMap(String configType) { //configType如：config-center，因为AbstractConfig有许多实例，所以返回值使用泛型
        return (Map<String, C>) read(() -> configsCache.getOrDefault(configType, emptyMap())); //getOrDefault()：从map中获取指定key的值，若值为空，取传入的默认值
    }

    protected <C extends AbstractConfig> Collection<C> getConfigs(String configType) { //获取值的集合，比如当暴露多个服务是，configMap中的"service"就会对应多个值，所以返回的是值列表
        return (Collection<C>) read(() -> getConfigsMap(configType).values()); //加锁读取configMap中的值，因为这个是公共资源，可能其它线程正在做写入操作，确保线程安全
    }

    protected <C extends AbstractConfig> C getConfig(String configType, String id) {
        return read(() -> {
            Map<String, C> configsMap = (Map) configsCache.getOrDefault(configType, emptyMap());
            return configsMap.get(id);
        });
    }

    protected <C extends AbstractConfig> C getConfig(String configType) throws IllegalStateException {
        return read(() -> {
            Map<String, C> configsMap = (Map) configsCache.getOrDefault(configType, emptyMap());
            int size = configsMap.size();
            if (size < 1) {
//                throw new IllegalStateException("No such " + configType.getName() + " is found");
                return null;
            } else if (size > 1) {
                logger.warn("Expected single matching of " + configType + ", but found " + size + " instances, will randomly pick the first one.");
            }

            return configsMap.values().iterator().next();
        });
    }

    private <V> V write(Callable<V> callable) {
        V value = null;
        Lock writeLock = lock.writeLock();
        try {
            writeLock.lock();
            value = callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e.getCause());
        } finally {
            writeLock.unlock();
        }
        return value;
    }

    private void write(Runnable runnable) {
        write(() -> {
            runnable.run();
            return null;
        });
    }

    private <V> V read(Callable<V> callable) { //开启线程读取配置，并在处理时加锁
        Lock readLock = lock.readLock();
        V value = null;
        try {
            readLock.lock(); //加锁
            value = callable.call(); //对执行的过程进行加锁
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            readLock.unlock(); //解锁，要放在finally执行
        }
        return value;
    }

    private static void checkDuplicate(AbstractConfig oldOne, AbstractConfig newOne) throws IllegalStateException {
        if (oldOne != null && !oldOne.equals(newOne)) { //检查配置是否重复
            String configName = oldOne.getClass().getSimpleName();
            logger.warn("Duplicate Config found for " + configName + ", you should use only one unique " + configName + " for one application.");
        }
    }

    private static Map newMap() {
        return new HashMap<>();
    }

    static <C extends AbstractConfig> void addIfAbsent(C config, Map<String, C> configsMap, boolean unique)
            throws IllegalStateException {

        if (config == null || configsMap == null) {
            return;
        }

        if (unique) { // check duplicate
            configsMap.values().forEach(c -> { //判断要加入的config是否已经在缓存中，若已存在，则给出提示（warn日志，只提示不抛出异常）
                checkDuplicate(c, config);
            });
        }

        String key = getId(config);

        C existedConfig = configsMap.get(key);

        if (existedConfig != null && !config.equals(existedConfig)) {
            if (logger.isWarnEnabled()) {
                String type = config.getClass().getSimpleName();
                logger.warn(String.format("Duplicate %s found, there already has one default %s or more than two %ss have the same id, " +
                        "you can try to give each %s a different id : %s", type, type, type, type, config));
            }
        } else {
            configsMap.put(key, config); //设置Map的值
        }
    }

    static <C extends AbstractConfig> String getId(C config) { //取config对象的id值
        String id = config.getId();
        return isNotEmpty(id) ? id : isDefaultConfig(config) ?
                config.getClass().getSimpleName() + "#" + DEFAULT_KEY : null;
    }

    static <C extends AbstractConfig> boolean isDefaultConfig(C config) {
        Boolean isDefault = getProperty(config, "isDefault");
        return isDefault == null || TRUE.equals(isDefault); //若不包含isDefault属性或isDefault属性值为true，则为默认配置（反义：包含isDefault属性，且为false）
    }

    static <C extends AbstractConfig> List<C> getDefaultConfigs(Map<String, C> configsMap) { //对map中的值列表进行过滤，configsMap值如<类的全路径名：对象实例>=<"org.apache.dubbo.config.spring.ConfigCenterBean", ConfigCenterBean@3154>
        return configsMap.values()
                .stream()
                .filter(ConfigManager::isDefaultConfig) //Predicate: 谓语
                .collect(Collectors.toList());
    }
}
