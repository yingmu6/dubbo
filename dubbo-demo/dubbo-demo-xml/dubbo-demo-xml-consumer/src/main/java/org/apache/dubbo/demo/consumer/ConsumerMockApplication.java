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
package org.apache.dubbo.demo.consumer;

public class ConsumerMockApplication {
    /**
     * In order to make sure multicast registry works, need to specify '-Djava.net.preferIPv4Stack=true' before
     * launch the application
     */
//    public static void main(String[] args) throws Exception {
//        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/dubbo-consumer-mock.xml");
//        context.start();
//        DemoService demoService = context.getBean("demoService", DemoService.class);
//        String response = demoService.sayHello2("mock test!");
//        System.out.println("mock result: " + response);
//
//        System.out.println(demoService.sayHello("ddds"));
//        System.in.read();
//    }


    /**
     * 同一个包下，如果有两个包含main方法的入口类，会报出
     * Execution default of goal org.springframework.boot:spring-boot-maven-plugin:2.1.4.RELEASE:repackage failed: Unable to find a single main class from the following candidates [org.apache.dubbo.demo.consumer.ConsumerApplication,
     * org.apache.dubbo.demo.consumer.ConsumerMockApplication]
     *
     * 所以打包时，可以将一个main()方法去掉
     */
}
