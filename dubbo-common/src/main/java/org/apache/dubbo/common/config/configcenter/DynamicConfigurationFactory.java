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
         * 1）此处获取ExtensionLoader<DynamicConfigurationFactory>实例时，缓存中已经有对应实例了，不需要再加载SPI文件了
         * 2）
         * 3）
         *
         * 问题点答疑：
         * 1）为什么加载不了 dubbo-configcenter-apollo、dubbo-configcengter-nacos等目录下的配置文件，而dubbo-configcenter-zookeeper却能加载？
         */
        return loader.getOrDefaultExtension(name);
    }
}
