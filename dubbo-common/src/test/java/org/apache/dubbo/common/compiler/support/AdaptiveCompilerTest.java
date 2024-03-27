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
package org.apache.dubbo.common.compiler.support;

import org.apache.dubbo.common.compiler.Compiler;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AdaptiveCompilerTest extends JavaCodeTest {

    @Test
    public void testAvailableCompiler() throws Exception {
        AdaptiveCompiler.setDefaultCompiler("jdk");
        AdaptiveCompiler compiler = new AdaptiveCompiler();
        Class<?> clazz = compiler.compile(getSimpleCode(), AdaptiveCompiler.class.getClassLoader());
        HelloService helloService = (HelloService) clazz.newInstance();
        Assertions.assertEquals("Hello world!", helloService.sayHello());
    }

    @Test
    public void testAdaptiveCompiler() throws Exception { //Compiler的自适应类为AdaptiveCompiler
        Compiler compiler = ExtensionLoader.getExtensionLoader(Compiler.class).getAdaptiveExtension();
        Assertions.assertTrue(compiler instanceof AdaptiveCompiler);

        /**
         * SPI文件的加载路径：（用SPI文件放在不同目录、不同模块测试）
         * 1）org.apache.dubbo.common.compiler.Compiler文件放在src、test的resources目录下，都可以被加载
         * 2）org.apache.dubbo.common.compiler.Compiler文件放在其它模块，如dubbo-cluster就加载不了了
         */
    }

}
