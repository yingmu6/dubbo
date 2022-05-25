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
package org.apache.dubbo.config.spring.extension;

import org.apache.dubbo.config.spring.api.DemoService;
import org.apache.dubbo.config.spring.api.HelloService;
import org.apache.dubbo.config.spring.impl.DemoServiceImpl;
import org.apache.dubbo.config.spring.impl.HelloServiceImpl;
import org.apache.dubbo.rpc.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringExtensionFactoryTest {

    private SpringExtensionFactory springExtensionFactory = new SpringExtensionFactory();
    /**
     * Standalone application context, accepting annotated classes as input - in particular
     *
     * @Configuration-annotated classes, but also plain @Component types
     * （AnnotationConfigApplicationContext：独立的应用上下文，支持带有注解类的输入，比如@Configuration、@Component等）
     */
    private AnnotationConfigApplicationContext context1;
    private AnnotationConfigApplicationContext context2;

    @BeforeEach
    public void init() {
        SpringExtensionFactory.clearContexts();
        context1 = new AnnotationConfigApplicationContext();
        context1.register(getClass()); //往上下文中注册带有注解的类
        context1.refresh(); //必须调用refresh()才能处理新的类，此处若不调用refresh()，当前类中的bean1、bean2、hello就不能实例

        context2 = new AnnotationConfigApplicationContext();
        context2.register(BeanForContext2.class);
        context2.refresh();
        SpringExtensionFactory.addApplicationContext(context1);
        SpringExtensionFactory.addApplicationContext(context2);
    }

    @Test
    public void testGetExtensionBySPI() { //已测
        Protocol protocol = springExtensionFactory.getExtension(Protocol.class, "protocol");
        Assertions.assertNull(protocol); //因为SpringExtensionFactory#getExtension方法中，会对SPI接口做判断，SPI接口返回null，不处理
    }

    @Test
    public void testGetExtensionByName() { //已测，使用spring容器获取bean
        DemoService bean = springExtensionFactory.getExtension(DemoService.class, "bean1");
        Assertions.assertNotNull(bean);
        HelloService hello = springExtensionFactory.getExtension(HelloService.class, "hello");
        Assertions.assertNotNull(hello);
    }

    @AfterEach
    public void destroy() {
        SpringExtensionFactory.clearContexts();
        context1.close();
        /**
         * 应用上下文关闭：会调用AbstractApplicationContext的close()方法
         * 会做一些处理：
         * 1）发出容器关闭事件 ContextClosedEvent
         * 2）停止所有生命周期的相关bean（Lifecycle beans）
         * 3）销毁所有的bean
         * 4）关闭bean工厂，即把serializationId置为null
         * 5）子类可以实现重写AbstractApplicationContext中的onClose()方法，实现业务逻辑处理
         */
        context2.close();
    }

    /**
     * Indicates that a method produces a bean to be managed by the Spring container.
     * （@Bean：由方法产生Spring管理的bean）
     */
    @Bean("bean1")
    public DemoService bean1() {
        return new DemoServiceImpl();
    }

    @Bean("bean2")
    public DemoService bean2() {
        return new DemoServiceImpl();
    }

    @Bean("hello")
    public HelloService helloService() {
        return new HelloServiceImpl();
    }
}
