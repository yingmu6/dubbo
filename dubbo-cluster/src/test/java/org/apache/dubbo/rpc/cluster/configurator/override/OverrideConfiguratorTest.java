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
package org.apache.dubbo.rpc.cluster.configurator.override;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.rpc.cluster.configurator.absent.AbsentConfigurator;
import org.apache.dubbo.rpc.cluster.configurator.consts.UrlConstant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * OverrideConfiguratorTest
 */
public class OverrideConfiguratorTest {

    @Test
    public void testOverride_Application() {
        // 此处OverrideConfigurator中维护的configuratorUrl值为 "override://0.0.0.0/com.foo.BarService?timeout=200"，由于URL的toString()不显示用户名、密码，所以此处string中，没看到foo，但url中的username是有值的
        OverrideConfigurator configurator = new OverrideConfigurator(URL.valueOf("override://foo@0.0.0.0/com.foo.BarService?timeout=200"));

        URL url = configurator.configure(URL.valueOf(UrlConstant.URL_CONSUMER)); //产生的url为：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer&timeout=200
        Assertions.assertEquals("200", url.getParameter("timeout")); //若满足配置条件，则将配置url的参数替换指定url的参数

        url = configurator.configure(URL.valueOf(UrlConstant.URL_ONE)); //产生的url值为：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer&timeout=200
        Assertions.assertEquals("200", url.getParameter("timeout"));

        url = configurator.configure(URL.valueOf(UrlConstant.APPLICATION_BAR_SIDE_CONSUMER_11)); //产生的url：dubbo://10.20.153.11:20880/com.foo.BarService?application=bar&side=consumer
        Assertions.assertNull(url.getParameter("timeout")); //此处由于没符合配置条件，所以直接返回输入的url：APPLICATION_BAR_SIDE_CONSUMER_11

        url = configurator.configure(URL.valueOf(UrlConstant.TIMEOUT_1000_SIDE_CONSUMER_11)); //产生的url：dubbo://10.20.153.11:20880/com.foo.BarService?application=bar&side=consumer&timeout=1000
        Assertions.assertEquals("1000", url.getParameter("timeout")); //此处也是没有满足配置条件，直接返回输入的url
    }

    @Test
    public void testOverride_Host() {
        OverrideConfigurator configurator = new OverrideConfigurator(URL.valueOf("override://" + NetUtils.getLocalHost() + "/com.foo.BarService?timeout=200")); //url的内容如：override://192.168.1.106/com.foo.BarService?timeout=200

        URL url = configurator.configure(URL.valueOf(UrlConstant.URL_CONSUMER));
        Assertions.assertEquals("200", url.getParameter("timeout"));

        url = configurator.configure(URL.valueOf(UrlConstant.URL_ONE));
        Assertions.assertEquals("200", url.getParameter("timeout"));

        AbsentConfigurator configurator1 = new AbsentConfigurator(URL.valueOf("override://10.20.153.10/com.foo.BarService?timeout=200"));

        url = configurator1.configure(URL.valueOf(UrlConstant.APPLICATION_BAR_SIDE_CONSUMER_10));
        Assertions.assertNull(url.getParameter("timeout"));

        url = configurator1.configure(URL.valueOf(UrlConstant.TIMEOUT_1000_SIDE_CONSUMER_10));
        Assertions.assertEquals("1000", url.getParameter("timeout"));
    }

}
