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
package org.apache.dubbo.common.config.configcenter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;

/**
 * The factory interface to create the instance of {@link DynamicConfiguration}
 */
@SPI("nop") // 2.7.5 change the default SPI implementation
public interface DynamicConfigurationFactory {

    DynamicConfiguration getDynamicConfiguration(URL url);

    /**
     * Get an instance of {@link DynamicConfigurationFactory} by the specified name. If not found, take the default
     * extension of {@link DynamicConfigurationFactory}
     *
     * @param name the name of extension of {@link DynamicConfigurationFactory}
     * @return non-null
     * @see 2.7.4
     */
    static DynamicConfigurationFactory getDynamicConfigurationFactory(String name) { //通过SPI方式，获取当前接口DynamicConfigurationFactory的实例
        Class<DynamicConfigurationFactory> factoryClass = DynamicConfigurationFactory.class;
        ExtensionLoader<DynamicConfigurationFactory> loader = getExtensionLoader(factoryClass); //获取扩展加载器ExtensionLoader
        /**
         * 流程分析：SPI配置文件org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory的加载流程
         * 1）DubboBootstrap#startConfigCenter()启动配置中心时，会校验是否做了配置中心的设置，若没有判断校验注册中心是否可以做配置中心
         * 2）加载依赖模块下的所有xxx.DynamicConfigurationFactory的SPI配置文件，判断注册中心对应的协议名是否在配置中心的扩展名中，若在则根据注册中心信息构建配置中心
         * 3）用构建好的配置中心信息，进行远程连接测试，在DubboBootstrap#prepareEnvironment中，可以获取到缓存中DynamicConfigurationFactory实例
         *
         * 问题点答疑：
         * 1）为什么在进入DubboBootstrap#prepareEnvironment方法前，ExtensionLoader<DynamicConfigurationFactory>实例已经在缓存中存在了？
         *    解答：因为在此之前，由于没有设置配置中心，就会用注册中心作为配置中心，所以就会检测加载DynamicConfigurationFactory对应的SPI文件，
         *         真实判断注册中心是否是对应的扩展实例，判断的过程中已经存入缓存了。
         * 2）为什么加载不了dubbo-configcenter-apollo、dubbo-configcengter-nacos等目录下的配置文件，而dubbo-configcenter-zookeeper却能加载？
         *    解答：需要看启动入口，在测试时使用的启动入口是dubbo-demo-xml模块下的ProviderApplication启动类，而该模块原先只依赖dubbo-configcenter-zookeeper、
         *         dubbo-configcenter-nacos两个模块，所以只会引入这两个模块下的META-INF/dubbo等文件，要想引入其它模块，maven添加对应依赖即可。
         */
        return loader.getOrDefaultExtension(name);
    }
}
