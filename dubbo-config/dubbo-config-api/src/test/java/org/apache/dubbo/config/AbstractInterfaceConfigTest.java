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
package org.apache.dubbo.config;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.api.Greeting;
import org.apache.dubbo.config.mock.GreetingLocal1;
import org.apache.dubbo.config.mock.GreetingLocal2;
import org.apache.dubbo.config.mock.GreetingLocal3;
import org.apache.dubbo.config.mock.GreetingMock1;
import org.apache.dubbo.config.mock.GreetingMock2;
import org.apache.dubbo.config.utils.ConfigValidationUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;

public class AbstractInterfaceConfigTest {
    private static File dubboProperties;

    @BeforeAll
    public static void setUp(@TempDir Path folder) {
        dubboProperties = folder.resolve(CommonConstants.DUBBO_PROPERTIES_KEY).toFile();
        System.setProperty(CommonConstants.DUBBO_PROPERTIES_KEY, dubboProperties.getAbsolutePath());
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(CommonConstants.DUBBO_PROPERTIES_KEY);
    }

    @AfterEach
    public void tearMethodAfterEachUT() {
//        ApplicationModel.getConfigManager().clear();
    }

    @Test
    public void testCheckRegistry1() {
        /**
         * 调试问题点：
         * 1）为什么调用了interfaceConfig.checkRegistry()后，注册列表中AbstractInterfaceConfig#registries就有值了？
         *    解答：在调用checkRegistry()时，会调用convertRegistryIdsToRegistries()，里面会调用AbstractConfig#refresh()从配置源中获取值，
         *         因为有使用System.setProperty(...)设置了注册地址，从而创建了注册实例，注册列表就不为空
         */
        System.setProperty("dubbo.registry.address", "addr1");
        try {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setApplication(new ApplicationConfig("testCheckRegistry1"));
            interfaceConfig.checkRegistry(); //检查注册配置是否存在，并会转换为RegistryConfig
            Assertions.assertEquals(1, interfaceConfig.getRegistries().size());
            Assertions.assertEquals("addr1", interfaceConfig.getRegistries().get(0).getAddress());
        } finally {
            System.clearProperty("dubbo.registry.address");
        }
    }

    @Test
    public void testCheckRegistry2() { //已测（检查注册配置时，若应用配置信息ApplicationConfig为空时，会抛出异常）
        /**
         * 调试问题答疑：
         * 1）为什么此处会抛出异常？是在哪里抛出的异常？
         *    解答：因为AbstractInterfaceConfig#computeValidRegistryIds中获取应用配置 ConfigManager#getApplicationOrElseThrow()时，
         *         若应用配置没有设置，即AbstractInterfaceConfig#application属性值为null时，会抛出异常
         *
         * 注明：查看assertThrows抛出的异常轨迹方式
         * 1）可去掉Assertions.assertThrows运行，看异常的轨迹
         * 2）也可以在AssertThrows#assertThrows打断点，查看异常信息（推荐方式）
         */
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.checkRegistry();
        });

//        InterfaceConfig interfaceConfig = new InterfaceConfig();
//        interfaceConfig.checkRegistry();
    }

    @Test
    public void checkInterfaceAndMethods1() { //已测（参数interfaceClass不能为空）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.checkInterfaceAndMethods(null, null);
        });
    }

    @Test
    public void checkInterfaceAndMethods2() { //已测（输入的interfaceClass需要接口类型）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.checkInterfaceAndMethods(AbstractInterfaceConfigTest.class, null);
        });
    }

    @Test
    public void checkInterfaceAndMethod3() { //已测（MethodConfig中的name属性是必填的）
        Assertions.assertThrows(IllegalStateException.class, () -> { //抛出的异常信息为：java.lang.IllegalStateException: <dubbo:method> name attribute is required!....
            MethodConfig methodConfig = new MethodConfig();
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.checkInterfaceAndMethods(Greeting.class, Collections.singletonList(methodConfig)); //singletonList：产生单例列表
        });
    }

    @Test
    public void checkInterfaceAndMethod4() { //已测（判断接口中是否包含指定的方法）
        Assertions.assertThrows(IllegalStateException.class, () -> { //抛出的异常信息：The interface org.apache.dubbo.config.api.Greeting not found method nihao
            MethodConfig methodConfig = new MethodConfig();
            methodConfig.setName("nihao");
            InterfaceConfig interfaceConfig = new InterfaceConfig(); //因为Greeting类中，不包含方法名为"nihao"的方法，所以会抛出异常
            interfaceConfig.checkInterfaceAndMethods(Greeting.class, Collections.singletonList(methodConfig));
        });
    }

    @Test
    public void checkInterfaceAndMethod5() { //已测（接口与方法能匹配，即Greeting类中，包含"hello"的方法 ）
        MethodConfig methodConfig = new MethodConfig();
        methodConfig.setName("hello");
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkInterfaceAndMethods(Greeting.class, Collections.singletonList(methodConfig));
    }

    @Test
    public void checkStubAndMock1() { //已测（检查服务接口与本地实现类即stub的关系）
        /**
         * 调试问题答疑：
         * 1）AbstractInterfaceConfig#local成员属性的功能用途是什么？
         *    解答：是服务接口对应的本地实现类的类名，已被弃用，用stub代替
         */
        Assertions.assertThrows(IllegalStateException.class, () -> { //抛出的异常：java.lang.IllegalStateException: The local implementation class org.apache.dubbo.config.mock.GreetingLocal1 not implement...
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setLocal(GreetingLocal1.class.getName()); //设置服务接口的本地实现类类名
            interfaceConfig.checkStubAndLocal(Greeting.class); //此处会检查AbstractInterfaceConfig#verify接口与实现类的关系，因为Greeting与GreetingLocal1没有关联，所以会报出异常
            ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
        });
    }

    @Test
    public void checkStubAndMock2() { //已测（检查本地存根stud中的构造方法是否正确）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setLocal(GreetingLocal2.class.getName());
            interfaceConfig.checkStubAndLocal(Greeting.class); //因为GreetingLocal2是Greeting的实现类，满足 Greeting.class.isAssignFrom(GreetingLocal2.class)，因为GreetingLocal2是Greeting实现类，所以可以进行赋值。
            ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
        });

        /**
         * 结果分析:
         * 1）会抛出异常： "java.lang.IllegalStateException: No such constructor "public GreetingLocal2..."
         *
         * 2）原因分析：因为checkStubAndLocal检查本地存根时，会调用ReflectUtils.findConstructor(localClass, interfaceClass);
         *           来检查类中是否包含指定参数的构造方法。因为GreetingLocal2中不包含Greeting为参数的构造方法，所以抛出异常
         */
    }

    @Test
    public void checkStubAndMock3() { //已测（测试Mock类设置，）
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLocal(GreetingLocal3.class.getName());
        interfaceConfig.checkStubAndLocal(Greeting.class);
        ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
    }

    @Test
    public void checkStubAndMock4() { //已测（检查接口与实现类关系）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setStub(GreetingLocal1.class.getName());
            interfaceConfig.checkStubAndLocal(Greeting.class);
            ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
        });

        /**
         * 输出结果：
         * java.lang.IllegalStateException: The local implementation class
         * org.apache.dubbo.config.mock.GreetingLocal1 not implement interface org.apache.dubbo.config.api.Greeting
         *
         * 结果分析：
         * 由于AbstractInterfaceConfig#checkStubAndLocal 会检查接口与实现类的关系
         */
    }

    @Test
    public void checkStubAndMock5() { //已测（校验ReflectUtils.findConstructor 检查类中是否包含指定参数的构造方法）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setStub(GreetingLocal2.class.getName());
            interfaceConfig.checkStubAndLocal(Greeting.class);
            ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
        });
    }

    @Test
    public void checkStubAndMock6() { //已测（检查接口与本地存根的关系，未设置Mock类，不检查接口是Mock的关系）
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setStub(GreetingLocal3.class.getName());
        interfaceConfig.checkStubAndLocal(Greeting.class);
        ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
    }

    @Test
    public void checkStubAndMock7() { //已测（mock字符串的合法性检查）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setMock("return {a, b}");
//            interfaceConfig.setMock("return {\"a\":\"b\"}"); //此mock字符串，能通过校验
            interfaceConfig.checkStubAndLocal(Greeting.class);
            ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
        });

        /**
         * 结果输出：
         * 1）会抛出：com.alibaba.fastjson.JSONException: expect ':' at 0, actual ,
         * 2）抛异常的位置：MockInvoker#parseMockValue中
         *  else if (mock.startsWith("{")) { //按Map对象解析
         *      value = JSON.parseObject(mock, Map.class);
         *  }
         *
         * 结果分析：
         * mock配置的字符串为"return {a, b}"，解析的mock字符串为"{a, b}"，按代码逻辑，若"{"开头，会按Map进行解析
         * "{a, b}"不是Map的key、value形式，所以会报错，mock字符串可改为 "return {\"a\":\"b\"}"
         */
    }

    @Test
    public void checkStubAndMock8() { //已测（检查mock=Mock类名时，校验服务接口与Mock类名关系）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setMock(GreetingMock1.class.getName());
            interfaceConfig.checkStubAndLocal(Greeting.class);
            ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
        });

        /**
         * 结果输出：
         * 1）抛出的异常：java.lang.IllegalStateException: The mock class org.apache.dubbo.config.mock.GreetingMock1 not implement interface org.apache.dubbo.config.api.Greeting
         * 2）异常的位置：MockInvoker#getMockObject中的
         *      if (mockClass == null || !serviceType.isAssignableFrom(mockClass)) { //检查mock类是否实现了指定接口
         *           throw new IllegalStateException("The mock class " + mockClass.getName() +
         *                " not implement interface " + serviceType.getName());
         *      }
         *
         * 结果分析：
         * 当mock字符串设置的是Mock实现类的类名，则会检查服务接口与Mock实现类的关系是否正确，即Mock类需实现服务接口
         */
    }

    @Test
    public void checkStubAndMock9() { //已测（因为创建mock实例对象时，构造方法是private，所以创建异常）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setMock(GreetingMock2.class.getName());
            interfaceConfig.checkStubAndLocal(Greeting.class);
            ConfigValidationUtils.checkMock(Greeting.class, interfaceConfig);
        });

        /**
         * 结果输出：
         * 1）抛出的异常：java.lang.IllegalStateException: java.lang.IllegalAccessException: Class org.apache.dubbo.rpc.support.MockInvoker
         *              can not access a member of class org.apache.dubbo.config.mock.GreetingMock2 with modifiers "private"
         *
         * 2）异常的位置：MockInvoker#getMockObject
         *
         * 结果分析：
         * 1）因为getMockObject会创建实例，mockClass.newInstance()，因为构造方法是private，所以不能创建对象
         */
    }

    @Test
    public void testLocal() { //已测（设置本地实现类Local）
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLocal((Boolean) null);
        Assertions.assertNull(interfaceConfig.getLocal());
        interfaceConfig.setLocal(true); //可以设置true
        Assertions.assertEquals("true", interfaceConfig.getLocal());
        interfaceConfig.setLocal("GreetingMock"); //可以设置Local实现类
        Assertions.assertEquals("GreetingMock", interfaceConfig.getLocal());
    }

    @Test
    public void testStub() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setStub((Boolean) null);
        Assertions.assertNull(interfaceConfig.getStub());
        interfaceConfig.setStub(true);
        Assertions.assertEquals("true", interfaceConfig.getStub());
        interfaceConfig.setStub("GreetingMock");
        Assertions.assertEquals("GreetingMock", interfaceConfig.getStub());
    }

    @Test
    public void testCluster() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setCluster("mockcluster");
        Assertions.assertEquals("mockcluster", interfaceConfig.getCluster());
    }

    @Test
    public void testProxy() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setProxy("mockproxyfactory");
        Assertions.assertEquals("mockproxyfactory", interfaceConfig.getProxy());
    }

    @Test
    public void testConnections() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setConnections(1);
        Assertions.assertEquals(1, interfaceConfig.getConnections().intValue());
    }

    @Test
    public void testFilter() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setFilter("mockfilter");
        Assertions.assertEquals("mockfilter", interfaceConfig.getFilter());
    }

    @Test
    public void testListener() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setListener("mockinvokerlistener");
        Assertions.assertEquals("mockinvokerlistener", interfaceConfig.getListener());
    }

    @Test
    public void testLayer() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLayer("layer");
        Assertions.assertEquals("layer", interfaceConfig.getLayer());
    }

    @Test
    public void testApplication() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        ApplicationConfig applicationConfig = new ApplicationConfig();
        interfaceConfig.setApplication(applicationConfig);
        Assertions.assertSame(applicationConfig, interfaceConfig.getApplication());
    }

    @Test
    public void testModule() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        ModuleConfig moduleConfig = new ModuleConfig();
        interfaceConfig.setModule(moduleConfig);
        Assertions.assertSame(moduleConfig, interfaceConfig.getModule());
    }

    @Test
    public void testRegistry() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        RegistryConfig registryConfig = new RegistryConfig();
        interfaceConfig.setRegistry(registryConfig);
        Assertions.assertSame(registryConfig, interfaceConfig.getRegistry());
    }

    @Test
    public void testRegistries() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        RegistryConfig registryConfig = new RegistryConfig();
        interfaceConfig.setRegistries(Collections.singletonList(registryConfig));
        Assertions.assertEquals(1, interfaceConfig.getRegistries().size());
        Assertions.assertSame(registryConfig, interfaceConfig.getRegistries().get(0));
    }

    @Test
    public void testMonitor() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setMonitor("monitor-addr");
        Assertions.assertEquals("monitor-addr", interfaceConfig.getMonitor().getAddress());
        MonitorConfig monitorConfig = new MonitorConfig();
        interfaceConfig.setMonitor(monitorConfig);
        Assertions.assertSame(monitorConfig, interfaceConfig.getMonitor());
    }

    @Test
    public void testOwner() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setOwner("owner");
        Assertions.assertEquals("owner", interfaceConfig.getOwner());
    }

    @Test
    public void testCallbacks() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setCallbacks(2);
        Assertions.assertEquals(2, interfaceConfig.getCallbacks().intValue());
    }

    @Test
    public void testOnconnect() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setOnconnect("onConnect");
        Assertions.assertEquals("onConnect", interfaceConfig.getOnconnect());
    }

    @Test
    public void testOndisconnect() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setOndisconnect("onDisconnect");
        Assertions.assertEquals("onDisconnect", interfaceConfig.getOndisconnect());
    }

    @Test
    public void testScope() {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setScope("scope");
        Assertions.assertEquals("scope", interfaceConfig.getScope());
    }

    public static class InterfaceConfig extends AbstractInterfaceConfig {

    }
}
