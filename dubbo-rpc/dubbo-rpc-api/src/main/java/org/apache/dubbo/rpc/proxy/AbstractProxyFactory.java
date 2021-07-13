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
package org.apache.dubbo.rpc.proxy;

import com.alibaba.dubbo.rpc.service.EchoService;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.rpc.Constants;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.Destroyable;
import org.apache.dubbo.rpc.service.GenericService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.rpc.Constants.INTERFACES;

/**
 * AbstractProxyFactory抽象代理工厂类只实现了接口getProxy方法并对参数校验，getInvoker方法交由子类完成
 * AbstractProxyFactory实现了getProxy方法，并对该方法实现了新的抽象getProxy(Invoker<T> invoker, Class<?>[] types)
 */
public abstract class AbstractProxyFactory implements ProxyFactory {
    private static final Class<?>[] INTERNAL_INTERFACES = new Class<?>[]{ //所有的代理类都实现了EchoService接口
            EchoService.class, Destroyable.class
    };

    @Override
    public <T> T getProxy(Invoker<T> invoker) throws RpcException { //代理相关了解
        return getProxy(invoker, false);
    }

    /**
     * 获取invoker对应的代理类
     * 1）创建代理前，先找出需要代理接口的Class集合
     * 2）调用代理实现类的方法获取代理（不同的代理方式：JavassistProxyFactory或JdkProxyFactory）
     */
    @Override
    public <T> T getProxy(Invoker<T> invoker, boolean generic) throws RpcException {
        /**
         * 查找需要代理的接口集合
         */
        Set<Class<?>> interfaces = new HashSet<>();

        //todo @csy-015 url中的INTERFACES内容会是什么？从哪里写入的？
        String config = invoker.getUrl().getParameter(INTERFACES); //启动时，Invoker对应的实例为MockClusterInvoker,MockClusterInvoker中的invoker为AbstractCluster$InterceptorInvokerNode（内部类）
        if (config != null && config.length() > 0) { //从url中获取配置的接口类型，设置到Class集合中
            String[] types = COMMA_SPLIT_PATTERN.split(config);
            for (String type : types) {
                // TODO can we load successfully for a different classloader?.
                interfaces.add(ReflectUtils.forName(type)); //生成指定类型的Class，并加到集合中
            }
        }

        if (generic) { //泛化类型，处理泛化类型  todo @csy-001 构建用例测试该入口
            if (!GenericService.class.isAssignableFrom(invoker.getInterface())) { //兼容alibaba的GenericService泛化类型
                interfaces.add(com.alibaba.dubbo.rpc.service.GenericService.class); //若不是apache的泛化类，就加载alibaba的泛化类
            }

            try {
                // find the real interface from url
                String realInterface = invoker.getUrl().getParameter(Constants.INTERFACE);
                interfaces.add(ReflectUtils.forName(realInterface));
            } catch (Throwable e) {
                // ignore
            }
        }

        interfaces.add(invoker.getInterface()); //实际接口对应的Class
        interfaces.addAll(Arrays.asList(INTERNAL_INTERFACES)); //预定接口对应的Class

        /**
         * 对查找到的接口集合进行代理
         */
        return getProxy(invoker, interfaces.toArray(new Class<?>[0])); //调用抽象方法，具体的实现交由子类执行
    }

    public abstract <T> T getProxy(Invoker<T> invoker, Class<?>[] types); //todo @csy-001 是怎么选择抽象类的实例的？SPI机制吗？

}
