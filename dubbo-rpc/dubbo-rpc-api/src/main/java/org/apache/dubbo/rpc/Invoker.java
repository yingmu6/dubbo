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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.Node;

/**
 * Invoker. (API/SPI, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.rpc.Protocol#refer(Class, org.apache.dubbo.common.URL)
 * @see org.apache.dubbo.rpc.InvokerListener
 * @see org.apache.dubbo.rpc.protocol.AbstractInvoker
 */
public interface Invoker<T> extends Node { //todo @csy Invoker的相关继承图，整理下
    /**
     * Invoker是提供者、消费者都会用到的吗？最终的执行都是Invoker执行的吗？
     * 解：Invoker是实体域，它是Dubbo的核心模型，其它模型都向它靠扰，或转换成它，它代表一个可执行体，可向它发起invoke调用，
     * 它有可能是一个本地的实现，也可能是一个远程的实现，也可能一个集群实现。（官网描述）【对于消费者，invoker里的信息，就是提供者的信息，相反也是类似】
     */

    /**
     * get service interface.（获取服务接口）
     *
     * @return service interface.
     */
    Class<T> getInterface();

    /**
     * invoke.（执行调用）
     *
     * @param invocation  调用信息
     * @return result 调用结果
     * @throws RpcException
     */
    Result invoke(Invocation invocation) throws RpcException;

}