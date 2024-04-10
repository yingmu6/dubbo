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
package org.apache.dubbo.common.context;

import org.apache.dubbo.common.extension.SPI;

@SPI
public interface FrameworkExt extends Lifecycle { //FrameworkExt:框架，框架的生命周期，（SPI接口，按扩展名查找对应实例）
    /**
     * FrameworkExt的三个实现类：
     * 1）ConfigManager：维护dubbo标签名与Config对象的缓存映射关系
     * 2）Environment：通过加载属性文件，生成属性对象，管理对象的生命周期
     * 3）ServiceRepository：维护接口key与ConsumerModel、ProviderModel模型的映射
     *
     * 虽然FrameworkExt没有声明方法，但它继承了Lifecycle的其中的方法，并以SPI的方式开放出去，支持扩展
     */

    /**
     * 流程分析：org.apache.dubbo.common.context.FrameworkExt配置文件的加载流程
     * 1）FrameworkExt接口有3个实现类，xxx.FrameworkExt之中也配置了3个实现类的key=value键值对
     * 2）在使用时，如ApplicationModel.getConfigManager()、ApplicationModel.getEnvironment()，都是先
     *    获取到FrameworkExt的扩展加载器ExtensionLoader.getExtensionLoader(FrameworkExt.class)，然后再按需获取ConfigManager或Environment实例
     * 3）此处就很好的提现了dubbo的SPI机制，当SPI接口有多个实现类时，是按需创建扩展实例的（加载SPI文件时，是根据ExtensionLoader中的维护的type查找的；
     *    而创建扩展实例或查找扩展实例缓存时，是根据传入的类名做选择的）
     */
}
