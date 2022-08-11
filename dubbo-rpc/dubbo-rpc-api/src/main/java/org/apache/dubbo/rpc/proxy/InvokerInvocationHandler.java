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

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Constants;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ConsumerModel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * InvokerHandler
 * 1）代理模式：通过代理间接的调用被代理对象的方法
 * 2）Java的反射包提供了一个Porxy类和InvokationHandler接口。它们结合在一起后可以创建动态代理类。Porxy类基于传递的参数创建动态代理类。
 * InvocationHandler则用于激发动态代理类的方法。这个过程是在程序执行过程中动态生成与处理的，所以叫动态代理
 * 3）动态代理就是Proxy的class文件在程序运行前是不存在，其字节码是在运行的时候自动生成的。
 * https://www.jianshu.com/p/4df6e4d7eb46
 */
public class InvokerInvocationHandler implements InvocationHandler { //Proxy与InvocationHandler结合使用，创建动态代理类
    // InvocationHandler：每一个代理实例都与一个调用处理类关联，当代理实例上的方法被调用时，会调用InvocationHandler的invoke方法（方法回调）
    private static final Logger logger = LoggerFactory.getLogger(InvokerInvocationHandler.class);
    private final Invoker<?> invoker;
    private ConsumerModel consumerModel; //@csy 此处为啥只有消费者模型，不用维护提供者模型吗？解：此处是由消费端发起的请求调用，所以不用维护提供者模型

    public InvokerInvocationHandler(Invoker<?> handler) { //构造invoker对应的处理类
        this.invoker = handler;
        String serviceKey = invoker.getUrl().getServiceKey();
        if (serviceKey != null) {
            this.consumerModel = ApplicationModel.getConsumerModel(serviceKey); //根据服务key获取消费者模型数据，并设置到当前成员变量中
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        /**
         *  @csy 该方法的功能用途是什么？
         *  执行远程方法的调用（执行具体接口的方法调用时，会进入该方法）
         */

        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) { //调用无参的指定方法
            if ("toString".equals(methodName)) {
                return invoker.toString();
            } else if ("$destroy".equals(methodName)) {
                invoker.destroy();
                return null;
            } else if ("hashCode".equals(methodName)) {
                return invoker.hashCode();
            }
        } else if (parameterTypes.length == 1 && "equals".equals(methodName)) { //调用equals方法
            return invoker.equals(args[0]);
        }

        // 调用包含多个参数的方法
        RpcInvocation rpcInvocation = new RpcInvocation(method, invoker.getInterface().getName(), args);
        String serviceKey = invoker.getUrl().getServiceKey();
        rpcInvocation.setTargetServiceUniqueName(serviceKey); //设置目标服务名

        if (consumerModel != null) { //将消费模型数据，设置到调用的RpcInvocation信息中
            rpcInvocation.put(Constants.CONSUMER_MODEL, consumerModel);
            rpcInvocation.put(Constants.METHOD_MODEL, consumerModel.getMethodModel(method));
        }

        return invoker.invoke(rpcInvocation).recreate(); //执行远程调用
    }
}
