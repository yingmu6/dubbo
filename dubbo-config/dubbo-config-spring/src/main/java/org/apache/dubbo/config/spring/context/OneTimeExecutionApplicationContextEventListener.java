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

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;

import java.util.Objects;

/**
 * The abstract class {@link ApplicationListener} for {@link ApplicationContextEvent} guarantees just one-time（一次性） execution
 * and prevents the event propagation in the hierarchical {@link ApplicationContext ApplicationContexts}
 * （该事件监听器只执行一次，在spring容器加载完成后）
 *
 * @since 2.7.5
 */
abstract class OneTimeExecutionApplicationContextEventListener implements ApplicationListener, ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * Spring的事件机制：
     * 1）ApplicationContext事件机制是观察者设计模式的实现，通过ApplicationEvent类和ApplicationListener接口，可以实现ApplicationContext事件处理。
     * 2）如果容器中有一个ApplicationListener Bean，每当ApplicationContext发布ApplicationEvent时，ApplicationListener Bean将自动被触发。这种事件机制都必须需要程序显示的触发
     * 3）其中spring有一些内置的事件，当完成某种操作时会发出某些事件动作。比如监听ContextRefreshedEvent事件，当所有的bean都初始化完成并被成功装载后会触发该事件，
     * 实现ApplicationListener<ContextRefreshedEvent>接口可以收到监听动作，然后可以写自己的逻辑
     * 4）同样事件可以自定义、监听也可以自定义，完全根据自己的业务逻辑来处理。
     * https://blog.csdn.net/liyantianmin/article/details/81017960 spring事件处理，包含内置事件的描述
     */
    public final void onApplicationEvent(ApplicationEvent event) {
        if (isOriginalEventSource(event) && event instanceof ApplicationContextEvent) {
            onApplicationContextEvent((ApplicationContextEvent) event);
        }
    }

    /**
     * The subclass overrides this method to handle {@link ApplicationContextEvent}
     *
     * @param event {@link ApplicationContextEvent}
     */
    protected abstract void onApplicationContextEvent(ApplicationContextEvent event);

    /**
     * Is original {@link ApplicationContext} as the event source
     * <p>
     * java事件源处理：
     * 1）事件源产生事件，事件带有事件源，监听器监听事件。事件驱动模型是观察者模式的升级版本
     * 2）JDK提供了EventObject类和EventListener接口定义了实现观察者模式
     * 3）在Spring中为自定义事件和自定义监听者，分别提供一个类和一个接口。
     * ApplicationEvent类继承了EventObject，用于在Spring环境下自定义事件
     * ApplicationListener接口继承JDK的EventListener，用于在Spring环境下自定义监听者
     *
     * <p>
     * 观察者模式：
     * 1）观察者模式(Observer Design Pattern)也被称为发布订阅模式(Publish-Subcribe Design Pattern)
     * 2）观察者模式定义了一种一对多的依赖关系，让多个观察者对象同时监听某一个主题对象。这个主题对象在状态变化时，会通知所有观察者对象，使它们能够自动更新自己。
     * 3）回到本质，设计模式要干的事情就是解耦。创建型模式是将创建对象和使用对象解耦，结构型模式是将不同功能代码解耦，行为型模式是将不同的行为代码解耦，具体到观察者模式，是将观察者和被观察者代码解耦。
     *
     * <p>
     * EventObject
     *
     * @param event {@link ApplicationEvent}
     * @return
     */
    private boolean isOriginalEventSource(ApplicationEvent event) {
        return (applicationContext == null) // Current ApplicationListener is not a Spring Bean, just was added
                // into Spring's ConfigurableApplicationContext
                || Objects.equals(applicationContext, event.getSource());
    }

    @Override
    public final void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
