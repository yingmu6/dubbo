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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by LinShunkang on 2020/03/12
 */
public class URLStrParserTest { //@DtY-Doing

    /**
     * 知识点：
     *
     * 知识点概括：
     * 1）URL中部分特殊字符对照
     *   %3A -> ':'
     *   %2F -> '/'
     *   %24 -> '$'
     *   %3F -> '?'
     *   %3D -> '='
     *
     * 关联点学习：
     * 1）ThreadLocal功能了解以及源码阅读（Doing）
     * 2）Java中的URL编解码以及中文编解码原理了解（Doing）
     */

    @Test
    public void test() { // Doing_测试Dubbo实现的URL解码功能
        String str = "dubbo%3A%2F%2Fadmin%3Aadmin123%40192.168.1.41%3A28113%2Forg.test.api.DemoService%24Iface%3Fanyhost%3Dtrue%26application%3Ddemo-service%26dubbo%3D2.6.1%26generic%3Dfalse%26interface%3Dorg.test.api.DemoService%24Iface%26methods%3DorbCompare%2CcheckText%2CcheckPicture%26pid%3D65557%26revision%3D1.4.17%26service.filter%3DbootMetrics%26side%3Dprovider%26status%3Dserver%26threads%3D200%26timestamp%3D1583136298859%26version%3D1.0.0";
        System.out.println(URLStrParser.parseEncodedStr(str)); //按dubbo方式解码

        String str2 = "dubbo%3A%2F%2F192.168.1.41%3A28113%2Forg.test.api.DemoService%24Iface%3Fanyhost%3Dtrue%26application%3D%E6%B5%8B%E8%AF%95%26dubbo%3D2.6.1%26generic%3Dfalse%26interface%3Dorg.test.api.DemoService%24Iface%26methods%3DorbCompare%2CcheckText%2CcheckPicture%26pid%3D65557%26revision%3D1.4.17%26service.filter%3DbootMetrics%26side%3Dprovider%26status%3Dserver%26threads%3D200%26timestamp%3D1583136298859%26version%3D1.0.0";
        System.out.println(URLStrParser.parseEncodedStr(str2)); //str2含有中文，即application=测试

        String decodeStr = URL.decode(str);
        System.out.println("java解码1：" + decodeStr);

        String decodeStr2 = URL.decode(str2);
        System.out.println("java解码2：" + decodeStr2); //使用java提供的URL解码，和Dubbo实现的URLStrParser.parseEncodedStr解码结果是一样的

        URL originalUrl = URL.valueOf(decodeStr);
        assertThat(URLStrParser.parseEncodedStr(str), equalTo(originalUrl));
        assertThat(URLStrParser.parseDecodedStr(decodeStr), equalTo(originalUrl));

        /**
         * 输出结果：
         * dubbo://192.168.1.41:28113/org.test.api.DemoService$Iface?anyhost=true&application=demo-service&dubbo=2.6.1&generic=false&interface=org.test.api.DemoService$Iface&methods=orbCompare,checkText,checkPicture&pid=65557&revision=1.4.17&service.filter=bootMetrics&side=provider&status=server&threads=200&timestamp=1583136298859&version=1.0.0
         * dubbo://192.168.1.41:28113/org.test.api.DemoService$Iface?anyhost=true&application=测试&dubbo=2.6.1&generic=false&interface=org.test.api.DemoService$Iface&methods=orbCompare,checkText,checkPicture&pid=65557&revision=1.4.17&service.filter=bootMetrics&side=provider&status=server&threads=200&timestamp=1583136298859&version=1.0.0
         * java解码1：dubbo://admin:admin123@192.168.1.41:28113/org.test.api.DemoService$Iface?anyhost=true&application=demo-service&dubbo=2.6.1&generic=false&interface=org.test.api.DemoService$Iface&methods=orbCompare,checkText,checkPicture&pid=65557&revision=1.4.17&service.filter=bootMetrics&side=provider&status=server&threads=200&timestamp=1583136298859&version=1.0.0
         * java解码2：dubbo://192.168.1.41:28113/org.test.api.DemoService$Iface?anyhost=true&application=测试&dubbo=2.6.1&generic=false&interface=org.test.api.DemoService$Iface&methods=orbCompare,checkText,checkPicture&pid=65557&revision=1.4.17&service.filter=bootMetrics&side=provider&status=server&threads=200&timestamp=1583136298859&version=1.0.0
         *
         * 结果分析：
         * 1）根据"%3F"（对应"?"字符）分隔参数，解析出的参数放入URL中的参数Map中。其中在解码时，会通过TempBuf先分开存含有
         *    特殊字符的字节数组、字符数组，解码后再合并一起，组合成解码后的字符串
         *
         * 2）解析URL的主体内容时，会根据[protocol://][username:password@][host:port]/[path]格式依次解析URL内容
         *
         * 3）举例说明：得到解码URL的步骤 dubbo://192.168.1.41:28113/org.test.api.DemoService$Iface?anyhost=true&application=demo-service&dubbo=2.6.1&generic=false&interface=org.test.api.DemoService$Iface&methods=orbCompare,checkText,checkPicture&pid=65557...
         *    3.1）得到"%3F"的下标为88，从当前线程局部变量ThreadLocal中得到TempBuf
         *    3.2）从88+3，即跳过"%3F"的字符开始遍历
         *         a）找到'%'符号，对解析出对应字符，如"%3D"对应"="
         *         b）找出键值对关联的字符"="、"&"
         *
         */
    }

    /**
     * 新增场景：URL只有一个参数
     */
    @Test
    public void test_only_param() {
        String str = "dubbo%3A%2F%2Fadmin%3Aadmin123%40192.168.1.41%3A28113%2Forg.test.api.DemoService%24Iface%3Fanyhost%3Dtrue";
        System.out.println(URLStrParser.parseEncodedStr(str));
    }

}
