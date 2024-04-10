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

import org.apache.dubbo.common.Version;
import org.apache.dubbo.config.*;
import org.apache.dubbo.config.spring.ConfigCenterBean;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.beans.factory.config.ConfigurableSourceBeanMetadataElement;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.w3c.dom.Element;

import static org.apache.dubbo.config.spring.util.DubboBeanUtils.registerCommonBeans;

 /**
 * DubboNamespaceHandler 命名空间处理类
 *
 * @export
 */
public class DubboNamespaceHandler extends NamespaceHandlerSupport implements ConfigurableSourceBeanMetadataElement {
     /**
      * 知识点：Spring中的自定义标签
      *
      * 概要总结：
      * 1）注册自定义的bean解析器，用来解析自定义元素（在调用自定义元素前初始化）
      * （元素名是不带命名空间的，如<dubbo:application> 元素为application）
      *
      * 2）NamespaceHandler：命名空间处理器
      *    a）Spring为了开放性提供了NamespaceHandler机制，这样我们就可以根据需求自己来处理我们设置的标签元素。
      *    b）NamespaceHandler是一个处理器，该处理器负责，将该命名空间下的所有解析器进行都注册。然后根据Element找到合适的解析器进行解析元素。具体解析交由对应的解析器来处理
      *
      * 参考链接
      *    https://juejin.cn/post/6844903665262657544 NamespaceHandler使用
      */

    static { //static在类加载时，Version中有静态方法，会先执行Version.checkDuplicate(Version.class)，再执行Version.checkDuplicate(DubboNamespaceHandler.class);
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }


     /**
      * 流程分析：Spring容器找到DubboNamespaceHandler的流程
      * 1）Spring容器读取XML文件，遍历节点Node，依次获取Node关联的命名空间的uri，如"http://dubbo.apache.org/schema/dubbo"
      *    与Spring的uri比较，若不同则为自定义命名空间，即与"http://www.springframework.org/schema/beans"
      * 2）读取Spring约定META-INF下的spring.handlers文件，取出以Node的uri为key的值，即为命名空间处理类的类名，如：org.apache.dubbo.config.spring.schema.DubboNamespaceHandler
      *    流程分析：读取spring.handler文件的流程（按从dubbo-demo启动分析，可参考dubbo-common下自定义的文件）
      *        a）dubbo-demo依赖了dubbo-bom，而dubbo-bom模块引入了所有dubbo模块。
      *        b）Spring容器在读取xml文件时，会读取关联模块下的META-INF/spring.handlers和META-INF/spring.schemas
      *        c）会按照classLoader.getResources(resourceName) 或ClassLoader.getSystemResources(resourceName)方式加载到引用得所有模块的所有文件(包含引入的jar，如Spring的文件)
      * 3）然后通过反射机制，创建命名空间处理类的对象实例，先调用NamespaceHandler的init()方法，然后再调用parse()方法
      */
     @Override
    public void init() { //在解析自定义元素前，进行初始化操作（设置父类NamespaceHandlerSupport的Map<String, BeanDefinitionParser> parsers，即设置元素名与bean解析器的映射）
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("config-center", new DubboBeanDefinitionParser(ConfigCenterBean.class, true));
        registerBeanDefinitionParser("metadata-report", new DubboBeanDefinitionParser(MetadataReportConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("metrics", new DubboBeanDefinitionParser(MetricsConfig.class, true));
        registerBeanDefinitionParser("ssl", new DubboBeanDefinitionParser(SslConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true)); //将元素名与对应的bean进行对应
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser()); //对应注解解析器
    }

    /**
     * 解析自定义元素
     *
     * Override {@link NamespaceHandlerSupport#parse(Element, ParserContext)} method
     *
     * @param element       {@link Element}
     * @param parserContext {@link ParserContext}
     * @return
     * @since 2.7.5
     */
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) { //Spring解析自定义元素时回调的方法
        BeanDefinitionRegistry registry = parserContext.getRegistry(); //BeanDefinitionRegistry：用来保存Bean的类，此处的实例为DefaultListableBeanFactory@xxx（通过debug，可看到注册的所有Bean）
        registerAnnotationConfigProcessors(registry); //注册基础的注解处理器Bean
        /**
         * @since 2.7.8
         * issue : https://github.com/apache/dubbo/issues/6275
         */
        registerCommonBeans(registry); //注册基础设施的Bean（除Config对象外的Bean，不需要通过自定义解析器DubboBeanDefinitionParser解析）
        BeanDefinition beanDefinition = super.parse(element, parserContext); //调用父类的parse()方法解析，父类中会找到init()设置的解析器，再调用解析器的parse()解析元素
        setSource(beanDefinition); //设置源对象，将beanDefinition设置到BeanMetadataAttributeAccessor
        return beanDefinition;
    }

     /**
     * 注册基础设施的注解处理器Bean
     * Register the processors for the Spring Annotation-Driven features
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @see AnnotationConfigUtils
     * @since 2.7.5
     */
    private void registerAnnotationConfigProcessors(BeanDefinitionRegistry registry) {
        AnnotationConfigUtils.registerAnnotationConfigProcessors(registry);
    }
}
