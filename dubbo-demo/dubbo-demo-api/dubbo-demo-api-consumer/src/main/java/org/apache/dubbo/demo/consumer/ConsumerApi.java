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

import com.google.common.collect.Lists;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.demo.ICollectionParamService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsumerApi {
    public static void main(String[] args) throws Exception {
        if (isClassic(args)) {
            runWithRefer();
        } else {
            runWithBootstrap();
        }
        System.in.read();
    }

    private static boolean isClassic(String[] args) {
        return args.length > 0 && "classic".equalsIgnoreCase(args[0]);
    }

    private static void runWithBootstrap() {
//        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
//        reference.setInterface(DemoService.class);
//        reference.setGeneric("true");

        ReferenceConfig<ICollectionParamService> reference2 = new ReferenceConfig<>();
        reference2.setInterface(ICollectionParamService.class);

        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-provider"))
                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
//                .reference(reference)
                .reference(reference2)
                .start();

//        DemoService demoService = ReferenceConfigCache.getCache().get(reference);
//        String message = demoService.sayHello2("dubbo");
//        System.out.println(message);

        Map<Integer, String> map = new HashMap<>();
        map.put(1, "aa");
        map.put(2, "cc");
        ICollectionParamService collectionParamService = ReferenceConfigCache.getCache().get(reference2);
        String message2 = collectionParamService.getValWithMapParam(map);
        System.out.println("map调用：" + message2);

        List<String> params = Lists.newArrayList("aa", "tt");
        String message3 = collectionParamService.getValWitchListParam(params);
        System.out.println("list调用：" + message3);

//        // generic invoke
//        GenericService genericService = (GenericService) demoService;
//        Object genericInvokeResult = genericService.$invoke("sayHello", new String[] { String.class.getName() },
//                new Object[] { "dubbo generic invoke" });
//        System.out.println(genericInvokeResult);
    }

    private static void runWithRefer() {
        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
        reference.setApplication(new ApplicationConfig("dubbo-demo-api-consumer"));
        reference.setRegistry(new RegistryConfig("zookeeper://127.0.0.1:2181"));
        reference.setInterface(DemoService.class);
        DemoService service = reference.get();
        String message = service.sayHello("dubbo");
        System.out.println(message);
    }
}
