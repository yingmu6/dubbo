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

import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.config.api.Greeting;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AbstractConfigTest {

    //FIXME
    /*@Test
    public void testAppendProperties1() throws Exception {
        try {
            System.setProperty("dubbo.properties.i", "1");
            System.setProperty("dubbo.properties.c", "c");
            System.setProperty("dubbo.properties.b", "2");
            System.setProperty("dubbo.properties.d", "3");
            System.setProperty("dubbo.properties.f", "4");
            System.setProperty("dubbo.properties.l", "5");
            System.setProperty("dubbo.properties.s", "6");
            System.setProperty("dubbo.properties.str", "dubbo");
            System.setProperty("dubbo.properties.bool", "true");
            PropertiesConfig config = new PropertiesConfig();
            AbstractConfig.appendProperties(config);
            Assertions.assertEquals(1, config.getI());
            Assertions.assertEquals('c', config.getC());
            Assertions.assertEquals((byte) 0x02, config.getB());
            Assertions.assertEquals(3d, config.getD());
            Assertions.assertEquals(4f, config.getF());
            Assertions.assertEquals(5L, config.getL());
            Assertions.assertEquals(6, config.getS());
            Assertions.assertEquals("dubbo", config.getStr());
            Assertions.assertTrue(config.isBool());
        } finally {
            System.clearProperty("dubbo.properties.i");
            System.clearProperty("dubbo.properties.c");
            System.clearProperty("dubbo.properties.b");
            System.clearProperty("dubbo.properties.d");
            System.clearProperty("dubbo.properties.f");
            System.clearProperty("dubbo.properties.l");
            System.clearProperty("dubbo.properties.s");
            System.clearProperty("dubbo.properties.str");
            System.clearProperty("dubbo.properties.bool");
        }
    }

    @Test
    public void testAppendProperties2() throws Exception {
        try {
            System.setProperty("dubbo.properties.two.i", "2");
            PropertiesConfig config = new PropertiesConfig("two");
            AbstractConfig.appendProperties(config);
            Assertions.assertEquals(2, config.getI());
        } finally {
            System.clearProperty("dubbo.properties.two.i");
        }
    }

    @Test
    public void testAppendProperties3() throws Exception {
        try {
            Properties p = new Properties();
            p.put("dubbo.properties.str", "dubbo");
            ConfigUtils.setProperties(p);
            PropertiesConfig config = new PropertiesConfig();
            AbstractConfig.appendProperties(config);
            Assertions.assertEquals("dubbo", config.getStr());
        } finally {
            System.clearProperty(Constants.DUBBO_PROPERTIES_KEY);
            ConfigUtils.setProperties(null);
        }
    }*/

    @Test
    public void testAppendParameters1() throws Exception { //已测（将Config对象的属性进行处理后，添加到参数Map中）
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("num", "ONE");
        AbstractConfig.appendParameters(parameters, new ParameterConfig(1, "hello/world", 30, "password"), "prefix");
        Assertions.assertEquals("one", parameters.get("prefix.key.1")); //值的来源：AbstractConfigTest$ParameterConfig#getParameters中参数key.1，参数key加上了前缀prefix
        Assertions.assertEquals("two", parameters.get("prefix.key.2")); //值的来源：AbstractConfigTest$ParameterConfig#getParameters中参数key-2，由于做了兼容性处理，"-"会被替换为"."
        Assertions.assertEquals("ONE,1", parameters.get("prefix.num")); //值的来源：由于AbstractConfigTest$ParameterConfig#getNumber上的@Parameter注解中的append=true，在相同key对应多个值时，使用分隔符","进行拼接
        Assertions.assertEquals("hello%2Fworld", parameters.get("prefix.naming")); //值的来源：由于AbstractConfigTest$ParameterConfig#getName的@Parameter的key配置为"naming"，会以注解上的配置为主；又因为escaped=true，所以会进行url编码
        Assertions.assertEquals("30", parameters.get("prefix.age")); //值得来源：AbstractConfigTest$ParameterConfig#getAge方法上没有配置@Parameter注解，直接从方法名中取出属性名age，再对应加上前缀
        Assertions.assertTrue(parameters.containsKey("prefix.key-2"));
        Assertions.assertTrue(parameters.containsKey("prefix.key.2")); //虽然会做兼容，将"-"替换为"."，但"-"对应的key也会存在，也就是新建了key，对应的value是相同的
        Assertions.assertFalse(parameters.containsKey("prefix.secret")); //由于AbstractConfigTest$ParameterConfig#getSecret上配置的@Paramter注解中的exclued=true，所以该值不会出现在Map中
    }

    @Test
    public void testAppendParameters2() throws Exception { //已测（在@Parameter中的required=true时，若参数为空，则会抛出IllegalStateException异常）
        try {
            Assertions.assertThrows(IllegalStateException.class, () -> {
                Map<String, String> parameters = new HashMap<String, String>();
                AbstractConfig.appendParameters(parameters, new ParameterConfig()); //因为定义的@Parameter中有些参数设置了required=true，即值是非空的。若为空，即会抛出异常
            });
        } catch (Exception e) {
            System.out.println(e.getMessage()); //因为Assertions.assertThrows内部已经捕获了异常，所以没有再把异常抛出来，所以此处新加的try/catch其实没有用到
        }
    }

    @Test
    public void testAppendParameters3() throws Exception { //已测（config对象为空时，不处理）
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractConfig.appendParameters(parameters, null);
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testAppendParameters4() throws Exception { //已测（附加参数时，可以不指定前缀，若不指定，则参数Map中的key就没有对应的前缀）
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractConfig.appendParameters(parameters, new ParameterConfig(1, "hello/world", 30, "password"));
        Assertions.assertEquals("one", parameters.get("key.1"));
        Assertions.assertEquals("two", parameters.get("key.2"));
        Assertions.assertEquals("1", parameters.get("num"));
        Assertions.assertEquals("hello%2Fworld", parameters.get("naming"));
        Assertions.assertEquals("30", parameters.get("age"));
    }

    @Test
    public void testAppendAttributes1() throws Exception { //已测（将Config对象中的属性，添加到参数Map中，AbstractConfig#appendAttributes方法，已被标记为弃用）
        Map<String, Object> parameters = new HashMap<String, Object>();
        AbstractConfig.appendAttributes(parameters, new AttributeConfig('l', true, (byte) 0x01), "prefix");
        Assertions.assertEquals('l', parameters.get("prefix.let")); //方法上配置的注解为：@Parameter(attribute = true, key = "let")
        Assertions.assertEquals(true, parameters.get("prefix.activate")); //方法上配置的注解为：@Parameter(attribute = true)
        Assertions.assertFalse(parameters.containsKey("prefix.flag")); //此处是因为getFlag()方法上，没有配置@Parameter，所以没设值处理
    }

    @Test
    public void testAppendAttributes2() throws Exception { //已测（添加属性时，没有指定前缀）
        Map<String, Object> parameters = new HashMap<String, Object>();
        AbstractConfig.appendAttributes(parameters, new AttributeConfig('l', true, (byte) 0x01));
        Assertions.assertEquals('l', parameters.get("let"));
        Assertions.assertEquals(true, parameters.get("activate"));
        Assertions.assertFalse(parameters.containsKey("flag"));
    }

    @Test
    public void checkExtension() throws Exception { //已测（检查属性对应的扩展名是否正确）
        Assertions.assertThrows(IllegalStateException.class, () -> ConfigValidationUtils.checkExtension(Greeting.class, "hello", "world"));
    }

    @Test
    public void checkMultiExtension1() throws Exception { //已测（检查属性对应的多个扩展名是否正确）
        Assertions.assertThrows(IllegalStateException.class, () -> ConfigValidationUtils.checkMultiExtension(Greeting.class, "hello", "default,world"));
    }

    @Test
    public void checkMultiExtension2() throws Exception { //已测（检查扩展名包含 "-"、"default"时的处理方式）
        Assertions.assertThrows(IllegalStateException.class, () -> ConfigValidationUtils.checkMultiExtension(Greeting.class, "hello", "default,-world"));
    }

    @Test
    public void checkLength() throws Exception { //已测（检查属性值的最大长度，最大字符长度不超过300个）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i <= 200; i++) {
                builder.append("a");
            }
            ConfigValidationUtils.checkLength("hello", builder.toString());
        });
    }

    @Test
    public void checkPathLength() throws Exception { //已测（校验路径长度）
        Assertions.assertThrows(IllegalStateException.class, () -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i <= 200; i++) {
                builder.append("a"); //附加了201个字符
            }
            ConfigValidationUtils.checkPathLength("hello", builder.toString()); //路径长度也不能超过200个字符
        });
    }

    @Test
    public void checkName() throws Exception { //已测（检查属性名是否正确，'%'符号不包含属性名的正则表达式中 [\-._0-9a-zA-Z]+ ）
        Assertions.assertThrows(IllegalStateException.class, () -> ConfigValidationUtils.checkName("hello", "world%"));
    }

    @Test
    public void checkNameHasSymbol() throws Exception { //已测（匹配的正则表达式为：[:*,\s/\-._0-9a-zA-Z]+）
        try {
            ConfigValidationUtils.checkNameHasSymbol("hello", ":*,/ -0123\tabcdABCD"); //非打印字符'\t' 能匹配上，即使正则表达式中没有
            ConfigValidationUtils.checkNameHasSymbol("mock", "force:return world");
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    public void checkKey() throws Exception { //已测（检查key的正确性）
        try {
            ConfigValidationUtils.checkKey("hello", "*,-0123abcdABCD");
        } catch (Exception e) {
            fail("the value should be legal."); //捕获到异常，再次抛出异常（所以此处：预期是不抛出异常的）
        }
    }

    @Test
    public void checkMultiName() throws Exception { //已测（正则表达式为：[,\-._0-9a-zA-Z]+，多个扩展名是用","分隔的）
        try {
            ConfigValidationUtils.checkMultiName("hello", ",-._0123abcdABCD");
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    public void checkPathName() throws Exception { //已测（路径的正则表达式为 [/\-$._0-9a-zA-Z]+ ）
        try {
            ConfigValidationUtils.checkPathName("hello", "/-$._0123abcdABCD");
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    public void checkMethodName() throws Exception { //已测（方法名对应的正则表达式为 [a-zA-Z][0-9a-zA-Z]* ）
        try {
            ConfigValidationUtils.checkMethodName("hello", "abcdABCD0123abcd");
        } catch (Exception e) {
            fail("the value should be legal.");
        }

        try {
            ConfigValidationUtils.checkMethodName("hello", "0a");
            fail("the value should be illegal.");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void checkParameterName() throws Exception { //已测（检查参数名称是否正确，正则表达式为[:*,\s/\-._0-9a-zA-Z]+ ）
        Map<String, String> parameters = Collections.singletonMap("hello", ":*,/-._0123abcdABCD"); //构建一个不可变的Map
        try {
            ConfigValidationUtils.checkParameterName(parameters);
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    @Config(interfaceClass = Greeting.class, filter = {"f1, f2"}, listener = {"l1, l2"},
            parameters = {"k1", "v1", "k2", "v2"})
    public void appendAnnotation() throws Exception { //已测（模拟解析注解中配置的值，然后附加到对应Config对象的过程，如@Reference、@Service等注解）
        Config config = getClass().getMethod("appendAnnotation").getAnnotation(Config.class);
        AnnotationConfig annotationConfig = new AnnotationConfig(); //AnnotationConfig的属性与@Config的属性相对应
        annotationConfig.appendAnnotation(Config.class, config);
        Assertions.assertSame(Greeting.class, annotationConfig.getInterface()); //对于"interfaceClass" 或 "interfaceName"，都会按 "interface"来处理
        Assertions.assertEquals("f1, f2", annotationConfig.getFilter()); //AbstractConfig#appendAnnotation中会将filter、listener对应的数组值转换为字符串，并使用分隔符拼接
        Assertions.assertEquals("l1, l2", annotationConfig.getListener());
        Assertions.assertEquals(2, annotationConfig.getParameters().size());
        Assertions.assertEquals("v1", annotationConfig.getParameters().get("k1")); //对于"parameters"，会按照参数Map来处理
        Assertions.assertEquals("v2", annotationConfig.getParameters().get("k2"));
        assertThat(annotationConfig.toString(), Matchers.containsString("filter=\"f1, f2\" "));
        assertThat(annotationConfig.toString(), Matchers.containsString("listener=\"l1, l2\" ")); //annotationConfig.toString()获取到的字符串值为："<dubbo:annotation listener="l1, l2" filter="f1, f2" />"
    }

    @Test
    public void testRefreshAll() { //已测（测试config的refresh() ）
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");
            overrideConfig.setExclude("override-config");

            Map<String, String> external = new HashMap<>();
            external.put("dubbo.override.address", "external://127.0.0.1:2181");
            // @Parameter(exclude=true)
            external.put("dubbo.override.exclude", "external");
            // @Parameter(key="key1", useKeyAsProperty=false)
            external.put("dubbo.override.key", "external");
            // @Parameter(key="key2", useKeyAsProperty=true)
            external.put("dubbo.override.key2", "external");
            ApplicationModel.getEnvironment().setExternalConfigMap(external); //设置额外的配置信息
            ApplicationModel.getEnvironment().initialize(); //从配置中心拉取配置做初始化（若有配置中心，则会覆盖Environment#externalConfigurationMap的值）

            System.setProperty("dubbo.override.address", "system://127.0.0.1:2181");
            System.setProperty("dubbo.override.protocol", "system");
            // this will not override, use 'key' instead, @Parameter(key="key1", useKeyAsProperty=false)
            System.setProperty("dubbo.override.key1", "system");
            System.setProperty("dubbo.override.key2", "system"); //设置系统变量

            // Load configuration from  system properties -> externalConfiguration -> RegistryConfig -> dubbo.properties（配置源加载的顺序）
            overrideConfig.refresh();

            Assertions.assertEquals("system://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("system", overrideConfig.getProtocol());
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());
            Assertions.assertEquals("external", overrideConfig.getKey());
            Assertions.assertEquals("system", overrideConfig.getUseKeyAsProperty());
        } finally {
            System.clearProperty("dubbo.override.address");
            System.clearProperty("dubbo.override.protocol");
            System.clearProperty("dubbo.override.key1");
            System.clearProperty("dubbo.override.key2");
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testRefreshSystem() { //已测（从系统配置中获取配置值，System设置的变量值 优于 Config对象设置的值）
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");
            overrideConfig.setExclude("override-config");

            System.setProperty("dubbo.override.address", "system://127.0.0.1:2181");
            System.setProperty("dubbo.override.protocol", "system");
            System.setProperty("dubbo.override.key", "system");

            overrideConfig.refresh();

            Assertions.assertEquals("system://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("system", overrideConfig.getProtocol()); //SystemConfiguration > AbstractConfig，所以会取System设置的值
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());
            Assertions.assertEquals("system", overrideConfig.getKey());
        } finally {
            System.clearProperty("dubbo.override.address");
            System.clearProperty("dubbo.override.protocol");
            System.clearProperty("dubbo.override.key1");
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testRefreshProperties() throws Exception { //已测（从属性文件中配置值）
        /**
         * 调试问题解答：
         * 1）ConfigUtils.setProperties(properties)是设置到ConfigUtils的共享变量中，为啥refresh()后，config对象能获取到值？
         *    解答：是因为在提取属性配置CompositeConfiguration#getInternalProperty时会遍历配置源，当遍历到PropertiesConfiguration配置源时，
         *         会调用getInternalProperty方法获取属性值，里面会调用ConfigUtils.getProperty(key)获取值，所有最终是取ConfigUtils#PROPERTIES值
         *
         * 2）PropertiesConfiguration配置源的优先级是怎样的？
         *    解答：根据Environment#getPrefixedConfiguration配置源的列表排列。
         *         1）if::this.isConfigCenterFirst()
         *              排列顺序：SystemConfiguration -> AppExternalConfiguration -> ExternalConfiguration -> AbstractConfig -> PropertiesConfiguration
         *         2）else
         *              排列顺序：SystemConfiguration -> AbstractConfig -> AppExternalConfiguration -> ExternalConfiguration -> PropertiesConfiguration
         */
        try {
            ApplicationModel.getEnvironment().setExternalConfigMap(new HashMap<>());
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");

            Properties properties = new Properties();
            properties.load(this.getClass().getResourceAsStream("/dubbo.properties")); //从当前类所在模块中，查找资源文件（即从test的resources目录下查找）
            ConfigUtils.setProperties(properties);

            overrideConfig.refresh();

            Assertions.assertEquals("override-config://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("override-config", overrideConfig.getProtocol());
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());
            //Assertions.assertEquals("properties", overrideConfig.getUseKeyAsProperty());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();
            ConfigUtils.setProperties(null);
        }
    }

    @Test
    public void testRefreshExternal() { //已测（从配置中心获取配置值，若没指定配置中心，则取设置的Map值）
        /**
         * 调试问题解答：
         * 1）Environment#setExternalConfigMap设置值时，是设置到Environment#externalConfigurationMap，对应哪个Configuration，是怎么取到值的？
         *   解答：在Environment#initialize()有进行设值，
         *         this.externalConfiguration.setProperties(externalConfigurationMap); //externalConfiguration对应externalConfigurationMap
         *         this.appExternalConfiguration.setProperties(appExternalConfigurationMap);
         *        对应的本地缓存类型为InmemoryConfiguration，在compositeConfiguration.getString(...) 取配置值时，会依次遍历配置源列表去获取值
         *
         * 2）ApplicationModel.getEnvironment().initialize();有什么作用？不调用的话，是不是不能获取值？
         *   解答：从方法执行逻辑来看，是将远程配置的值，加载到本地缓存中
         */
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");
            overrideConfig.setExclude("override-config");

            Map<String, String> external = new HashMap<>();
            external.put("dubbo.override.address", "external://127.0.0.1:2181");
            external.put("dubbo.override.protocol", "external");
            external.put("dubbo.override.escape", "external://");
            // @Parameter(exclude=true)
            external.put("dubbo.override.exclude", "external");
            // @Parameter(key="key1", useKeyAsProperty=false)
            external.put("dubbo.override.key", "external");
            // @Parameter(key="key2", useKeyAsProperty=true)
            external.put("dubbo.override.key2", "external");
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            ApplicationModel.getEnvironment().initialize();

            overrideConfig.refresh();

            Assertions.assertEquals("external://127.0.0.1:2181", overrideConfig.getAddress()); //默认情况下，Environment#configCenterFirst值为true，即默认 配置中心的配置优先，即ExternalConfigMap、AppExternalConfigMap优于Config对象的配置
            Assertions.assertEquals("external", overrideConfig.getProtocol());
            Assertions.assertEquals("external://", overrideConfig.getEscape());
            Assertions.assertEquals("external", overrideConfig.getExclude());
            Assertions.assertEquals("external", overrideConfig.getKey());
            Assertions.assertEquals("external", overrideConfig.getUseKeyAsProperty());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testRefreshById() { //todo pause
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setId("override-id");
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");
            overrideConfig.setExclude("override-config");

            Map<String, String> external = new HashMap<>();
            external.put("dubbo.override.override-id.address", "external-override-id://127.0.0.1:2181");
            external.put("dubbo.override.address", "external://127.0.0.1:2181");
            // @Parameter(exclude=true)
            external.put("dubbo.override.exclude", "external");
            // @Parameter(key="key1", useKeyAsProperty=false)
            external.put("dubbo.override.key", "external");
            // @Parameter(key="key2", useKeyAsProperty=true)
            external.put("dubbo.override.key2", "external");
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            ApplicationModel.getEnvironment().initialize();

            ConfigCenterConfig configCenter = new ConfigCenterConfig();
            overrideConfig.setConfigCenter(configCenter);
            // Load configuration from  system properties -> externalConfiguration -> RegistryConfig -> dubbo.properties
            overrideConfig.refresh();

            Assertions.assertEquals("external-override-id://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("override-config", overrideConfig.getProtocol());
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());
            Assertions.assertEquals("external", overrideConfig.getKey());
            Assertions.assertEquals("external", overrideConfig.getUseKeyAsProperty());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testRefreshParameters() {
        try {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("key1", "value1");
            parameters.put("key2", "value2");
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setParameters(parameters);


            Map<String, String> external = new HashMap<>();
            external.put("dubbo.override.parameters", "[{key3:value3},{key4:value4},{key2:value5}]");
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            ApplicationModel.getEnvironment().initialize();

            ConfigCenterConfig configCenter = new ConfigCenterConfig();
            overrideConfig.setConfigCenter(configCenter);
            // Load configuration from  system properties -> externalConfiguration -> RegistryConfig -> dubbo.properties
            overrideConfig.refresh();

            Assertions.assertEquals("value1", overrideConfig.getParameters().get("key1"));
            Assertions.assertEquals("value5", overrideConfig.getParameters().get("key2"));
            Assertions.assertEquals("value3", overrideConfig.getParameters().get("key3"));
            Assertions.assertEquals("value4", overrideConfig.getParameters().get("key4"));

            System.setProperty("dubbo.override.parameters", "[{key3:value6}]");
            overrideConfig.refresh();

            Assertions.assertEquals("value6", overrideConfig.getParameters().get("key3"));
            Assertions.assertEquals("value4", overrideConfig.getParameters().get("key4"));
        } finally {
            System.clearProperty("dubbo.override.parameters");
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testOnlyPrefixedKeyTakeEffect() {
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setNotConflictKey("value-from-config");

            Map<String, String> external = new HashMap<>();
            external.put("notConflictKey", "value-from-external");

            try {
                Map<String, String> map = new HashMap<>();
                map.put("notConflictKey", "value-from-env");
                map.put("dubbo.override.notConflictKey2", "value-from-env");
                setOsEnv(map);
            } catch (Exception e) {
                // ignore
                e.printStackTrace();
            }

            ApplicationModel.getEnvironment().setExternalConfigMap(external);

            overrideConfig.refresh();

            Assertions.assertEquals("value-from-config", overrideConfig.getNotConflictKey());
            Assertions.assertEquals("value-from-env", overrideConfig.getNotConflictKey2());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();

        }
    }

    @Test
    public void tetMetaData() {
        OverrideConfig overrideConfig = new OverrideConfig();
        overrideConfig.setId("override-id");
        overrideConfig.setAddress("override-config://127.0.0.1:2181");
        overrideConfig.setProtocol("override-config");
        overrideConfig.setEscape("override-config://");
        overrideConfig.setExclude("override-config");

        Map<String, String> metaData = overrideConfig.getMetaData();
        Assertions.assertEquals("override-config://127.0.0.1:2181", metaData.get("address"));
        Assertions.assertEquals("override-config", metaData.get("protocol"));
        Assertions.assertEquals("override-config://", metaData.get("escape"));
        Assertions.assertEquals("override-config", metaData.get("exclude"));
        Assertions.assertNull(metaData.get("key"));
        Assertions.assertNull(metaData.get("key2"));
    }

    @Test
    public void testEquals() {
        ApplicationConfig application1 = new ApplicationConfig();
        ApplicationConfig application2 = new ApplicationConfig();
        application1.setName("app1");
        application2.setName("app2");
        Assertions.assertNotEquals(application1, application2);
        application1.setName("sameName");
        application2.setName("sameName");
        Assertions.assertEquals(application1, application2);

        ProtocolConfig protocol1 = new ProtocolConfig();
        protocol1.setHost("127.0.0.1");// excluded
        protocol1.setName("dubbo");
        ProtocolConfig protocol2 = new ProtocolConfig();
        protocol2.setHost("127.0.0.2");// excluded
        protocol2.setName("dubbo");
        Assertions.assertEquals(protocol1, protocol2);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE})
    public @interface ConfigField {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface Config {
        Class<?> interfaceClass() default void.class; //对应的接口Class

        String interfaceName() default "";

        String[] filter() default {};

        String[]  listener() default {};

        String[] parameters() default {};

        ConfigField[] configFields() default {};

        ConfigField configField() default @ConfigField;
    }

    private static class OverrideConfig extends AbstractInterfaceConfig {
        public String address;
        public String protocol;
        public String exclude;
        public String key;
        public String useKeyAsProperty;
        public String escape;
        public String notConflictKey;
        public String notConflictKey2;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        @Parameter(excluded = true)
        public String getExclude() {
            return exclude;
        }

        public void setExclude(String exclude) {
            this.exclude = exclude;
        }

        @Parameter(key = "key1", useKeyAsProperty = false)
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @Parameter(key = "key2", useKeyAsProperty = true)
        public String getUseKeyAsProperty() {
            return useKeyAsProperty;
        }

        public void setUseKeyAsProperty(String useKeyAsProperty) {
            this.useKeyAsProperty = useKeyAsProperty;
        }

        @Parameter(escaped = true)
        public String getEscape() {
            return escape;
        }

        public void setEscape(String escape) {
            this.escape = escape;
        }

        public String getNotConflictKey() {
            return notConflictKey;
        }

        public void setNotConflictKey(String notConflictKey) {
            this.notConflictKey = notConflictKey;
        }

        public String getNotConflictKey2() {
            return notConflictKey2;
        }

        public void setNotConflictKey2(String notConflictKey2) {
            this.notConflictKey2 = notConflictKey2;
        }
    }

    private static class PropertiesConfig extends AbstractConfig {
        private char c;
        private boolean bool;
        private byte b;
        private int i;
        private long l;
        private float f;
        private double d;
        private short s;
        private String str;

        PropertiesConfig() {
        }

        PropertiesConfig(String id) {
            this.id = id;
        }

        public char getC() {
            return c;
        }

        public void setC(char c) {
            this.c = c;
        }

        public boolean isBool() {
            return bool;
        }

        public void setBool(boolean bool) {
            this.bool = bool;
        }

        public byte getB() {
            return b;
        }

        public void setB(byte b) {
            this.b = b;
        }

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }

        public long getL() {
            return l;
        }

        public void setL(long l) {
            this.l = l;
        }

        public float getF() {
            return f;
        }

        public void setF(float f) {
            this.f = f;
        }

        public double getD() {
            return d;
        }

        public void setD(double d) {
            this.d = d;
        }

        public String getStr() {
            return str;
        }

        public void setStr(String str) {
            this.str = str;
        }

        public short getS() {
            return s;
        }

        public void setS(short s) {
            this.s = s;
        }
    }

    private static class ParameterConfig { //用于测试的静态内部类
        private int number;
        private String name;
        private int age;
        private String secret;

        ParameterConfig() {
        }

        ParameterConfig(int number, String name, int age, String secret) {
            this.number = number;
            this.name = name;
            this.age = age;
            this.secret = secret;
        }

        @Parameter(key = "num", append = true)
        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }

        @Parameter(key = "naming", append = true, escaped = true, required = true)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        @Parameter(excluded = true)
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Map getParameters() {
            Map<String, String> map = new HashMap<String, String>();
            map.put("key.1", "one");
            map.put("key-2", "two");
            return map;
        }
    }

    private static class AttributeConfig {
        private char letter;
        private boolean activate;
        private byte flag;

        public AttributeConfig(char letter, boolean activate, byte flag) {
            this.letter = letter;
            this.activate = activate;
            this.flag = flag;
        }

        @Parameter(attribute = true, key = "let")
        public char getLetter() {
            return letter;
        }

        public void setLetter(char letter) {
            this.letter = letter;
        }

        @Parameter(attribute = true)
        public boolean isActivate() {
            return activate;
        }

        public void setActivate(boolean activate) {
            this.activate = activate;
        }

        public byte getFlag() {
            return flag;
        }

        public void setFlag(byte flag) {
            this.flag = flag;
        }
    }

    private static class AnnotationConfig extends AbstractConfig { //与@Config注解中属性是相对应的
        private Class interfaceClass;
        private String filter;
        private String listener;
        private Map<String, String> parameters;
        private String[] configFields;

        public Class getInterface() {
            return interfaceClass;
        }

        public void setInterface(Class interfaceName) {
            this.interfaceClass = interfaceName;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public String getListener() {
            return listener;
        }

        public void setListener(String listener) {
            this.listener = listener;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        public String[] getConfigFields() {
            return configFields;
        }

        public void setConfigFields(String[] configFields) {
            this.configFields = configFields;
        }
    }

    protected static void setOsEnv(Map<String, String> newenv) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (NoSuchFieldException e) {
            Class[] classes = Collections.class.getDeclaredClasses();
            Map<String, String> env = System.getenv();
            for (Class cl : classes) {
                if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
        }
    }
}
