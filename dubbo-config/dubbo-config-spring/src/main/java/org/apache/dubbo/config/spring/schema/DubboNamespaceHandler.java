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
     * 数据结构
     * 1）继承了spring的NamespaceHandlerSupport，可以解析自定义的元素
     * 2）实现ConfigurableSourceBeanMetadataElement
     * （ConfigurableSourceBeanMetadataElement用途：）
     */

    static { //static在类加载时，Version中有静态方法，会先执行Version.checkDuplicate(Version.class)，再执行Version.checkDuplicate(DubboNamespaceHandler.class);
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    /**
     * 注册自定义的bean解析器，用来解析自定义元素（在调用自定义元素前初始化）
     * （元素名是不带命名空间的，如<dubbo:application> 元素为application）
     * <p>
     * NamespaceHandler：命名空间处理器
     * 1）Spring为了开放性提供了NamespaceHandler机制，这样我们就可以根据需求自己来处理我们设置的标签元素。
     * 2）NamespaceHandler是一个处理器，该处理器负责，将该命名空间下的所有解析器进行都注册。然后根据ELement找到合适的解析器进行解析元素。具体解析交由对应的解析器来处理
     * <p>
     * 解析流程：
     * 1）在init方法中，去注册解析器，然后在解析xml时，通过约定的key去map中拿到相应的解析器去解析
     * 2）解析得到BeanDefinition，最后Spring 对相应的bean进行实例化
     * <p>
     * https://juejin.cn/post/6844903665262657544 NamespaceHandler使用
     */
    @Override
    public void init() { //设置父类NamespaceHandlerSupport的Map<String, BeanDefinitionParser> parsers，即设置元素名与bean解析器的映射
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
        /**
         * @csy-11/23-P2（11/24解） registerBeanDefinitionParser 做了什么处理？
         * 解：init()方法解析：
         * 1）重写Spring NamespaceHandler的init()方法，
         * 2）在解析XML中的命名空间url时，如xmlns:dubbo="http://dubbo.apache.org/schema/dubbo"，会调用init()方法，
         * 3）调用的地方org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver#resolve
         *
         * registerBeanDefinitionParser()方法解析：
         * 1）重写Spring NamespaceHandlerSupport#registerBeanDefinitionParser()方法
         * 2）将元素名，如"application"与对应的解析器按键值对存储起来 Map<String, BeanDefinitionParser> parsers
         * 3）注册以后当前对象DubboNamespaceHandler从NamespaceHandlerSupport继承的私有成员变量parsers就有相关值了
         */
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
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        BeanDefinitionRegistry registry = parserContext.getRegistry();
        registerAnnotationConfigProcessors(registry);
        /**
         * @since 2.7.8
         * issue : https://github.com/apache/dubbo/issues/6275
         */
        registerCommonBeans(registry);
        BeanDefinition beanDefinition = super.parse(element, parserContext);
        setSource(beanDefinition);
        return beanDefinition;
    }

    /**
     * 解析加载Spring Xml的过程：
     * https://blog.csdn.net/weixin_33747129/article/details/94609557
     *
     * Spring关于Xml的bean与Dubbo config的转化
     *
     * 进入Dubbo服务暴露的流程
     */


    /**
     * 注册注解配置
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
