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

import org.apache.dubbo.demo.GreetingService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ConsumerApplication {
    /**
     * In order to make sure multicast registry works, need to specify '-Djava.net.preferIPv4Stack=true' before
     * launch the application
     */
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/dubbo-consumer.xml");
        context.start();
//        DemoService demoService = context.getBean("demoService", DemoService.class);
////        CompletableFuture<String> hello = demoService.sayHelloAsync("world");
//        String response = demoService.sayHello2("how are you?");
//        System.out.println("demoService 结果: " + response);

        GreetingService greetingService = context.getBean("greetingService", GreetingService.class);
        System.out.println(greetingService.hello("GreetingService: 你好！")); //todo @pause 09-19
        

        // 泛化调用
//        GenericService genericService = (GenericService) context.getBean("demoService");
//        String[] parameterTypes = new String[1];
//        parameterTypes[0] = "java.lang.String";
//        Object[] argValues = new Object[1];
//        argValues[0] = "fff";
//        Object obj = genericService.$invoke("sayHello", parameterTypes, argValues);
//        System.out.println(JSON.toJSONString(obj));

//        Class cls = demoService.getClass();
//        System.out.println("是否有注解：" + cls.isAnnotationPresent(BasicInfo.class));
//        if (cls.isAnnotationPresent(BasicInfo.class)) {
//
//        }
//        BasicInfo basicInfo = (BasicInfo) cls.getAnnotation(BasicInfo.class);
//        System.out.println(basicInfo.age() + ";;;" + basicInfo.username());


//        GenericService genericService = (GenericService) context.getBean("demoService");
//        Object result = genericService.$invoke("sayHello2", new String[] {"java.lang.String"}, new Object[] {"Worldsss"});
//        System.out.println("generic result:" + result);


//        for (int i = 0; i < 3; i++) {
//            GreetingService greetingService = context.getBean("greetingService", GreetingService.class);
//            System.out.println("greetingService result: " + greetingService.hello());
//        }


        System.in.read();

    }
}
