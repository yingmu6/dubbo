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

import org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt;
import org.apache.dubbo.common.extension.ext_self.animal.Animal;
import org.apache.dubbo.common.utils.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdaptiveClassCodeGenerator} Test
 *
 * @since 2.7.5
 */
public class AdaptiveClassCodeGeneratorTest {

    @Test
    public void testGenerate() throws IOException { //已测（测试打印产生的自适应类，以及产生的自适应字节码文件中读取内容）
        AdaptiveClassCodeGenerator generator = new AdaptiveClassCodeGenerator(HasAdaptiveExt.class, "adaptive");
        String value = generator.generate(); //自适应生成的代码
        System.out.println("自适应类代码：" + value);
        URL url = getClass().getResource("/org/apache/dubbo/common/extension/adaptive/HasAdaptiveExt$Adaptive");
        try (InputStream inputStream = url.openStream()) { //可以从产生的自适应字节码文件中，读取内容
            String content = IOUtils.read(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            // in Windows platform content get from resource contains \r delimiter
            content = content.replaceAll("\r", "");
            assertTrue(content.contains(value));
        }
    }

    @Test
    public void testGenerate_V2() throws IOException { //todo @pause (在Animal中没有指定key时，key值从哪里找的？)
        AdaptiveClassCodeGenerator generator = new AdaptiveClassCodeGenerator(Animal.class, "");
        String value = generator.generate(); //自适应生成的代码
        System.out.println("自适应类代码V2：" + value);
        URL url = getClass().getResource("/org/apache/dubbo/common/extension/adaptive/Animal$Adaptive");
        try (InputStream inputStream = url.openStream()) { //可以从产生的自适应字节码文件中，读取内容
            String content = IOUtils.read(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            // in Windows platform content get from resource contains \r delimiter
            content = content.replaceAll("\r", "");
            assertTrue(content.contains(value));
        }
    }
}
