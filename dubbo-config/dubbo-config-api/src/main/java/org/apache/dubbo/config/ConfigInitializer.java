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
package org.apache.dubbo.config;

import org.apache.dubbo.common.extension.SPI;

/**
 * Dynamically add some parameters / check config
 */

@SPI
public interface ConfigInitializer { //todo @csy-007 该接口的用途以及使用场景是啥？没看到实现类，在哪里实现的？
    /**
     * @csy-007 url出现configInitializer://，这是什么协议？解：做配置初始化，出现在org.apache.dubbo.config.ServiceConfig#checkAndUpdateSubConfigs()
     * https://dmsupine.com/2021/05/23/dubbo-fu-wu-dao-chu-yuan-ma/ 服务导出流程中的配置
     * 在Spring容器启动的时候执行了服务导出的过程，dubboBootstrap.start()
     */
    default void initReferConfig(ReferenceConfig referenceConfig) {

    }

    default void initServiceConfig(ServiceConfig serviceConfig) {

    }

}
