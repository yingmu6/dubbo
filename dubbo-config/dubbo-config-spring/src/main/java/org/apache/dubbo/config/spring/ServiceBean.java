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
package org.apache.dubbo.config.spring;

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.spring.context.event.ServiceBeanExportedEvent;
import org.apache.dubbo.config.spring.extension.SpringExtensionFactory;
import org.apache.dubbo.config.support.Parameter;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * ServiceFactoryBean
 *
 * @export
 */
public class ServiceBean<T> extends ServiceConfig<T> implements InitializingBean, DisposableBean,
        ApplicationContextAware, BeanNameAware, ApplicationEventPublisherAware {
    /**
     * InitializingBean：（Initializing：美[ɪˈnɪʃəlaɪzɪŋ] 正在初始化的）
     * 1）bean初始化后，回调afterPropertiesSet()方法（在属性设置后才调用的）
     * 2）spring为bean提供了两种初始化bean的方式，实现InitializingBean接口，实现afterPropertiesSet方法，
     * 或者在配置文件中同过init-method指定，两种方式可以同时使用
     * https://www.jianshu.com/p/f0af22d671a5
     * <p>
     * DisposableBean：（Disposable：英 [dɪˈspəʊzəbl] 用完即可丢弃的）
     * 1）在bean销毁时，回调destroy()
     * 2）与在配置文件中指令destroy-method类似功能
     * <p>
     * ApplicationContextAware：
     * 1）Aware接口的Bean在被初始之后，可以取得一些相对应的资源。
     * Aware接口本身并不具备什么功能，一般是用于子类继承后，Spring上下文初始化bean的时候会对这个bean传入需要的资源。
     * 例如ApplicationContextAware接口，可以在Spring初始化实例 Bean的时候，可以通过这个接口将当前的Spring上下文传入。
     * 2）ApplicationContextAware的最本质的应用就是：对当前bean传入对应的Spring上下文。
     * a）保存Spring上下文，b）监听上下文启动，并完成相关操作
     * <p>
     * BeanNameAware：
     * 1）如果某个bean需要访问配置文件中本身bean的id属性，这个Bean类通过实现该接口，
     * 在依赖关系确定之后，初始化方法之前，提供回调自身的能力，从而获得本身bean的id属性
     * <p>
     * ApplicationEventPublisherAware：事件发布
     * 1）事件的发布者发布事件，事件的监听这对对应的事件进行监听，当监听到对应的事件时，
     * 就会触发调用相关的方法。因此，在事件处理中，事件是核心，是事件发布者和事件监听者的桥梁。
     * 2）Spring事件机制是观察者模式的一种实现，但是除了发布者和监听者者两个角色之外，还有一个EventMultiCaster的角色负责把事件转发给监听者
     * https://www.jianshu.com/p/dcbe8f0afbdb
     */

    private static final long serialVersionUID = 213195494150089726L;

    private final transient Service service;

    private transient ApplicationContext applicationContext;

    private transient String beanName;

    private ApplicationEventPublisher applicationEventPublisher;

    public ServiceBean() {
        super();
        this.service = null;
    }

    public ServiceBean(Service service) {
        super(service);
        this.service = service;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) { //ApplicationContext: Central interface to provide configuration for an application (核心接口，为应用程序提供配置)
        this.applicationContext = applicationContext; //保存spring上下文
        SpringExtensionFactory.addApplicationContext(applicationContext);
    }

    @Override
    public void setBeanName(String name) { //会设置bean的名称，比如org.apache.dubbo.demo.DemoService
        this.beanName = name;
    }

    /**
     * Gets associated {@link Service}
     *
     * @return associated {@link Service}
     */
    public Service getService() {
        return service;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (StringUtils.isEmpty(getPath())) { //若没有设置path，则使用接口名称进行设置
            if (StringUtils.isNotEmpty(getInterface())) {
                setPath(getInterface());
            }
        }
    }

    /**
     * Get the name of {@link ServiceBean}
     *
     * @return {@link ServiceBean}'s name
     * @since 2.6.5
     */
    @Parameter(excluded = true)
    public String getBeanName() {
        return this.beanName;
    }

    /**
     * @since 2.6.5
     */
    @Override
    public void exported() {
        super.exported();
        // Publish ServiceBeanExportedEvent
        publishExportEvent();
    }

    /**
     * @since 2.6.5
     */
    private void publishExportEvent() {
        ServiceBeanExportedEvent exportEvent = new ServiceBeanExportedEvent(this);
        applicationEventPublisher.publishEvent(exportEvent); //发布事件
    }

    @Override
    public void destroy() throws Exception {
        // no need to call unexport() here, see
        // org.apache.dubbo.config.spring.extension.SpringExtensionFactory.ShutdownHookListener
    }

    // merged from dubbox
    @Override
    protected Class getServiceClass(T ref) {
        if (AopUtils.isAopProxy(ref)) {
            return AopUtils.getTargetClass(ref);
        }
        return super.getServiceClass(ref);
    }

    /**
     * @param applicationEventPublisher
     * @since 2.6.5
     */
    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
