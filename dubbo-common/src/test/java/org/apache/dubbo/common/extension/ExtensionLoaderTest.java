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
import org.apache.dubbo.common.convert.Converter;
import org.apache.dubbo.common.convert.StringToBooleanConverter;
import org.apache.dubbo.common.convert.StringToDoubleConverter;
import org.apache.dubbo.common.convert.StringToIntegerConverter;
import org.apache.dubbo.common.extension.activate.ActivateExt1;
import org.apache.dubbo.common.extension.activate.impl.*;
import org.apache.dubbo.common.extension.convert.String2BooleanConverter;
import org.apache.dubbo.common.extension.convert.String2DoubleConverter;
import org.apache.dubbo.common.extension.convert.String2IntegerConverter;
import org.apache.dubbo.common.extension.ext1.SimpleExt;
import org.apache.dubbo.common.extension.ext1.impl.SimpleExtImpl1;
import org.apache.dubbo.common.extension.ext1.impl.SimpleExtImpl2;
import org.apache.dubbo.common.extension.ext10_multi_names.Ext10MultiNames;
import org.apache.dubbo.common.extension.ext2.Ext2;
import org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt;
import org.apache.dubbo.common.extension.ext6_wrap.WrappedExt;
import org.apache.dubbo.common.extension.ext6_wrap.impl.Ext5Wrapper1;
import org.apache.dubbo.common.extension.ext6_wrap.impl.Ext5Wrapper2;
import org.apache.dubbo.common.extension.ext7.InitErrorExt;
import org.apache.dubbo.common.extension.ext8_add.AddExt1;
import org.apache.dubbo.common.extension.ext8_add.AddExt2;
import org.apache.dubbo.common.extension.ext8_add.AddExt3;
import org.apache.dubbo.common.extension.ext8_add.AddExt4;
import org.apache.dubbo.common.extension.ext8_add.impl.*;
import org.apache.dubbo.common.extension.ext9_empty.Ext9Empty;
import org.apache.dubbo.common.extension.ext9_empty.impl.Ext9EmptyImpl;
import org.apache.dubbo.common.extension.injection.InjectExt;
import org.apache.dubbo.common.extension.injection.impl.InjectExtImpl;
import org.apache.dubbo.common.lang.Prioritized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.extension.ExtensionLoader.getLoadingStrategies;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

public class ExtensionLoaderTest {

    /**
     * 测试的部分场景：
     * 1）SPI配置文件路径在不同的maven模块（确定SPI文件正确的摆放位置）
     * 2）依赖注册测试，即IOC功能测试
     * 3）封装类测试，即AOP功能测试
     */

    @Test
    public void test_getExtensionLoader_Null() throws Exception { //已测
        try {
            getExtensionLoader(null); //扩展类不能传入null
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(),
                    containsString("Extension type == null")); //期待实际值与预期值相同，如果实际值与预期的值不同，则会抛出异常
        }
    }

    @Test
    public void test_getExtensionLoader_NotInterface() throws Exception { //已测
        try {
            getExtensionLoader(ExtensionLoaderTest.class); //扩展类型需要是一个接口
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(),
                    containsString("Extension type (class org.apache.dubbo.common.extension.ExtensionLoaderTest) is not an interface"));
        }
    }

    @Test
    public void test_getExtensionLoader_NotSpiAnnotation() throws Exception { //已测
        try {
            getExtensionLoader(NoSpiExt.class); //扩展类需要带有@SPI的接口
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(),
                    allOf(containsString("org.apache.dubbo.common.extension.NoSpiExt"),
                            containsString("is not an extension"),
                            containsString("NOT annotated with @SPI")));
        }
    }

    @Test
    public void test_getDefaultExtension() throws Exception {
        ExtensionLoader<SimpleExt> extensionLoader1 = getExtensionLoader(SimpleExt.class);
        /**
         * 默认扩展实例的处理流程
         * 1）ExtensionLoader#getExtensionClasses() 获取扩展名与扩展Class映射的Map（如不存在，则加载SPI配置文件，构建该Map）
         * 2）ExtensionLoader#cacheDefaultExtensionName() 将去SPI注解声明的值，作为默认扩展名，设置到成员变量cachedDefaultName中
         * 3）ExtensionLoader#getExtension(extensionName) 传入默认扩展名，查找的配置文件对应的扩展Class，然后java.lang.Class#newInstance()创建实例
         *
         */
        SimpleExt ext = extensionLoader1.getDefaultExtension();

        /**
         * Hamcrest 是一个书写匹配器对象时允许直接定义匹配规则的框架。有大量的匹配器是侵入式的，例如 UI 验证或者数据过滤，但是匹配对象在书写灵活的测试是最常用。
         * （先根据匹配规则产生匹配器，然后带着匹配器去进行匹配）
         *
         * https://www.oschina.net/p/hamcrest?hmsr=aladdin1e1
         * http://hamcrest.org/JavaHamcrest/javadoc/1.3/   1.3 API文档
         */
        assertThat(ext, instanceOf(SimpleExtImpl1.class)); //先构建IsInstanceOf的匹配，然后使用assertThat()进行匹配

        // 先获取扩展加载器，然后再执行相应的方法
        ExtensionLoader<UseProtocolKeyExt> extensionLoader2 = getExtensionLoader(UseProtocolKeyExt.class); //@csy 此处为啥ExtensionLoader#EXTENSION_LOADERS中没有值？解答：经过调试以及代码分析，EXTENSION_LOADERS是存有值的
        UseProtocolKeyExt keyExt = extensionLoader2.getDefaultExtension();

        /**
         * extensionLoader2、extensionLoader3 属于同一扩展接口的扩展加载器，是同一个实例（会判断缓存中是否存在）
         *     ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
         */
        ExtensionLoader<UseProtocolKeyExt> extensionLoader3 = getExtensionLoader(UseProtocolKeyExt.class);
        UseProtocolKeyExt keyExt2 = extensionLoader3.getDefaultExtension(); //此处的keyExt与keyExt2是同一个对象实例（同一个扩展接口，对应的ExtensionLoader是相同的）

        assertThat(keyExt, instanceOf(UseProtocolKeyExt.class));
        assertThat(keyExt2, instanceOf(UseProtocolKeyExt.class));
        String name = getExtensionLoader(SimpleExt.class).getDefaultExtensionName();
        assertEquals("impl1", name);
    }

    @Test
    public void test_getDefaultExtension_NULL() throws Exception {
        Ext2 ext = getExtensionLoader(Ext2.class).getDefaultExtension(); //获取默认扩展实例
        /**
         * @csy-009 此处为啥没有获取到扩展实例，对应的配置文件有看到配置的
         * 解：是因为没有默认扩展名，getDefaultExtension()返回的就为空，默认扩展名是指SPI(value=) ，声明的value值
         */
        assertNull(ext);

        String name = getExtensionLoader(Ext2.class).getDefaultExtensionName(); //获取默认扩展名
        assertNull(name);
    }

    @Test
    public void test_getExtension() throws Exception {
        /**
         * @csy-009 扩展类的实例是怎么创建的？
         * 解：先加载扩展类，然后通过反射机制创建实例对象，并且处理依赖注入、封装类的实例化
         */
        assertTrue(getExtensionLoader(SimpleExt.class).getExtension("impl1") instanceof SimpleExtImpl1);
        assertTrue(getExtensionLoader(SimpleExt.class).getExtension("impl2") instanceof SimpleExtImpl2);
    }

    @Test
    public void test_getExtension_WithWrapper() throws Exception {
        /**
         * @csy-010 此处配置文件中impl1明明配置的是org.apache.dubbo.common.extension.ext6_wrap.impl.Ext5Impl1，为啥会取到Ext5Wrapper1的实例？
         * 解：若需要封装的话，if (wrap) {instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance)); ....} 会先调用封装类，然后封装类中再调用目标类
         * 因为创建扩展实例的时候，默认会使用封装类列表对扩展实例进行封装。此处的impl1、impl2实例都是Ext5Wrapper2，（扩展实例被封装类封装处理了，所以最终呈现的是封装类实例，然后封装类中的成员属性包含具体的封装类实例）
         * a）impl1的依赖为：impl1 = Ext5Wrapper2@xxx -> 的instance属性为Ext5Wrapper1@xxx -> 的instance属性为Ext5Impl1@xx
         * b）impl1的依赖为：impl2 = Ext5Wrapper2@xxx -> 的instance属性为Ext5Wrapper1@xxx -> 的instance属性为Ext5Impl2@xx
         */
        WrappedExt impl1 = getExtensionLoader(WrappedExt.class).getExtension("impl1");
        assertThat(impl1, anyOf(instanceOf(Ext5Wrapper1.class), instanceOf(Ext5Wrapper2.class)));

        WrappedExt impl2 = getExtensionLoader(WrappedExt.class).getExtension("impl2");
        assertThat(impl2, anyOf(instanceOf(Ext5Wrapper1.class), instanceOf(Ext5Wrapper2.class)));


        URL url = new URL("p1", "1.2.3.4", 1010, "path1");
        int echoCount1 = Ext5Wrapper1.echoCount.get();
        int echoCount2 = Ext5Wrapper2.echoCount.get();

        assertEquals("Ext5Impl1-echo", impl1.echo(url, "ha")); //先调用封装类，然后封装类中拦截处理，最后再调用具体实例方法
        assertEquals(echoCount1 + 1, Ext5Wrapper1.echoCount.get()); //impl的类型为Ext5Wrapper2@xxx，依赖关系见上面a），可推断出结果
        assertEquals(echoCount2 + 1, Ext5Wrapper2.echoCount.get());
        assertEquals("Ext5Impl2-echo", impl2.echo(url, "hh"));
    }

    @Test
    public void test_getExtension_ExceptionNoExtension() throws Exception {
        try {
            getExtensionLoader(SimpleExt.class).getExtension("XXX");
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("No such extension org.apache.dubbo.common.extension.ext1.SimpleExt by name XXX"));
        }
    }

    @Test
    public void test_getExtension_ExceptionNoExtension_WrapperNotAffactName() throws Exception {
        try {
            getExtensionLoader(WrappedExt.class).getExtension("XXX"); //未找到指定的扩展名，会抛出异常
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("No such extension org.apache.dubbo.common.extension.ext6_wrap.WrappedExt by name XXX"));
        }
    }

    @Test
    public void test_getExtension_ExceptionNullArg() throws Exception {
        try {
            getExtensionLoader(SimpleExt.class).getExtension(null); //扩展名不能为空
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), containsString("Extension name == null"));
        }
    }

    @Test
    public void test_hasExtension() throws Exception {
        assertTrue(getExtensionLoader(SimpleExt.class).hasExtension("impl1"));
        assertFalse(getExtensionLoader(SimpleExt.class).hasExtension("impl1,impl2")); //扩展名只有单一一个，不支持类似这种分隔（没有对扩展名进行分隔解析）
        assertFalse(getExtensionLoader(SimpleExt.class).hasExtension("xxx"));

        try {
            getExtensionLoader(SimpleExt.class).hasExtension(null); //扩展名不能为空
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), containsString("Extension name == null"));
        }
    }

    @Test
    public void test_hasExtension_wrapperIsNotExt() throws Exception {
        assertTrue(getExtensionLoader(WrappedExt.class).hasExtension("impl1"));
        assertFalse(getExtensionLoader(WrappedExt.class).hasExtension("impl1,impl2"));
        assertFalse(getExtensionLoader(WrappedExt.class).hasExtension("xxx"));

        /**
         * @csy-010 此处为啥没有wrapper1扩展实例？配置WrappedExt对应的配置文件是配置的（并不是配置文件中配置了，就存在扩展，要判断具体的类型，比如封装类等等）
         * 解：是从cachedClasses缓存类中取值判断的，wrapper1对应的是封装类，设置在cachedWrapperClasses成员变量中
         * 所以wrapper1对应的类是封装类，在cachedClasses成员变量中没有找到
         */
        assertFalse(getExtensionLoader(WrappedExt.class).hasExtension("wrapper1")); //封装类，不是扩展类，没有在cachedClasses缓存中

        try {
            getExtensionLoader(WrappedExt.class).hasExtension(null);
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), containsString("Extension name == null"));
        }
    }

    @Test
    public void test_getSupportedExtensions() throws Exception {
        Set<String> exts = getExtensionLoader(SimpleExt.class).getSupportedExtensions(); //获取支持的扩展名集合，即成员变量cachedClasses对应的key值集合

        Set<String> expected = new HashSet<String>();
        expected.add("impl1");
        expected.add("impl2");
        expected.add("impl3");

        assertEquals(expected, exts);
    }

    @Test
    public void test_getSupportedExtensions_wrapperIsNotExt() throws Exception {
        Set<String> exts = getExtensionLoader(WrappedExt.class).getSupportedExtensions(); //封装类的扩展名不在支持的扩展名集合中

        Set<String> expected = new HashSet<String>();
        expected.add("impl1");
        expected.add("impl2");

        assertEquals(expected, exts);
    }

    @Test
    public void test_AddExtension() throws Exception {
        try {
            getExtensionLoader(AddExt1.class).getExtension("Manual1"); //配置文件中没有配置Manual1扩展，所以会抛出未找到扩展异常
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("No such extension org.apache.dubbo.common.extension.ext8_add.AddExt1 by name Manual"));
        }

        getExtensionLoader(AddExt1.class).addExtension("Manual1", AddExt1_ManualAdd1.class); //添加扩展信息（都过接口添加配置，与配置文件方式的目标相同，最终都是把扩展信息存入缓存中）
        AddExt1 ext = getExtensionLoader(AddExt1.class).getExtension("Manual1");

        assertThat(ext, instanceOf(AddExt1_ManualAdd1.class));
        assertEquals("Manual1", getExtensionLoader(AddExt1.class).getExtensionName(AddExt1_ManualAdd1.class));
    }

    @Test
    public void test_AddExtension_NoExtend() throws Exception {
//        ExtensionLoader.getExtensionLoader(Ext9Empty.class).getSupportedExtensions();
        getExtensionLoader(Ext9Empty.class).addExtension("ext9", Ext9EmptyImpl.class);
        Ext9Empty ext = getExtensionLoader(Ext9Empty.class).getExtension("ext9");

        assertThat(ext, instanceOf(Ext9Empty.class));
        assertEquals("ext9", getExtensionLoader(Ext9Empty.class).getExtensionName(Ext9EmptyImpl.class)); //获取扩展实例对应的扩展名
    }

    @Test
    public void test_AddExtension_ExceptionWhenExistedExtension() throws Exception {
        SimpleExt ext = getExtensionLoader(SimpleExt.class).getExtension("impl1");

        try {
            getExtensionLoader(AddExt1.class).addExtension("impl1", AddExt1_ManualAdd1.class); //当扩展名已经存在时，就会抛出异常
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("Extension name impl1 already exists (Extension interface org.apache.dubbo.common.extension.ext8_add.AddExt1)!"));
        }
    }

    @Test
    public void test_AddExtension_Adaptive() throws Exception { //添加自适应扩展类
        ExtensionLoader<AddExt2> loader = getExtensionLoader(AddExt2.class);
        loader.addExtension(null, AddExt2_ManualAdaptive.class);

        AddExt2 adaptive = loader.getAdaptiveExtension();
        assertTrue(adaptive instanceof AddExt2_ManualAdaptive);
    }

    @Test
    public void test_AddExtension_Adaptive_ExceptionWhenExistedAdaptive() throws Exception {
        ExtensionLoader<AddExt1> loader = getExtensionLoader(AddExt1.class);

        loader.getAdaptiveExtension(); //todo @pause

        try {
            loader.addExtension(null, AddExt1_ManualAdaptive.class);
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("Adaptive Extension already exists (Extension interface org.apache.dubbo.common.extension.ext8_add.AddExt1)!"));
        }
    }

    @Test
    public void test_replaceExtension() throws Exception {
        try {
            getExtensionLoader(AddExt1.class).getExtension("Manual2");
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("No such extension org.apache.dubbo.common.extension.ext8_add.AddExt1 by name Manual"));
        }

        {
            AddExt1 ext = getExtensionLoader(AddExt1.class).getExtension("impl1");

            assertThat(ext, instanceOf(AddExt1Impl1.class));
            assertEquals("impl1", getExtensionLoader(AddExt1.class).getExtensionName(AddExt1Impl1.class));
        }
        {
            // 替换已存在的扩展
            getExtensionLoader(AddExt1.class).replaceExtension("impl1", AddExt1_ManualAdd2.class);
            AddExt1 ext = getExtensionLoader(AddExt1.class).getExtension("impl1");

            assertThat(ext, instanceOf(AddExt1_ManualAdd2.class));
            assertEquals("impl1", getExtensionLoader(AddExt1.class).getExtensionName(AddExt1_ManualAdd2.class));
        }
    }

    @Test
    public void test_replaceExtension_Adaptive() throws Exception { //替换带有@Adaptive注解的扩展类
        ExtensionLoader<AddExt3> loader = getExtensionLoader(AddExt3.class);

        AddExt3 adaptive = loader.getAdaptiveExtension();
        assertFalse(adaptive instanceof AddExt3_ManualAdaptive);

        loader.replaceExtension(null, AddExt3_ManualAdaptive.class);

        adaptive = loader.getAdaptiveExtension();
        assertTrue(adaptive instanceof AddExt3_ManualAdaptive);
    }

    @Test
    public void test_replaceExtension_ExceptionWhenNotExistedExtension() throws Exception {
        AddExt1 ext = getExtensionLoader(AddExt1.class).getExtension("impl1");

        try {
            getExtensionLoader(AddExt1.class).replaceExtension("NotExistedExtension", AddExt1_ManualAdd1.class);
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("Extension name NotExistedExtension doesn't exist (Extension interface org.apache.dubbo.common.extension.ext8_add.AddExt1)"));
        }
    }

    @Test
    public void test_replaceExtension_Adaptive_ExceptionWhenNotExistedExtension() throws Exception {
        ExtensionLoader<AddExt4> loader = getExtensionLoader(AddExt4.class); //AddExt4类没有对应的配置文件，所以缓存中没有对应配置

        try {
            loader.replaceExtension(null, AddExt4_ManualAdaptive.class);
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("Adaptive Extension doesn't exist (Extension interface org.apache.dubbo.common.extension.ext8_add.AddExt4)"));
        }
    }

    @Test
    public void test_InitError() throws Exception {
        ExtensionLoader<InitErrorExt> loader = getExtensionLoader(InitErrorExt.class);

        loader.getExtension("ok");

        try {
            loader.getExtension("error"); //error对应的类Ext7InitErrorImpl，在初始化时会主动抛出异常
            fail();
        } catch (IllegalStateException expected) { //上层应用主动接口异常并进行处理
            assertThat(expected.getMessage(), containsString("Failed to load extension class (interface: interface org.apache.dubbo.common.extension.ext7.InitErrorExt"));
            assertThat(expected.getCause(), instanceOf(ExceptionInInitializerError.class)); //ExceptionInInitializerError初始化时发生的异常
        }
    }

    @Test
    public void testLoadActivateExtension() throws Exception {
        // test default
        URL url = URL.valueOf("test://localhost/test");
        List<ActivateExt1> list = getExtensionLoader(ActivateExt1.class)
                .getActivateExtension(url, new String[]{}, "default_group");
        Assertions.assertEquals(1, list.size());
        Assertions.assertSame(list.get(0).getClass(), ActivateExt1Impl1.class); //找到一个符合条件的扩展类实例

        // test group
        url = url.addParameter(GROUP_KEY, "group1"); //值如：test://localhost/test?group=group1
        list = getExtensionLoader(ActivateExt1.class) //group1在配置文件中没有
                .getActivateExtension(url, new String[]{}, "group1");
        Assertions.assertEquals(1, list.size());
        Assertions.assertSame(list.get(0).getClass(), GroupActivateExtImpl.class);

        ActivateExt1 activateExt1 = getExtensionLoader(ActivateExt1.class).getExtension("group");
        System.out.println(activateExt1.echo("est3333"));

        // test old @Activate group
        url = url.addParameter(GROUP_KEY, "old_group");
        list = getExtensionLoader(ActivateExt1.class)
                .getActivateExtension(url, new String[]{}, "old_group");
        Assertions.assertEquals(2, list.size());
        Assertions.assertTrue(list.get(0).getClass() == OldActivateExt1Impl2.class
                || list.get(0).getClass() == OldActivateExt1Impl3.class);

        // test value
        url = url.removeParameter(GROUP_KEY);
        url = url.addParameter(GROUP_KEY, "value");
        url = url.addParameter("value", "value"); //url的值如：test://localhost/test?group=value&value=value
        list = getExtensionLoader(ActivateExt1.class)
                .getActivateExtension(url, new String[]{}, "value");
        Assertions.assertEquals(1, list.size());
        Assertions.assertSame(list.get(0).getClass(), ValueActivateExtImpl.class);

        // test order
        url = URL.valueOf("test://localhost/test");
        url = url.addParameter(GROUP_KEY, "order");
        list = getExtensionLoader(ActivateExt1.class)
                .getActivateExtension(url, new String[]{}, "order");
        Assertions.assertEquals(2, list.size());
        Assertions.assertSame(list.get(0).getClass(), OrderActivateExtImpl1.class);
        Assertions.assertSame(list.get(1).getClass(), OrderActivateExtImpl2.class);
    }

    @Test
    public void testLoadDefaultActivateExtension() throws Exception {
        // test default
        URL url = URL.valueOf("test://localhost/test?ext=order1,default,order4");
        List<ActivateExt1> list = getExtensionLoader(ActivateExt1.class)
                .getActivateExtension(url, "ext", "default_group");
        Assertions.assertEquals(2, list.size());
        Assertions.assertSame(list.get(0).getClass(), OrderActivateExtImpl1.class);
        Assertions.assertSame(list.get(1).getClass(), ActivateExt1Impl1.class);

        url = URL.valueOf("test://localhost/test?ext=default,order1");
        list = getExtensionLoader(ActivateExt1.class)
                .getActivateExtension(url, "ext", "default_group");
        Assertions.assertEquals(2, list.size());
        Assertions.assertSame(list.get(0).getClass(), ActivateExt1Impl1.class);
        Assertions.assertSame(list.get(1).getClass(), OrderActivateExtImpl1.class);
    }

    @Test
    public void testInjectExtension() {
        // test default
        InjectExt injectExt = getExtensionLoader(InjectExt.class).getExtension("injection");
        InjectExtImpl injectExtImpl = (InjectExtImpl) injectExt;
        Assertions.assertNotNull(injectExtImpl.getSimpleExt());
        Assertions.assertNull(injectExtImpl.getSimpleExt1());
        Assertions.assertNull(injectExtImpl.getGenericType());
    }

    @Test
    void testMultiNames() {
        Ext10MultiNames ext10MultiNames = getExtensionLoader(Ext10MultiNames.class).getExtension("impl");
        Assertions.assertNotNull(ext10MultiNames);
        ext10MultiNames = getExtensionLoader(Ext10MultiNames.class).getExtension("implMultiName");
        Assertions.assertNotNull(ext10MultiNames);
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> getExtensionLoader(Ext10MultiNames.class).getExtension("impl,implMultiName")
        );
    }

    @Test
    public void testGetOrDefaultExtension() {
        ExtensionLoader<InjectExt> loader = getExtensionLoader(InjectExt.class);
        InjectExt injectExt = loader.getOrDefaultExtension("non-exists");
        assertEquals(InjectExtImpl.class, injectExt.getClass());
        assertEquals(InjectExtImpl.class, loader.getOrDefaultExtension("injection").getClass());
    }

    @Test
    public void testGetSupported() {
        ExtensionLoader<InjectExt> loader = getExtensionLoader(InjectExt.class);
        assertEquals(1, loader.getSupportedExtensions().size());
        assertEquals(Collections.singleton("injection"), loader.getSupportedExtensions());
    }

    /**
     * @since 2.7.7
     */
    @Test
    public void testOverridden() {
        ExtensionLoader<Converter> loader = getExtensionLoader(Converter.class);

        Converter converter = loader.getExtension("string-to-boolean");
        assertEquals(String2BooleanConverter.class, converter.getClass());

        converter = loader.getExtension("string-to-double");
        assertEquals(String2DoubleConverter.class, converter.getClass());

        converter = loader.getExtension("string-to-integer");
        assertEquals(String2IntegerConverter.class, converter.getClass());

        assertEquals("string-to-boolean", loader.getExtensionName(String2BooleanConverter.class));
        assertEquals("string-to-boolean", loader.getExtensionName(StringToBooleanConverter.class));

        assertEquals("string-to-double", loader.getExtensionName(String2DoubleConverter.class));
        assertEquals("string-to-double", loader.getExtensionName(StringToDoubleConverter.class));

        assertEquals("string-to-integer", loader.getExtensionName(String2IntegerConverter.class));
        assertEquals("string-to-integer", loader.getExtensionName(StringToIntegerConverter.class));
    }

    /**
     * @since 2.7.7
     */
    @Test
    public void testGetLoadingStrategies() {
        List<LoadingStrategy> strategies = getLoadingStrategies();

        assertEquals(4, strategies.size());

        int i = 0;

        LoadingStrategy loadingStrategy = strategies.get(i++);
        assertEquals(DubboInternalLoadingStrategy.class, loadingStrategy.getClass());
        assertEquals(Prioritized.MAX_PRIORITY, loadingStrategy.getPriority());

        loadingStrategy = strategies.get(i++);
        assertEquals(DubboExternalLoadingStrategy.class, loadingStrategy.getClass());
        assertEquals(Prioritized.MAX_PRIORITY + 1, loadingStrategy.getPriority());


        loadingStrategy = strategies.get(i++);
        assertEquals(DubboLoadingStrategy.class, loadingStrategy.getClass());
        assertEquals(Prioritized.NORMAL_PRIORITY, loadingStrategy.getPriority());

        loadingStrategy = strategies.get(i++);
        assertEquals(ServicesLoadingStrategy.class, loadingStrategy.getClass());
        assertEquals(Prioritized.MIN_PRIORITY, loadingStrategy.getPriority());
    }
}
