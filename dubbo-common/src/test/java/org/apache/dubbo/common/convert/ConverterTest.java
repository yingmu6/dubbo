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
package org.apache.dubbo.common.convert;

import org.junit.jupiter.api.Test;

import static org.apache.dubbo.common.convert.Converter.convertIfPossible;
import static org.apache.dubbo.common.convert.Converter.getConverter;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link Converter} Test-Cases
 *
 * @since 2.7.8
 */
public class ConverterTest {

    @Test
    public void testGetConverter() { //已测（Converter的getConverter方法测试）
        getExtensionLoader(Converter.class)
                .getSupportedExtensionInstances()
                .forEach(converter -> { //遍历扩展实例，判断是否与Converter#getConverter获取的实例相同
                    assertSame(converter, getConverter(converter.getSourceType(), converter.getTargetType()));
                });
    }

    @Test
    public void testConvertIfPossible() { //已测（通过泛化类型找到Converter，然后再执行具体转换）
        assertEquals(Integer.valueOf(2), convertIfPossible("2", Integer.class));
        assertEquals(Boolean.FALSE, convertIfPossible("false", Boolean.class));
        assertEquals(Double.valueOf(1), convertIfPossible("1", Double.class));
    }
}
