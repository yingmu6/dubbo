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
package org.apache.dubbo.config.spring.schema;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.*;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.env.Environment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.HIDE_KEY_PREFIX;

/**
 * AbstractBeanDefinitionParser
 *
 * @export
 */
public class DubboBeanDefinitionParser implements BeanDefinitionParser {

    private static final Logger logger = LoggerFactory.getLogger(DubboBeanDefinitionParser.class);
    private static final Pattern GROUP_AND_VERSION = Pattern.compile("^[\\-.0-9_a-zA-Z]+(\\:[\\-.0-9_a-zA-Z]+)?$");
    private static final String ONRETURN = "onreturn";
    private static final String ONTHROW = "onthrow";
    private static final String ONINVOKE = "oninvoke";
    private static final String METHOD = "Method";
    private final Class<?> beanClass; // Bean对应的Class类（即Config的Class类）
    private final boolean required;   // 是否必须（即Config类的id是否必须）

    public DubboBeanDefinitionParser(Class<?> beanClass, boolean required) { // 在DubboNamespaceHandler的init方法写入成员变量的
        this.beanClass = beanClass;
        this.required = required;
    }

    /**
     * 解析XML元素，并将元素的属性值设置到Config类关联的Bean中
     */
    @SuppressWarnings("unchecked")
    //parse()方法是从哪里进入的？ 解：DubboNamespaceHandler#parse()中调用父类NamespaceHandlerSupport#parse()，然后在findParserForElement()之中，根据元素名称，从init()时映射的parsers键值对中找到对应的Bean解析器，就进入了该方法（策略模式）
    private static RootBeanDefinition parse(Element element, ParserContext parserContext, Class<?> beanClass, boolean required) { //ParserContext：通过bean定义解析过程传递的上下文，封装所有相关配置和状态，嵌套在XmlReaderContext内
        RootBeanDefinition beanDefinition = new RootBeanDefinition(); //创建spring的Bean实例（通过使用Spring的API方式创建Bean，而不是通过XML或注解方法配置Bean）
        beanDefinition.setBeanClass(beanClass); //设置Bean对应的class类，如MethodConfig.class（每一个Config对象都会产生对应的Bean）
        beanDefinition.setLazyInit(false); //设置是否延迟初始化，false：在spring容器启动时，就会创建Bean实例
        String id = resolveAttribute(element, "id", parserContext); //解析属性id的值
        /**
         * 处理属性id的值，若属性id为空且是必须的，则尝试获取其它属性的值，如name、interface属性，若还为空则获取bean的名称
         */
        if (StringUtils.isEmpty(id) && required) { //未设置属性id时，使用其它属性产生id值
            String generatedBeanName = resolveAttribute(element, "name", parserContext); //解析属性name的值，并作为id值
            if (StringUtils.isEmpty(generatedBeanName)) {
                if (ProtocolConfig.class.equals(beanClass)) {
                    generatedBeanName = "dubbo";
                } else {
                    generatedBeanName = resolveAttribute(element, "interface", parserContext);
                }
            }
            if (StringUtils.isEmpty(generatedBeanName)) {
                generatedBeanName = beanClass.getName(); //bean的名称如：class org.apache.dubbo.config.ApplicationConfig
            }
            id = generatedBeanName;
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) { //若bean已存在，则加上计数标识，直到没有重复的id为止
                id = generatedBeanName + (counter++); // count++的值从2开始递增，变量count在前，count++就是加之前的值
            }
        }
        if (StringUtils.isNotEmpty(id)) {
            if (parserContext.getRegistry().containsBeanDefinition(id)) { //判断已经注册的Bean中是否有重复的id
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            parserContext.getRegistry().registerBeanDefinition(id, beanDefinition); // 向spring注册中心注册bean实例（将id作为Bean的名称）
            beanDefinition.getPropertyValues().addPropertyValue("id", id); // 设置bean的id属性
        }
        /**
         * 对指定的bean进行处理，如ProtocolConfig、ServiceBean、ProviderConfig、ConsumerConfig等
         */
        if (ProtocolConfig.class.equals(beanClass)) { //对暴露的协议解析
            for (String name : parserContext.getRegistry().getBeanDefinitionNames()) { //遍历已注册的bean对应的名称列表
                BeanDefinition definition = parserContext.getRegistry().getBeanDefinition(name); //获取指定bean名称对应的BeanDefinition实例
                PropertyValue property = definition.getPropertyValues().getPropertyValue("protocol");
                if (property != null) {
                    Object value = property.getValue();
                    if (value instanceof ProtocolConfig && id.equals(((ProtocolConfig) value).getName())) {
                        definition.getPropertyValues().addPropertyValue("protocol", new RuntimeBeanReference(id)); //关联对应的bean
                    }
                }
            }
        } else if (ServiceBean.class.equals(beanClass)) { //对暴露的服务解析
            String className = resolveAttribute(element, "class", parserContext);
            if (StringUtils.isNotEmpty(className)) { //在<dubbo:service/>中设置class属性时进入（通过指定"class"属性方式暴露服务，还有一种是通过"ref"属性方式）
                RootBeanDefinition classDefinition = new RootBeanDefinition(); //根bean定义也可以用于注册单个bean定义
                classDefinition.setBeanClass(ReflectUtils.forName(className));
                classDefinition.setLazyInit(false);
                parseProperties(element.getChildNodes(), classDefinition, parserContext);
                beanDefinition.getPropertyValues().addPropertyValue("ref", new BeanDefinitionHolder(classDefinition, id + "Impl")); //BeanDefinitionHolder：带有名称和别名的bean定义的Holder
                // 此处的bean实例，会在bean的名称后面加上Impl，如：org.apache.dubbo.demo.DemoService + "Impl"，对应的实现类是org.apache.dubbo.demo.DemoServiceImpl
            }
        } else if (ProviderConfig.class.equals(beanClass)) { //对<dubbo:provider> 中的嵌套<dubbo:service> 元素进行解析
            parseNested(element, parserContext, ServiceBean.class, true, "service", "provider", id, beanDefinition);
        } else if (ConsumerConfig.class.equals(beanClass)) { //对<dubbo:consumer> 中的嵌套<dubbo:reference> 元素进行解析
            parseNested(element, parserContext, ReferenceBean.class, false, "reference", "consumer", id, beanDefinition);
        }
        Set<String> props = new HashSet<>();
        ManagedMap parameters = null; //托管的Map（Spring的标签集合类，用于保存被托管的Map）
        for (Method setter : beanClass.getMethods()) { //遍历Config中的set方法提取出属性名，然后通过XML的Element解析出对应的属性值，最后依次设置到Config类关联的Bean的属性中
            String name = setter.getName();
            if (name.length() > 3 && name.startsWith("set")
                    && Modifier.isPublic(setter.getModifiers())
                    && setter.getParameterTypes().length == 1) {
                Class<?> type = setter.getParameterTypes()[0];
                String beanProperty = name.substring(3, 4).toLowerCase() + name.substring(4); //解析出属性名，如方法名为setName，属性名为name
                String property = StringUtils.camelToSplitName(beanProperty, "-"); //按分隔符方式处理属性名称
                props.add(property); //将属性加到属性集合中
                // check the setter/getter whether match (检查set、get方法是否匹配)
                Method getter = null;
                try {
                    getter = beanClass.getMethod("get" + name.substring(3), new Class<?>[0]);//获取指定的属性对应的get方法，如getName
                } catch (NoSuchMethodException e) {
                    try {
                        getter = beanClass.getMethod("is" + name.substring(3), new Class<?>[0]); //处理is开头的方法，比如ApplicationConfig中的isDefault()方法
                    } catch (NoSuchMethodException e2) { //允许没有get方法，比如EnvironmentAware，只有set方法，不能抛出异常，不然引起应用启动失败（感觉有些多余，既然不处理异常，为啥还要检查是否有该方法 -- 从后面的处理来看，若get方法为空，会跳过后面处理，进入下一个循环，也就是有对应逻辑处理，所以不多余）
                        // ignore, there is no need any log here since some class implement the interface: EnvironmentAware,
                        // ApplicationAware, etc. They only have setter method, otherwise will cause the error log during application start up.
                    }
                }
                if (getter == null
                        || !Modifier.isPublic(getter.getModifiers())
                        || !type.equals(getter.getReturnType())) { //若没有找到符合条件的get方法，则本次不处理，跳到下次循环
                    continue;
                }

                /**
                 * 从XML元素中读取Config类属性对应的值，并设置到Bean的属性中
                 */
                if ("parameters".equals(property)) {
                    parameters = parseParameters(element.getChildNodes(), beanDefinition, parserContext); //解析<dubbo:parameter/>元素的值
                } else if ("methods".equals(property)) {
                    parseMethods(id, element.getChildNodes(), beanDefinition, parserContext); //解析<dubbo:method/>元素的值
                } else if ("arguments".equals(property)) {
                    parseArguments(id, element.getChildNodes(), beanDefinition, parserContext); //解析<dubbo:argument/>元素的值
                } else { //对常规属性做处理
                    String value = resolveAttribute(element, property, parserContext); //解析XML中元素对应的属性值
                    if (value != null) { //若属性值为不为空，则对应处理（即XML中设置了对应的属性值）
                        value = value.trim(); //过滤字符串的前后空格（即过滤掉空字符串）
                        if (value.length() > 0) {
                            if ("registry".equals(property) && RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(value)) {
                                RegistryConfig registryConfig = new RegistryConfig();
                                registryConfig.setAddress(RegistryConfig.NO_AVAILABLE);
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty, registryConfig);
                            } else if ("provider".equals(property) || "registry".equals(property) || ("protocol".equals(property) && AbstractServiceConfig.class.isAssignableFrom(beanClass))) {
                                // Class中方法isAssignableFrom()：判断当前class是否与参数中指定的class相同，或者是参数指定的class的父类或父接口（主语是当前class，如上的主语即为：AbstractServiceConfig）
                                /**
                                 * For 'provider' 'protocol' 'registry', keep literal value (should be id/name) and set the value to 'registryIds' 'providerIds' protocolIds'
                                 * The following process should make sure each id refers to the corresponding instance, here's how to find the instance for different use cases:
                                 * 1. Spring, check existing bean by id, see{@link ServiceBean#afterPropertiesSet()}; then try to use id to find configs defined in remote Config Center
                                 * 2. API, directly use id to find configs defined in remote Config Center; if all config instances are defined locally, please use {@link ServiceConfig#setRegistries(List)}
                                 */
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty + "Ids", value);
                            } else {
                                Object reference;
                                if (isPrimitive(type)) { //对基本类型的属性处理（基本类型进行了扩展，包含String、Date等）
                                    if ("async".equals(property) && "false".equals(value)
                                            || "timeout".equals(property) && "0".equals(value) //每个判断条件作为一行，清晰明了
                                            || "delay".equals(property) && "0".equals(value)
                                            || "version".equals(property) && "0.0.0".equals(value)
                                            || "stat".equals(property) && "-1".equals(value)
                                            || "reliable".equals(property) && "false".equals(value)) {
                                        // backward compatibility for the default value in old version's xsd（向后兼容旧版本的XSD中的默认值）
                                        value = null; //若旧版本的属性满足条件，则将属性值设置为null
                                    }
                                    reference = value;
                                // 对方法<dubbo:method>元素中的onreturn、onthrow、oninvoke属性进行处理
                                } else if (ONRETURN.equals(property) || ONTHROW.equals(property) || ONINVOKE.equals(property)) {
                                    int index = value.lastIndexOf("."); //待覆盖调试：事件通知允许Consumer端在调用之前、调用之后或出现异常时，触发oninvoke、onreturn、onthrow三个事件。 https://dubbo.apache.org/zh/docs/advanced/events-notify/
                                    String ref = value.substring(0, index); //onreturn、onthrow、oninvoke的属性值，必须要以 xxx.方法名形式，若没有"."，则会由于[0,-1)报字符串区间错误 "String index out of range: -1"
                                    String method = value.substring(index + 1);
                                    reference = new RuntimeBeanReference(ref);
                                    beanDefinition.getPropertyValues().addPropertyValue(property + METHOD, method);
                                } else { //解析ref属性<property name="ref">
                                    if ("ref".equals(property) && parserContext.getRegistry().containsBeanDefinition(value)) {
                                        BeanDefinition refBean = parserContext.getRegistry().getBeanDefinition(value);
                                        if (!refBean.isSingleton()) { //检查暴露的服务是不是单例
                                            throw new IllegalStateException("The exported service ref " + value + " must be singleton! Please set the " + value + " bean scope to singleton, eg: <bean id=\"" + value + "\" scope=\"singleton\" ...>");
                                        }
                                    }
                                    reference = new RuntimeBeanReference(value); //创建指定名称的bean
                                }
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty, reference); //为bean添加属性值
                            }
                        }
                    }
                }
            }
        }

        NamedNodeMap attributes = element.getAttributes(); //获取元素的属性节点列表
        int len = attributes.getLength();
        for (int i = 0; i < len; i++) {
            Node node = attributes.item(i);
            String name = node.getLocalName();
            if (!props.contains(name)) { //处理未处理过的属性节点
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String value = node.getNodeValue();
                parameters.put(name, new TypedStringValue(value, String.class));
            }
        }
        if (parameters != null) { //对<dubbo:parameters/>配置的参数单独处理，并设置到beanDefinition的parameters属性中
            beanDefinition.getPropertyValues().addPropertyValue("parameters", parameters);
        }
        return beanDefinition;
    }

    private static boolean isPrimitive(Class<?> cls) { //判断是否是基本类型（在Class的isPrimitive()基础上做了封装，加上额外的类型）
        return cls.isPrimitive() || cls == Boolean.class || cls == Byte.class
                || cls == Character.class || cls == Short.class || cls == Integer.class
                || cls == Long.class || cls == Float.class || cls == Double.class
                || cls == String.class || cls == Date.class || cls == Class.class;
    }

    /**
     * 解析元素内嵌的子节点，产生对应类型的bean，并建立关联
     */
    private static void parseNested(Element element, ParserContext parserContext, Class<?> beanClass, boolean required, String tag, String property, String ref, BeanDefinition beanDefinition) {
        NodeList nodeList = element.getChildNodes(); //获取元素的子节点列表
        if (nodeList == null) {
            return;
        }
        boolean first = true;
        for (int i = 0; i < nodeList.getLength(); i++) { //可以有多个内嵌节点
            Node node = nodeList.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            // 解析元素element指定名称的子节点
            if (tag.equals(node.getNodeName()) //带上命名空间的节点名称，如dubbo:service
                    || tag.equals(node.getLocalName())) { //去掉命名空间的名称，如service
                if (first) { //处理元素element的属性default（只处理一次即可）
                    first = false;
                    String isDefault = resolveAttribute(element, "default", parserContext); // default: 是否为缺省协议，用于多协议
                    if (StringUtils.isEmpty(isDefault)) { //处理默认属性default
                        beanDefinition.getPropertyValues().addPropertyValue("default", "false");
                    }
                }
                /**
                 * BeanDefinition：在Spring中，Bean的解析阶段，会把xml配制中的<bean>标签解析成Spring中的BeanDefinition对象
                 *    1）BeanDefinition是bean在Spring中的描述，有了BeanDefinition我们就可以创建Bean，BeanDefinition是Bean在Spring中的定义形态
                 *    2）BeanDefinition与Bean的关系, 就好比类与对象的关系. 类在spring的数据结构就是BeanDefinition.根据BeanDefinition得到的对象就是我们需要的Bean
                 * https://juejin.cn/post/6844903959136567310 Bean与BeanDefinition关系
                 */

                // 解析元素内部嵌套的子节点，产生对应的bean，并将元素与子节点进行关联
                BeanDefinition subDefinition = parse((Element) node, parserContext, beanClass, required); //获取嵌套元素对应的Bean，如<dubbo:provider>中<dubbo:service>
                if (subDefinition != null && StringUtils.isNotEmpty(ref)) { //依赖的bean用RuntimeBeanReference表示
                    subDefinition.getPropertyValues().addPropertyValue(property, new RuntimeBeanReference(ref)); //将元素与子节点进行关联，ref是元素的id，如<dubbo:provider>的id
                }
                /**
                 * RuntimeBeanReference：如果一个bean依赖其它的bean，比如<dubbo:service>中ref，那么被依赖的bean就用RuntimeBeanReference表示
                 * （因为解析阶段，还没有依赖的bean的实例，等解析以后存在实例时，再根据RuntimeBeanReference关联）
                 * https://blog.csdn.net/Jerryai1/article/details/52980239
                 */
            }
        }
    }

    /**
     * 解析属性元素<dubbo:property/>中配置的值，并设置到Bean的属性中
     */
    private static void parseProperties(NodeList nodeList, RootBeanDefinition beanDefinition, ParserContext parserContext) {
        if (nodeList == null) {
            return;
        }
        for (int i = 0; i < nodeList.getLength(); i++) { //NodeList: 提供了有序节点的抽象节点的集合
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("property".equals(element.getNodeName()) //只对属性节点处理<dubbo:property>
                    || "property".equals(element.getLocalName())) {
                String name = resolveAttribute(element, "name", parserContext); //解析<property>中的name属性
                if (StringUtils.isNotEmpty(name)) {
                    String value = resolveAttribute(element, "value", parserContext);
                    String ref = resolveAttribute(element, "ref", parserContext);
                    if (StringUtils.isNotEmpty(value)) { //解析基本属性
                        beanDefinition.getPropertyValues().addPropertyValue(name, value); //属性值列表beanDefinition.getPropertyValues()， 对应<property>的值
                    } else if (StringUtils.isNotEmpty(ref)) { //解析含有引用的属性，创建对应的bean
                        beanDefinition.getPropertyValues().addPropertyValue(name, new RuntimeBeanReference(ref));
                    } else {
                        throw new UnsupportedOperationException("Unsupported <property name=\"" + name + "\"> sub tag, Only supported <property name=\"" + name + "\" ref=\"...\" /> or <property name=\"" + name + "\" value=\"...\" />");
                    }
                }
            }
        }
    }

    /**
     * 解析参数元素<dubbo:parameter/>的值，并将键值对设置到Map中
     */
    @SuppressWarnings("unchecked")
    private static ManagedMap parseParameters(NodeList nodeList, RootBeanDefinition beanDefinition, ParserContext parserContext) {
        if (nodeList == null) { //NodeList表示一个有顺序的节点列表
            return null;
        }
        ManagedMap parameters = null; //托管的Map，用来保存map的值
        for (int i = 0; i < nodeList.getLength(); i++) { //可以有多个元素，如：<dubbo:parameter>
            if (!(nodeList.item(i) instanceof Element)) { //若元素不是Element实例，则不处理
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("parameter".equals(element.getNodeName())
                    || "parameter".equals(element.getLocalName())) { //只处理<dubbo:parameter/>
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String key = resolveAttribute(element, "key", parserContext); //解析<dubbo:parameter> 元素中的key
                String value = resolveAttribute(element, "value", parserContext); //解析<dubbo:parameter> 元素中的value
                boolean hide = "true".equals(resolveAttribute(element, "hide", parserContext));
                if (hide) { //是否隐藏，若需要隐藏，加上前缀
                    key = HIDE_KEY_PREFIX + key;
                }
                parameters.put(key, new TypedStringValue(value, String.class)); //多个 <dubbo:parameter>时，若key重复，value值会被覆盖（TypedStringValue：存储了string值，已经要转换的目标类型，类型转换由bean工厂处理）
            }
        }
        return parameters; //此处没有直接添加到bean的属性中，而是在DubboBeanDefinitionParser#parse方法的最后添加的
    }

    @SuppressWarnings("unchecked")
    private static void parseMethods(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                     ParserContext parserContext) { //解析元素下的<dubbo:method/>子元素，获取对应类型的bean列表，作为元素的属性"methods"的值
        if (nodeList == null) {
            return;
        }
        ManagedList methods = null; //托管bean的列表
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) { //NodeList节点列表中，可能包含文本、元素等，过滤掉只处理元素Element
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("method".equals(element.getNodeName()) || "method".equals(element.getLocalName())) { //处理<dubbo:method/>元素
                String methodName = resolveAttribute(element, "name", parserContext);
                if (StringUtils.isEmpty(methodName)) { //方法名是必须的
                    throw new IllegalStateException("<dubbo:method> name attribute == null");
                }
                if (methods == null) {
                    methods = new ManagedList();
                }
                RootBeanDefinition methodBeanDefinition = parse(element,
                        parserContext, MethodConfig.class, false); //解析<dubbo:method/>元素，获取对应的Bean
                String beanName = id + "." + methodName; //构建<dubbo:method>对应的bean的名称

                // If the PropertyValue named "id" can't be found,
                // bean name will be taken as the "id" PropertyValue for MethodConfig
                if (!hasPropertyValue(methodBeanDefinition, "id")) { // 若没有包含属性id，则为bean添加id属性值
                    addPropertyValue(methodBeanDefinition, "id", beanName);
                }

                BeanDefinitionHolder methodBeanDefinitionHolder = new BeanDefinitionHolder(
                        methodBeanDefinition, beanName); //将bean保存到BeanDefinitionHolder中
                methods.add(methodBeanDefinitionHolder);
            }
        }
        if (methods != null) { //此处的beanDefinition，如果是解析<dubbo:service>中的<dubbo:method>，则为Root bean: org.apache.dubbo.config.spring.ServiceBean
            beanDefinition.getPropertyValues().addPropertyValue("methods", methods); //为Bean添加methods属性值
        }
    }

    // 判断XML的Bean的属性Set中是否包含指定的属性
    private static boolean hasPropertyValue(AbstractBeanDefinition beanDefinition, String propertyName) {
        return beanDefinition.getPropertyValues().contains(propertyName);
    }

    // 为Bean添加属性值
    private static void addPropertyValue(AbstractBeanDefinition beanDefinition, String propertyName, String propertyValue) {
        if (StringUtils.isBlank(propertyName) || StringUtils.isBlank(propertyValue)) {
            return;
        }
        beanDefinition.getPropertyValues().addPropertyValue(propertyName, propertyValue);
    }

    /**
     * 解析<dubbo:argument>元素，并添加到Bean的属性中
     */
    @SuppressWarnings("unchecked")
    private static void parseArguments(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                       ParserContext parserContext) {
        if (nodeList == null) {
            return;
        }
        ManagedList arguments = null; //托管的bean列表（与<dubbo:method/>处理逻辑相似）
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) { //非元素Element，不处理
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("argument".equals(element.getNodeName()) || "argument".equals(element.getLocalName())) {
                String argumentIndex = resolveAttribute(element, "index", parserContext);
                if (arguments == null) {
                    arguments = new ManagedList();
                }
                BeanDefinition argumentBeanDefinition = parse(element, //获取ArgumentConfig对应的Bean
                        parserContext, ArgumentConfig.class, false);
                String name = id + "." + argumentIndex;
                BeanDefinitionHolder argumentBeanDefinitionHolder = new BeanDefinitionHolder(
                        argumentBeanDefinition, name);
                arguments.add(argumentBeanDefinitionHolder);
            }
        }
        if (arguments != null) {
            beanDefinition.getPropertyValues().addPropertyValue("arguments", arguments); //为元素添加属性arguments的值
        }
    }

    /**
     * 流程分析：进入Dubbo自定义元素解析的流程
     * 1）Spring容器启动，加载xml文件，读取到自定义的元素时，就会通过查找META-INF/spring.handles找到命名空间处理类的类名，并对应创建对象实例。
     * 2）Spring会先回调DubboNamespaceHandler的init方法，方法中会将元素的本地名称与自定义元素解析器DubboBeanDefinitionParser注册到NamespaceHandlerSupport中的parsers缓存中
     * 3）Spring再回调DubboNamespaceHandler的parse方法，该方法中会先注册基础设置的注解解析器Bean，以及通用功能的Bean，如DubboBootstrapApplicationListener
     *    然后通过super.parse(...)调用NamespaceHandlerSupport的解析逻辑，该方法会从parses缓存中，根据当前解析的元素的本地名localName找到对应的解析器DubboBeanDefinitionParser
     *    最终将元素信息Element、和解析的上下文信息ParseContext，回传给自定义解析器。自定义解析器按自定义逻辑解析，并生成BeanDefinition，交由给NamespaceHandlerSupport
     */
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) { //解析XML的元素，生成Spring的Bean实例
        return parse(element, parserContext, beanClass, required); //element、parserContext是解析元素时，spring回传的参数，beanClass、required是dubbo自定义参数
    }

      /**
     * 解析元素中的属性值 如：<dubbo:application name="test"/> ，name的属性值为test
     * <p>
     * Environment：相关信息（Interface representing the environment in which the current application is running.）
     * 1）Environment表示当前应用程序正在运行的环境。Environment接口继承自PropertyResolver，所以它既能处理属性值、也能处理配置Profile
     * 2）属性管理核心API信息
     * 核心API主要包括下面4个部分：
     * PropertySource：属性源。key-value属性对抽象
     * PropertyResolver：属性解析器。用于解析相应key的value
     * Profile：配置。只有激活的配置profile的组件/配置才会注册到Spring容器，类似于maven中profile
     * Environment：环境，本身也是个属性解析器PropertyResolver。它在基础上还提供了Profile特性，能够很好的对多环境支持。
     * 因此我们一般使用它，而不是底层接口PropertyResolver。 可以简单粗暴的把它理解为Profile 和 PropertyResolver 的组合
     * https://blog.csdn.net/f641385712/article/details/94402262
     * <p>
     * 3）用来表示整个应用运行时的环境，为了更形象地理解Environment，你可以把Spring应用的运行时简单地想象成两个部分：
     * 一个是Spring应用本身，一个是Spring应用所处的环境。Environment在容器中是一个抽象的集合，是指应用环境的2个方面：profiles和properties。
     * https://www.jianshu.com/p/5f10192eb958  Spring--Environment类
     * <p>
     * 4）https://blog.csdn.net/qq_33366098/article/details/105630266 Environment接口体系
     * <p>
     * 5）https://www.cnblogs.com/binarylei/p/10284826.html Spring PropertyResolver 占位符解析
     */
    private static String resolveAttribute(Element element, String attributeName, ParserContext parserContext) { //解析属性值（属性值若有占位符，则从环境中获取并设置）
        String attributeValue = element.getAttribute(attributeName); //获取元素中，指定属性名对应的属性值
        Environment environment = parserContext.getReaderContext().getEnvironment(); //Environment当前的实例对象是StandardEnvironment
        return environment.resolvePlaceholders(attributeValue); //替换占位符

    }

    /**
     * The Node interface is the primary datatype for the entire Document Object Model
     * (Node接口是整个文档对象模型的主要数据类型)
     *
     * Element接口继承了Node接口
     * The Element interface represents an element in an HTML or XML document. Elements may have attributes associated with them
     * (Element接口表示HTML或XML文档中的元素。元素可能具有与之关联的属性)
     *
     * Environment: Interface representing the environment in which the current application is running
     * (表示当前应用程序运行环境的接口)
     */
}
