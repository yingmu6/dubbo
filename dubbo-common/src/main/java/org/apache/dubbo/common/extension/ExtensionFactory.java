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
package org.apache.dubbo.common.extension;

/**
 * ExtensionFactory
 */
@SPI
public interface ExtensionFactory {
    /**
     * @csy-003 扩展工厂是何时使用的？为啥默认扩展是SpiExtensionFactory？
     * ExtensionFactory的实现类AdaptiveExtensionFactory是带有@Adaptive注解的
     * ExtensionLoader中getExtensionLoader创建扩展加载器时，会获取ExtensionFactory getAdaptiveExtension()自适应类，即为AdaptiveExtensionFactory实例
     * 而AdaptiveExtensionFactory创建时会设置所有可支持的实例，然后依次尝试去获取实例，那个工厂能获取到实例，就用对应实例
     * 解：1）可通过扩展工厂获取指定类型、名称的扩展实例
     *    2）不是SpiExtensionFactory，是遍历扩展工厂列表的
     */

    /**
     * Get extension.
     *
     * @param type object type.
     * @param name object name.
     * @return object instance.
     */
    <T> T getExtension(Class<T> type, String name);

}
