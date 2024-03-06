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
package org.apache.dubbo.config.bootstrap;

import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.bootstrap.rest.UserService;

/**
 * Dubbo Provider Bootstrap
 *
 * @since 2.7.5
 */
public class DubboServiceConsumerBootstrap { //使用DubboBootstrap引用服务

    public static void main(String[] args) throws Exception {

        DubboBootstrap bootstrap = DubboBootstrap.getInstance()
                .application("dubbo-consumer-demo")
                .protocol(builder -> builder.port(20887).name("dubbo"))
                // Eureka
//                .registry(builder -> builder.address("eureka://127.0.0.1:8761?registry-type=service&subscribed-services=dubbo-provider-demo"))

                // Zookeeper
                .registry("zookeeper", builder -> builder.address("zookeeper://127.0.0.1:2181?registry-type=service&subscribed-services=dubbo-provider-demo"))
                .metadataReport(new MetadataReportConfig("zookeeper://127.0.0.1:2181"))

                // Nacos
                // .registry("nacos", builder -> builder.address("nacos://127.0.0.1:8848?registry.type=service&subscribed.services=dubbo-provider-demo"))

                // Consul
                // .registry("consul", builder -> builder.address("consul://127.0.0.1:8500?registry.type=service&subscribed.services=dubbo-provider-demo").group("namespace1"))
                .reference("echo", builder -> builder.interfaceClass(EchoService.class).protocol("dubbo")) //builder类型为：ReferenceBuilder<S>
                .reference("user", builder -> builder.interfaceClass(UserService.class).protocol("rest")) //指定引用接口以及使用的协议
                .start();

        EchoService echoService = bootstrap.getCache().get(EchoService.class); //从缓存中获取引用接口的代理对象

        for (int i = 0; i < 500; i++) {
            Thread.sleep(2000L);
            System.out.println(echoService.echo("Hello,World")); //通过代理对象进行方法调用
        }

    }
}
