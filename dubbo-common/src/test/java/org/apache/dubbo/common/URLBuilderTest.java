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
package org.apache.dubbo.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class URLBuilderTest {
    @Test
    public void testNoArgConstructor() { // 已测（构建url时，未指定参数）
        URL url = new URLBuilder().build();
        assertThat(url.toString(), equalTo(""));

        /**
         * 调试问题点：
         * 1）URL#string的值是哪里设置的？此处为啥是""
         */
    }

    @Test
    public void shouldAddParameter() { //已测（构建URL时，指定参数）
        URL url1 = URL.valueOf("dubbo://admin:hello1234@10.20.130.230:20880/context/path?version=1.0.0&application=morgan");
        URL url2 = URLBuilder.from(url1)
                .addParameter("newKey1", "newValue1") // string
                .addParameter("newKey2", 2) // int
                .addParameter("version", 1) // override
                .build();
        System.out.println("url字符串：" + url2.toString());
        assertThat(url2.getParameter("newKey1"), equalTo("newValue1"));
        assertThat(url2.getParameter("newKey2"), equalTo("2")); //参数的值，都是按String存储的
        assertThat(url2.getParameter("version"), equalTo("1"));
    }

    @Test
    public void shouldSet() { // 已测（设置地址address）
        URL url1 = URL.valueOf("dubbo://admin:hello1234@10.20.130.230:20880/context/path?version=1.0.0&application=morgan");
        URL url2 = URLBuilder.from(url1)
                .setProtocol("rest")
                .setUsername("newUsername")
                .setPassword("newPassword")
                .setHost("newHost")
                .setPath("newContext")
                .setPort(1234)
                .build();
        assertThat(url2.getProtocol(), equalTo("rest"));
        assertThat(url2.getUsername(), equalTo("newUsername"));
        assertThat(url2.getPassword(), equalTo("newPassword"));
        assertThat(url2.getHost(), equalTo("newHost"));
        assertThat(url2.getPort(), equalTo(1234));
        assertThat(url2.getPath(), equalTo("newContext"));

        url2 = URLBuilder.from(url1)
                .setAddress("newHost2:2345")
                .build();
        assertThat(url2.getHost(), equalTo("newHost2"));
        assertThat(url2.getPort(), equalTo(2345));
    }

    @Test
    public void shouldClearParameters() { //已测（清空参数）
        URL url1 = URL.valueOf("dubbo://admin:hello1234@10.20.130.230:20880/context/path?version=1.0.0&application=morgan");
        URL url2 = URLBuilder.from(url1)
                .clearParameters()
                .build();
        assertThat(url2.getParameters().size(), equalTo(0));
    }

    @Test
    public void shouldRemoveParameters() { // 已测（移除参数）
        URL url1 = URL.valueOf("dubbo://admin:hello1234@10.20.130.230:20880/context/path?version=1.0.0&application=morgan&key2=v2");
        URL url2 = URLBuilder.from(url1)
                .removeParameters(Arrays.asList("key2", "application")) //按列表移除
                .build();
        assertThat(url2.getParameters().size(), equalTo(1));
        assertThat(url2.getParameter("version"), equalTo("1.0.0"));
    }

    @Test
    public void shouldAddIfAbsent() { // 已测（addParameterIfAbsent添加参数时，若参数已经存在，则不设置）
        URL url1 = URL.valueOf("dubbo://admin:hello1234@10.20.130.230:20880/context/path?version=1.0.0&application=morgan&key2=v2");
        URL url2 = URLBuilder.from(url1)
                .addParameterIfAbsent("absentKey", "absentValue")
                .addParameterIfAbsent("version", "2.0.0") // should not override
                .build();
        assertThat(url2.getParameter("version"), equalTo("1.0.0"));
        assertThat(url2.getParameter("absentKey"), equalTo("absentValue"));
    }
}
