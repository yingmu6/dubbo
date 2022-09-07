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
    default void setSource(BeanMetadataElement beanMetadataElement) { //BeanMetadataElement：用于获取定义Bean的源对象
        if (beanMetadataElement instanceof BeanMetadataAttributeAccessor) {
            BeanMetadataAttributeAccessor.class.cast(beanMetadataElement).setSource(this); //设置源对象（所谓的源对象就是定义这个Bean的资源）
        }
    }

    /**
     * 相关概念：
     * 1）org.springframework.beans.BeanMetadataElement接口，用于获取定义Bean的源对象，在实现类中通过Object对象保存，
     * 所谓的源对象就是定义这个Bean的资源（XML标签对象或者.class文件资源对象）
     *
     * 2）Spring Bean 的“前身”为 BeanDefinition 对象，里面包含了 Bean 的元信息，后续在 Bean 的生命周期中会根据该对象进行实例化和初始化等工作
     * BeanDefinition 接口的实现类主要根据 Bean 的定义方式进行区分，如下：
     *
     *    1、XML 定义 Bean：GenericBeanDefinition
     *    2、@Component 以及派生注解定义 Bean：ScannedGenericBeanDefinition
     *    3、借助于 @Import 导入 Bean：AnnotatedGenericBeanDefinition
     *    4、@Bean 定义的方法：ConfigurationClassBeanDefinition 私有静态类
     *    上面的 1、2、3 三种 BeanDefinition 实现类具有层次性，在 Spring BeanFactory 初始化 Bean 的前阶段，会根据 BeanDefinition 生成一个合并后的 RootBeanDefinition 对象
     *
     * 3）Spring 中通常以这两种方式定义一个 Bean：面向资源（XML、Properties）、面向注解
     * https://zhuanlan.zhihu.com/p/352440575
     *
     *
     * 我们在 Spring 中通常以这两种方式定义一个 Bean：面向资源（XML、Properties）、面向注解。
     * 如今 Spring Boot 被广泛应用，通过注解定义一个 Bean 的方式变得更为普遍，因为在实际的开发过程中注解的方式相比于 XML 文件更加轻便，可以有效地提高工作效率。
     *
     *
     * 在 Spring Bean 的生命周期 可以看到，BeanDefinition 可以说是 Bean 的“前身”，首先进入 Bean 的元信息的配置、解析和注册阶段，然后才开始 Bean 的实例化和初始化等工作
     */
}
