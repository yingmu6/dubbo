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
package org.apache.dubbo.config.spring.context;

import org.apache.dubbo.config.bootstrap.DubboBootstrap;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

/**
 * The {@link ApplicationListener} for {@link DubboBootstrap}'s lifecycle when the {@link ContextRefreshedEvent}
 * and {@link ContextClosedEvent} raised
 *
 * @since 2.7.5
 */
public class DubboBootstrapApplicationListener extends OneTimeExecutionApplicationContextEventListener
        implements Ordered { //Dubbo启动类的监听器（监听spring容器启动事件）

    /**
     * The bean name of {@link DubboBootstrapApplicationListener}
     *
     * @since 2.7.6
     */
    public static final String BEAN_NAME = "dubboBootstrapApplicationListener";

    private final DubboBootstrap dubboBootstrap;

    /**
     * 流程分析：服务启动时，DubboBootstrapApplicationListener是如何初始化的
     * 1）在进入DubboNamespaceHandler的parse解析元素时，会调用DubboBeanUtils#registerCommonBeans方法，生成DubboBootstrapApplicationListener对应的bean
     * 2）DubboLifecycleComponentApplicationListener也是通过registerCommonBeans创建的Bean实例
     * 3）要操作对象的方法，就必须先创建对象，所以要先确定对象是在何时创建的
     */
    public DubboBootstrapApplicationListener() {
        this.dubboBootstrap = DubboBootstrap.getInstance();
    }

    /**
     * 流程分析：服务启动时，监听到Spring容器事件经历的过程
     * 1）Spring解析配置的XML，会进入DubboNamespaceHandler，实例化自定义的Config对象
     * 2）然后调用当前DubboBootstrapApplicationListener的构造方法，创建DubboBootstrap的对象实例
     * 3）Spring初始化之后，发出容器刷新的事件，就进入了当前onApplicationContextEvent方法
     */
    @Override
    public void onApplicationContextEvent(ApplicationContextEvent event) { //spring容器事件发生时处理
        if (event instanceof ContextRefreshedEvent) { //当容器初始化完成或重新刷新时执行
            onContextRefreshedEvent((ContextRefreshedEvent) event);
        } else if (event instanceof ContextClosedEvent) { //当容器关闭时执行
            onContextClosedEvent((ContextClosedEvent) event);
        }
    }

    private void onContextRefreshedEvent(ContextRefreshedEvent event) {
        dubboBootstrap.start(); // dubbo服务的启动交给了DubboBootstrap
    }

    private void onContextClosedEvent(ContextClosedEvent event) {
        dubboBootstrap.stop();
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
