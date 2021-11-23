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
package org.apache.dubbo.config.spring.beans.factory.config;

import org.springframework.beans.BeanMetadataAttributeAccessor;
import org.springframework.beans.BeanMetadataElement;

/**
 * Configurable（可配置的） the {@link BeanMetadataAttributeAccessor#setSource(Object) source} for {@link BeanMetadataElement}
 *
 * @since 2.7.5
 */
public interface ConfigurableSourceBeanMetadataElement {

    /**
     * Set the source into the specified {@link BeanMetadataElement}
     *
     * @param beanMetadataElement {@link BeanMetadataElement} instance
     */
    default void setSource(BeanMetadataElement beanMetadataElement) {
        if (beanMetadataElement instanceof BeanMetadataAttributeAccessor) { //todo @csy-11/23-P2 此处的功能用途是什么？
            BeanMetadataAttributeAccessor.class.cast(beanMetadataElement).setSource(this);
        }
    }

    /**
     * 相关概念：
     * 1）org.springframework.beans.BeanMetadataElement接口，用于获取定义Bean的源对象，在实现类中通过Object对象保存，
     * 所谓的源对象就是定义这个Bean的资源（XML标签对象或者.class文件资源对象）
     *
     * 2）Spring Bean 的“前身”为 BeanDefinition 对象，里面包含了 Bean 的元信息，后续在 Bean 的生命周期中会根据该对象进行实例化和初始化等工作
     * BeanDefinition 接口的实现类主要根据 Bean 的定义方式进行区分，如下：
     * XML定义Bean：GenericBeanDefinition
     * @Component以及派生注解定义Bean：ScannedGenericBeanDefinition
     * 借助于@Import导入Bean：AnnotatedGenericBeanDefinition
     * @Bean定义的方法：ConfigurationClassBeanDefinition 私有静态类
     *
     * 3）Spring 中通常以这两种方式定义一个 Bean：面向资源（XML、Properties）、面向注解
     *
     * https://zhuanlan.zhihu.com/p/352440575
     */
}
