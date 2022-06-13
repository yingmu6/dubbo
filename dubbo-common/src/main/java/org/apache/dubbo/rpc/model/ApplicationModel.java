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
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.context.ConfigManager;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link ExtensionLoader}, {@code DubboBootstrap} （引导程序）and this class are at present designed to be
 * singleton（单例） or static（静态） (by itself totally static or uses some static fields). So the instances
 * returned from them are of process scope. If you want to support multiple dubbo servers in one
 * single process, you may need to refactor（重构） those three classes.
 * <p>
 * Represent a application which is using Dubbo and store basic metadata info for using
 * during the processing of RPC invoking.
 * （代表一个正在使用 Dubbo 的应用程序，并存储基本的元数据信息以供在处理 RPC 调用期间使用）
 * <p>
 * ApplicationModel includes many ProviderModel which is about published services
 * and many Consumer Model which is about subscribed services.
 * （ApplicationModel包含ProviderModel、ConsumerModel模型）
 * <p>
 */

public class ApplicationModel { //应用的数据模型
    protected static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModel.class);
    public static final String NAME = "application";

    private static AtomicBoolean INIT_FLAG = new AtomicBoolean(false);

    public static void init() {
        if (INIT_FLAG.compareAndSet(false, true)) { //在未初始化时，进行初始化
            ExtensionLoader<ApplicationInitListener> extensionLoader = ExtensionLoader.getExtensionLoader(ApplicationInitListener.class);
            Set<String> listenerNames = extensionLoader.getSupportedExtensions();
            for (String listenerName : listenerNames) {
                extensionLoader.getExtension(listenerName).init(); //此处的ApplicationInitListener没有实现类，应该是为3.x预留
            }
        }
    }

    public static Collection<ConsumerModel> allConsumerModels() { //通过ServiceRepository服务仓库，来管理ProviderModel、ConsumerModel
        return getServiceRepository().getReferredServices();
    }

    public static Collection<ProviderModel> allProviderModels() {
        return getServiceRepository().getExportedServices();
    }

    public static ProviderModel getProviderModel(String serviceKey) {
        return getServiceRepository().lookupExportedService(serviceKey);
    }

    public static ConsumerModel getConsumerModel(String serviceKey) {
        return getServiceRepository().lookupReferredService(serviceKey);
    }

    private static final ExtensionLoader<FrameworkExt> LOADER = ExtensionLoader.getExtensionLoader(FrameworkExt.class);

    public static void initFrameworkExts() {
        Set<FrameworkExt> exts = ExtensionLoader.getExtensionLoader(FrameworkExt.class).getSupportedExtensionInstances();
        for (FrameworkExt ext : exts) {
            ext.initialize(); //调用FrameworkExt所有扩展实例的initialize初始化方法，目前主要是Environment中的initialize方法
        }
    }

    public static Environment getEnvironment() {
        return (Environment) LOADER.getExtension(Environment.NAME); //从org.apache.dubbo.common.context.FrameworkExt配置文件中找到environment对应的扩展实例
    }

    public static ConfigManager getConfigManager() { //通过SPI机制，获取到ConfigManager实例（提供不同的方法，获取不同的扩展实例）
        return (ConfigManager) LOADER.getExtension(ConfigManager.NAME); //此处由于已经明确ConfigManager.NAME扩展名对应的类是ConfigManager，所以可以类型强转
    }

    public static ServiceRepository getServiceRepository() {
        return (ServiceRepository) LOADER.getExtension(ServiceRepository.NAME);
    }

    public static ApplicationConfig getApplicationConfig() {
        return getConfigManager().getApplicationOrElseThrow();
    }

    public static String getName() { //获取应用名
        return getApplicationConfig().getName();
    }

    @Deprecated
    private static String application;

    @Deprecated
    public static String getApplication() {
        return application == null ? getName() : application;
    }

    // Currently used by UT.
    @Deprecated
    public static void setApplication(String application) {
        ApplicationModel.application = application;
    }

    // only for unit test（此处的重置，仅仅用于单元测试使用）
    public static void reset() {
        getServiceRepository().destroy();
        getConfigManager().destroy();
        getEnvironment().destroy();
    }

}
