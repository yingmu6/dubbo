/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.cluster.RouterFactory;
import org.apache.dubbo.rpc.cluster.router.condition.ConditionRouterFactory;
import org.apache.dubbo.rpc.cluster.router.condition.config.AppRouterFactory;
import org.apache.dubbo.rpc.cluster.router.tag.TagRouterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.GENERIC_SERIALIZATION_NATIVE_JAVA;
import static org.apache.dubbo.common.constants.CommonConstants.INVOKER_LISTENER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REFERENCE_FILTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.STUB_EVENT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.CLUSTER_STICKY_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.ROUTER_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasValue;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractReferenceConfigTest { //@DtY-Doing

    /**
     * 知识点：
     *
     * 知识点概括：
     * 1）
     */

    @Test
    public void testCheck() throws Exception { //Doing_检查提供的服务是否存在
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setCheck(true);
        assertThat(referenceConfig.isCheck(), is(true));

        /**
         * 结果分析：
         */
    }

    @Test
    public void testInit() throws Exception { //已测（init：是否为惰性初始化）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setInit(true);
        assertThat(referenceConfig.isInit(), is(true));
    }

    @Test
    public void testGeneric() throws Exception { //已测（generic：是否使用泛化接口）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setGeneric(true);
        assertThat(referenceConfig.isGeneric(), is(true));
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractInterfaceConfig.appendParameters(parameters, referenceConfig);
        // FIXME: not sure why AbstractReferenceConfig has both isGeneric and getGeneric
        assertThat(parameters, hasKey("generic"));
    }

    @Test
    public void testInjvm() throws Exception { //已测（init：测试）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setInit(true);
        assertThat(referenceConfig.isInit(), is(true));
    }

    @Test
    public void testFilter() throws Exception { //已测（定义多个Filter，值进行拼接）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setFilter("mockfilter");
        assertThat(referenceConfig.getFilter(), equalTo("mockfilter"));
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(REFERENCE_FILTER_KEY, "prefilter");
        AbstractInterfaceConfig.appendParameters(parameters, referenceConfig);
        assertThat(parameters, hasValue("prefilter,mockfilter"));
        // 因为AbstractReferenceConfig.getFilter方法上@Parameter参数配置的key为REFERENCE_FILTER_KEY，
        // 而ReferenceConfig的filter属性也设置了值，所以会进行拼接
    }

    @Test
    public void testRouter() throws Exception { // 已测（服务路由的设置）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setRouter("condition"); //通过Config对象设置服务路由
        assertThat(referenceConfig.getRouter(), equalTo("condition"));
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(ROUTER_KEY, "tag");
        AbstractInterfaceConfig.appendParameters(parameters, referenceConfig);
        assertThat(parameters, hasValue("tag,condition")); //AbstractReferenceConfig#getRouter方法上的注解@Parameter的key为ROUTER_KEY，自定义参数的值与Config属性进行合并
        URL url = mock(URL.class); //使用Mockito创建Mock对象
        when(url.getParameter(ROUTER_KEY)).thenReturn("condition"); //在调用url.getParameter(ROUTER_KEY))方法时，返回mock值
        List<RouterFactory> routerFactories = ExtensionLoader.getExtensionLoader(RouterFactory.class).getActivateExtension(url, ROUTER_KEY);
        assertThat(routerFactories.stream().anyMatch(routerFactory -> routerFactory.getClass().equals(ConditionRouterFactory.class)), is(true));
        when(url.getParameter(ROUTER_KEY)).thenReturn("-tag,-app"); // 去除指定的路由扩展实例
        routerFactories = ExtensionLoader.getExtensionLoader(RouterFactory.class).getActivateExtension(url, ROUTER_KEY);
        assertThat(routerFactories.stream()
                .allMatch(routerFactory -> !routerFactory.getClass().equals(TagRouterFactory.class)
                        && !routerFactory.getClass().equals(AppRouterFactory.class)), is(true));
    }

    @Test
    public void testListener() throws Exception { // 已测（监听器测试）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setListener("mockinvokerlistener");
        assertThat(referenceConfig.getListener(), equalTo("mockinvokerlistener"));
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(INVOKER_LISTENER_KEY, "prelistener");
        AbstractInterfaceConfig.appendParameters(parameters, referenceConfig);
        assertThat(parameters, hasValue("prelistener,mockinvokerlistener")); //支持多个监听器，通过","拼接
    }

    @Test
    public void testLazy() throws Exception { // 已测（设置是否延迟创建连接）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setLazy(true);
        assertThat(referenceConfig.getLazy(), is(true));
    }

    @Test
    public void testOnconnect() throws Exception { //已测（设置连接事件）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setOnconnect("onConnect");
        assertThat(referenceConfig.getOnconnect(), equalTo("onConnect"));
        assertThat(referenceConfig.getStubevent(), is(true));
    }

    @Test
    public void testOndisconnect() throws Exception { //已测（设置拒绝事件）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setOndisconnect("onDisconnect");
        assertThat(referenceConfig.getOndisconnect(), equalTo("onDisconnect"));
        assertThat(referenceConfig.getStubevent(), is(true));
    }

    @Test
    public void testStubevent() throws Exception { //已测（获取存根事件）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setOnconnect("onConnect");
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractInterfaceConfig.appendParameters(parameters, referenceConfig); //因为setOnconnect时，会设置stubevent值，而getStubevent()方法上的@Parameter(key = STUB_EVENT_KEY)，所以参数包含STUB_EVENT_KEY
        assertThat(parameters, hasKey(STUB_EVENT_KEY));
    }

    @Test
    public void testReconnect() throws Exception { //已测（设置重连事件）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setReconnect("reconnect");
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractInterfaceConfig.appendParameters(parameters, referenceConfig);
        assertThat(referenceConfig.getReconnect(), equalTo("reconnect"));
        assertThat(parameters, hasKey(Constants.RECONNECT_KEY));
    }

    @Test
    public void testSticky() throws Exception { //已测（设置是否粘黏属性值）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setSticky(true);
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractInterfaceConfig.appendParameters(parameters, referenceConfig);
        assertThat(referenceConfig.getSticky(), is(true));
        assertThat(parameters, hasKey(CLUSTER_STICKY_KEY)); //参数key通过@Parameter或get方法提取属性名，此处是通过get方法获取的属性名
    }

    @Test
    public void testVersion() throws Exception { //已测（设置服务的版本号）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setVersion("version");
        assertThat(referenceConfig.getVersion(), equalTo("version"));
    }

    @Test
    public void testGroup() throws Exception { //已测（设置服务的分组）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setGroup("group");
        assertThat(referenceConfig.getGroup(), equalTo("group"));
    }

    @Test
    public void testGenericOverride() { //已测（设置泛化值 generic）
        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setGeneric("false");
        referenceConfig.refresh(); //refresh() 从各种配置中获取到值，然后设置到config中
        Assertions.assertFalse(referenceConfig.isGeneric());
        Assertions.assertEquals("false", referenceConfig.getGeneric());

        ReferenceConfig referenceConfig1 = new ReferenceConfig();
        referenceConfig1.setGeneric(GENERIC_SERIALIZATION_NATIVE_JAVA);
        referenceConfig1.refresh();
        Assertions.assertEquals(GENERIC_SERIALIZATION_NATIVE_JAVA, referenceConfig1.getGeneric());
        Assertions.assertTrue(referenceConfig1.isGeneric());

        ReferenceConfig referenceConfig2 = new ReferenceConfig();
        referenceConfig2.refresh();
        Assertions.assertNull(referenceConfig2.getGeneric());
    }

    private static class ReferenceConfig extends AbstractReferenceConfig {

    }
}
