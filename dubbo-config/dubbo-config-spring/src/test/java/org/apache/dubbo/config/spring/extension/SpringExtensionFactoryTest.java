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
    private AnnotationConfigApplicationContext context1;
    private AnnotationConfigApplicationContext context2;

    @BeforeEach
    public void init() {
        SpringExtensionFactory.clearContexts();
        context1 = new AnnotationConfigApplicationContext();
        context1.register(getClass());
        context1.refresh();
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
    public void testGetExtensionByName() { //todo @csy pause
        DemoService bean = springExtensionFactory.getExtension(DemoService.class, "bean1");
        Assertions.assertNotNull(bean);
        HelloService hello = springExtensionFactory.getExtension(HelloService.class, "hello");
        Assertions.assertNotNull(hello);
    }

    @AfterEach
    public void destroy() {
        SpringExtensionFactory.clearContexts();
        context1.close();
        context2.close();
    }

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
