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

import org.apache.dubbo.common.URL;

import java.lang.annotation.*;

/**
 * Provide helpful information for {@link ExtensionLoader} to inject dependency extension instance.
 *
 * @see ExtensionLoader
 * @see URL
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Adaptive {
    /**
     * Adaptive注解解析器了解？
     * 解：1）在ExtensionLoader#cacheAdaptiveClass()中将@Adaptive对应的Class缓存起来
     *     if (clazz.isAnnotationPresent(Adaptive.class)) {
     *          cacheAdaptiveClass(clazz, overridden);
     *     }
     *    2）在ExtensionLoader#createAdaptiveExtensionClass()中产生自适应代码
     *    3）在Compiler#compile()对产生的自适应代码进行编译，生成对应的Class对象
     *    4）最后通过Class的newInstance()方法，创建自适应代码的实例对象
     */

    /**
     * @csy-011 方法描述的含义是什么？
     * 解：描述的是方法中使用@Adaptive时，获取扩展名的方式
     * 1）从url取注解上声明的key对应的值作为扩展名
     * 2）若都没取到值，去SPI上声明的默认值
     */

    /**
     * Decide which target extension to be injected. The name of the target extension is decided by the parameter passed
     * in the URL, and the parameter names are given by this method.
     * <p>
     * If the specified parameters are not found from {@link URL}, then the default extension will be used for
     * dependency injection (specified in its interface's {@link SPI}).
     * <p>
     * For example, given <code>String[] {"key1", "key2"}</code>:
     * <ol>
     * <li>find parameter 'key1' in URL, use its value as the extension's name</li>
     * <li>try 'key2' for extension's name if 'key1' is not found (or its value is empty) in URL</li>
     * <li>use default extension if 'key2' doesn't exist either</li>
     * <li>otherwise, throw {@link IllegalStateException}</li>
     * </ol>
     * If the parameter names are empty, then a default parameter name is generated from interface's
     * class name with the rule: divide classname from capital char into several parts, and separate the parts with
     * dot '.', for example, for {@code org.apache.dubbo.xxx.YyyInvokerWrapper}, the generated name is
     * <code>String[] {"yyy.invoker.wrapper"}</code>.
     *
     * @return parameter names in URL
     */
    String[] value() default {}; //生成自适应扩展类，然后在方法中选择具体的实例，执行具体实例的方法

    /**
     * @Adaptive中的value是不是指的是url的参数？
     * 解：参考org.apache.dubbo.common.extension.ext1.SimpleExt$Adaptive 自适应代码的处理逻辑
     * 1）去查找扩展名
     *    a）从参数中获取URL，可以是URL参数，也可以是Invoker等对象，最终获取到URL对象
     *    b）取@Adaptive注解中设置的参数值，如@Adaptive({"key1", "key2"})，从左到右，依次尝试获取url中对应的参数值
     *       若没有从url获取到对应值，去@SPI上声明的扩展名，若还没找到扩展名，则抛出Failed to get extensio
     *       获取扩展名的方式，如：String extName = url.getParameter("key1", url.getParameter("key2", "impl1"));
     * 2）根据扩展名获取扩展实例 ExtensionLoader.getExtensionLoader(xxx.SimpleExt.class).getExtension(extName)
     * 3）执行扩展实例的对应方法 extension.xxx()
     */
}