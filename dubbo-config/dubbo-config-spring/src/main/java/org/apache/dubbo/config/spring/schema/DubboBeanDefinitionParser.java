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
import org.apache.dubbo.config.AbstractServiceConfig;
import org.apache.dubbo.config.ArgumentConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
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
    private final Class<?> beanClass; // bean的名称
    private final boolean required;   // 是否是必须的

    public DubboBeanDefinitionParser(Class<?> beanClass, boolean required) { // 在DubboNamespaceHandler的init方法写入成员变量的
        this.beanClass = beanClass;
        this.required = required;
    }

    @SuppressWarnings("unchecked")
    private static RootBeanDefinition parse(Element element, ParserContext parserContext, Class<?> beanClass, boolean required) { //ParserContext：通过bean定义解析过程传递的上下文，封装所有相关配置和状态，嵌套在XmlReaderContext内
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(beanClass);
        beanDefinition.setLazyInit(false);
        String id = resolveAttribute(element, "id", parserContext); //解析属性名对应的值
        /**
         * 处理属性id的值，若属性id为空且是必须的，则尝试获取name、interface对应的值，若还为空则获取bean的名称
         */
        if (StringUtils.isEmpty(id) && required) {
            String generatedBeanName = resolveAttribute(element, "name", parserContext);
            if (StringUtils.isEmpty(generatedBeanName)) {
                if (ProtocolConfig.class.equals(beanClass)) {
                    generatedBeanName = "dubbo";
                } else {
                    generatedBeanName = resolveAttribute(element, "interface", parserContext);
                }
            }
            if (StringUtils.isEmpty(generatedBeanName)) {
                generatedBeanName = beanClass.getName();
            }
            id = generatedBeanName;
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) { //若bean已存在，则加上计数标识
                id = generatedBeanName + (counter++);
            }
        }
        if (StringUtils.isNotEmpty(id)) {
            if (parserContext.getRegistry().containsBeanDefinition(id)) {
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            parserContext.getRegistry().registerBeanDefinition(id, beanDefinition);
            beanDefinition.getPropertyValues().addPropertyValue("id", id);
        }
        /**
         * 对指定的bean进行处理，如ProtocolConfig、ServiceBean、ProviderConfig、ConsumerConfig等
         */
        if (ProtocolConfig.class.equals(beanClass)) {
            for (String name : parserContext.getRegistry().getBeanDefinitionNames()) { //遍历已注册的bean对应的名称列表，比如<dubbo:protocol name="dubbo"/>对应的bean的name为"dubbo"等
                BeanDefinition definition = parserContext.getRegistry().getBeanDefinition(name);
                PropertyValue property = definition.getPropertyValues().getPropertyValue("protocol");
                if (property != null) {
                    Object value = property.getValue();
                    if (value instanceof ProtocolConfig && id.equals(((ProtocolConfig) value).getName())) {
                        definition.getPropertyValues().addPropertyValue("protocol", new RuntimeBeanReference(id));
                    }
                }
            }
        } else if (ServiceBean.class.equals(beanClass)) {
            String className = resolveAttribute(element, "class", parserContext);
            if (StringUtils.isNotEmpty(className)) {
                RootBeanDefinition classDefinition = new RootBeanDefinition(); //根bean定义也可以用于注册单个bean定义
                classDefinition.setBeanClass(ReflectUtils.forName(className));
                classDefinition.setLazyInit(false);
                parseProperties(element.getChildNodes(), classDefinition, parserContext);
                beanDefinition.getPropertyValues().addPropertyValue("ref", new BeanDefinitionHolder(classDefinition, id + "Impl")); //BeanDefinitionHolder：带有名称和别名的bean定义的Holder
                // 此处的bean实例，会在bean的名称后面加上Impl，如：org.apache.dubbo.demo.DemoService + "Impl"，对应的实现类是org.apache.dubbo.demo.DemoServiceImpl
            }
        } else if (ProviderConfig.class.equals(beanClass)) {
            parseNested(element, parserContext, ServiceBean.class, true, "service", "provider", id, beanDefinition);
        } else if (ConsumerConfig.class.equals(beanClass)) {
            parseNested(element, parserContext, ReferenceBean.class, false, "reference", "consumer", id, beanDefinition);
        }
        Set<String> props = new HashSet<>();
        ManagedMap parameters = null;
        for (Method setter : beanClass.getMethods()) {
            String name = setter.getName();
            if (name.length() > 3 && name.startsWith("set")
                    && Modifier.isPublic(setter.getModifiers())
                    && setter.getParameterTypes().length == 1) { //遍历bean中的set方法，只处理一个参数的setter方法
                Class<?> type = setter.getParameterTypes()[0];
                String beanProperty = name.substring(3, 4).toLowerCase() + name.substring(4); //解析出属性名，如方法名为setName，属性名为name
                String property = StringUtils.camelToSplitName(beanProperty, "-");
                props.add(property);
                // check the setter/getter whether match (检查set、get方法是否匹配)
                Method getter = null;
                try {
                    getter = beanClass.getMethod("get" + name.substring(3), new Class<?>[0]);//获取指定的方法对应的Method，如getName
                } catch (NoSuchMethodException e) {
                    try {
                        getter = beanClass.getMethod("is" + name.substring(3), new Class<?>[0]); //处理is开头的方法，比如ApplicationConfig中的isDefault()方法
                    } catch (NoSuchMethodException e2) { //允许没有getter的方法，比如EnvironmentAware，只有setter方法，不能抛出异常，不然引起应用启动失败
                        // ignore, there is no need any log here since some class implement the interface: EnvironmentAware,
                        // ApplicationAware, etc. They only have setter method, otherwise will cause the error log during application start up.
                    }
                }
                if (getter == null
                        || !Modifier.isPublic(getter.getModifiers())
                        || !type.equals(getter.getReturnType())) { //set、get方法不匹配时，本次不处理，跳到下次循环
                    continue;
                }
                if ("parameters".equals(property)) {
                    parameters = parseParameters(element.getChildNodes(), beanDefinition, parserContext);
                } else if ("methods".equals(property)) {
                    parseMethods(id, element.getChildNodes(), beanDefinition, parserContext);
                } else if ("arguments".equals(property)) {
                    parseArguments(id, element.getChildNodes(), beanDefinition, parserContext);
                } else {
                    String value = resolveAttribute(element, property, parserContext);
                    if (value != null) { //若值为null或者""，则不处理
                        value = value.trim();
                        if (value.length() > 0) {
                            if ("registry".equals(property) && RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(value)) {
                                RegistryConfig registryConfig = new RegistryConfig();
                                registryConfig.setAddress(RegistryConfig.NO_AVAILABLE);
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty, registryConfig);
                            } else if ("provider".equals(property) || "registry".equals(property) || ("protocol".equals(property) && AbstractServiceConfig.class.isAssignableFrom(beanClass))) {
                                /**
                                 * For 'provider' 'protocol' 'registry', keep literal value (should be id/name) and set the value to 'registryIds' 'providerIds' protocolIds'
                                 * The following process should make sure each id refers to the corresponding instance, here's how to find the instance for different use cases:
                                 * 1. Spring, check existing bean by id, see{@link ServiceBean#afterPropertiesSet()}; then try to use id to find configs defined in remote Config Center
                                 * 2. API, directly use id to find configs defined in remote Config Center; if all config instances are defined locally, please use {@link ServiceConfig#setRegistries(List)}
                                 */
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty + "Ids", value);
                            } else {
                                Object reference;
                                if (isPrimitive(type)) { //属性类型为基本类型，并且为指定的值，则将值值为null
                                    if ("async".equals(property) && "false".equals(value) //若为指定的属性和属性值，则设置value为null
                                            || "timeout".equals(property) && "0".equals(value) //每个判断条件作为一行，清晰明了
                                            || "delay".equals(property) && "0".equals(value)
                                            || "version".equals(property) && "0.0.0".equals(value)
                                            || "stat".equals(property) && "-1".equals(value)
                                            || "reliable".equals(property) && "false".equals(value)) {
                                        // backward compatibility for the default value in old version's xsd
                                        value = null;
                                    }
                                    reference = value;
                                } else if (ONRETURN.equals(property) || ONTHROW.equals(property) || ONINVOKE.equals(property)) {
                                    int index = value.lastIndexOf(".");
                                    String ref = value.substring(0, index);
                                    String method = value.substring(index + 1);
                                    reference = new RuntimeBeanReference(ref);
                                    beanDefinition.getPropertyValues().addPropertyValue(property + METHOD, method);
                                } else {
                                    if ("ref".equals(property) && parserContext.getRegistry().containsBeanDefinition(value)) {
                                        BeanDefinition refBean = parserContext.getRegistry().getBeanDefinition(value);
                                        if (!refBean.isSingleton()) {
                                            throw new IllegalStateException("The exported service ref " + value + " must be singleton! Please set the " + value + " bean scope to singleton, eg: <bean id=\"" + value + "\" scope=\"singleton\" ...>");
                                        }
                                    }
                                    reference = new RuntimeBeanReference(value);
                                }
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty, reference); //将属性名、属性值组装成PropertyValue对象，放到List<PropertyValue>属性值列表中
                            }
                        }
                    }
                }
            }
        }
        NamedNodeMap attributes = element.getAttributes();
        int len = attributes.getLength();
        for (int i = 0; i < len; i++) {
            Node node = attributes.item(i);
            String name = node.getLocalName();
            if (!props.contains(name)) {
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String value = node.getNodeValue();
                parameters.put(name, new TypedStringValue(value, String.class));
            }
        }
        if (parameters != null) {
            beanDefinition.getPropertyValues().addPropertyValue("parameters", parameters);
        }
        return beanDefinition;
    }

    private static boolean isPrimitive(Class<?> cls) { //Class中isPrimitive()，判断是否是基本类型，Class中native方法，在Class的基础上做了封装，加上额外的类型
        return cls.isPrimitive() || cls == Boolean.class || cls == Byte.class
                || cls == Character.class || cls == Short.class || cls == Integer.class
                || cls == Long.class || cls == Float.class || cls == Double.class
                || cls == String.class || cls == Date.class || cls == Class.class;
    }

    /**
     * 解析内嵌元素
     */
    private static void parseNested(Element element, ParserContext parserContext, Class<?> beanClass, boolean required, String tag, String property, String ref, BeanDefinition beanDefinition) {
        NodeList nodeList = element.getChildNodes();
        if (nodeList == null) {
            return;
        }
        boolean first = true;
        for (int i = 0; i < nodeList.getLength(); i++) { //可以有多个内嵌节点
            Node node = nodeList.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            if (tag.equals(node.getNodeName()) //带上命名空间的节点名称，如dubbo:service
                    || tag.equals(node.getLocalName())) { //去掉命名空间的名称，如service
                if (first) {
                    first = false;
                    String isDefault = resolveAttribute(element, "default", parserContext);
                    if (StringUtils.isEmpty(isDefault)) { //处理默认属性default
                        beanDefinition.getPropertyValues().addPropertyValue("default", "false");
                    }
                }
                // 内部嵌套的元素，按单个元素解析的方式依次解析
                BeanDefinition subDefinition = parse((Element) node, parserContext, beanClass, required);
                if (subDefinition != null && StringUtils.isNotEmpty(ref)) {
                    subDefinition.getPropertyValues().addPropertyValue(property, new RuntimeBeanReference(ref));
                }
            }
        }
    }

    /**
     * 解析属性（解析元素属性值，并存入PropertyValue对象）
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
            if ("property".equals(element.getNodeName()) //只对属性节点处理
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

    @SuppressWarnings("unchecked")
    private static ManagedMap parseParameters(NodeList nodeList, RootBeanDefinition beanDefinition, ParserContext parserContext) {
        if (nodeList == null) {
            return null;
        }
        ManagedMap parameters = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("parameter".equals(element.getNodeName())
                    || "parameter".equals(element.getLocalName())) {
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String key = resolveAttribute(element, "key", parserContext);
                String value = resolveAttribute(element, "value", parserContext);
                boolean hide = "true".equals(resolveAttribute(element, "hide", parserContext));
                if (hide) {
                    key = HIDE_KEY_PREFIX + key;
                }
                parameters.put(key, new TypedStringValue(value, String.class));
            }
        }
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private static void parseMethods(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                     ParserContext parserContext) {
        if (nodeList == null) {
            return;
        }
        ManagedList methods = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("method".equals(element.getNodeName()) || "method".equals(element.getLocalName())) {
                String methodName = resolveAttribute(element, "name", parserContext);
                if (StringUtils.isEmpty(methodName)) {
                    throw new IllegalStateException("<dubbo:method> name attribute == null");
                }
                if (methods == null) {
                    methods = new ManagedList();
                }
                RootBeanDefinition methodBeanDefinition = parse(element,
                        parserContext, MethodConfig.class, false);
                String beanName = id + "." + methodName;

                // If the PropertyValue named "id" can't be found,
                // bean name will be taken as the "id" PropertyValue for MethodConfig
                if (!hasPropertyValue(methodBeanDefinition, "id")) { // 若没有包含属性，则添加属性
                    addPropertyValue(methodBeanDefinition, "id", beanName);
                }

                BeanDefinitionHolder methodBeanDefinitionHolder = new BeanDefinitionHolder(
                        methodBeanDefinition, beanName);
                methods.add(methodBeanDefinitionHolder);
            }
        }
        if (methods != null) {
            beanDefinition.getPropertyValues().addPropertyValue("methods", methods);
        }
    }

    // 判断是否包含属性名
    private static boolean hasPropertyValue(AbstractBeanDefinition beanDefinition, String propertyName) {
        return beanDefinition.getPropertyValues().contains(propertyName);
    }

    private static void addPropertyValue(AbstractBeanDefinition beanDefinition, String propertyName, String propertyValue) {
        if (StringUtils.isBlank(propertyName) || StringUtils.isBlank(propertyValue)) {
            return;
        }
        beanDefinition.getPropertyValues().addPropertyValue(propertyName, propertyValue);
    }

    @SuppressWarnings("unchecked")
    private static void parseArguments(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                       ParserContext parserContext) {
        if (nodeList == null) {
            return;
        }
        ManagedList arguments = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("argument".equals(element.getNodeName()) || "argument".equals(element.getLocalName())) {
                String argumentIndex = resolveAttribute(element, "index", parserContext);
                if (arguments == null) {
                    arguments = new ManagedList();
                }
                BeanDefinition argumentBeanDefinition = parse(element,
                        parserContext, ArgumentConfig.class, false);
                String name = id + "." + argumentIndex;
                BeanDefinitionHolder argumentBeanDefinitionHolder = new BeanDefinitionHolder(
                        argumentBeanDefinition, name);
                arguments.add(argumentBeanDefinitionHolder);
            }
        }
        if (arguments != null) {
            beanDefinition.getPropertyValues().addPropertyValue("arguments", arguments);
        }
    }

    /**
     * 实现spring BeanDefinitionParser的方法，获取到解析的元素和解析的内容，就可以按照自定义的解析方式进行解析
     */
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        return parse(element, parserContext, beanClass, required);
    }

    /**
     * 解析元素中的属性值 如：<dubbo:application name="test"/> ，name的属性值为test
     */
    private static String resolveAttribute(Element element, String attributeName, ParserContext parserContext) {
        String attributeValue = element.getAttribute(attributeName); //获取元素中，指定属性名对应的属性值
        Environment environment = parserContext.getReaderContext().getEnvironment();
        return environment.resolvePlaceholders(attributeValue);
    }
}
