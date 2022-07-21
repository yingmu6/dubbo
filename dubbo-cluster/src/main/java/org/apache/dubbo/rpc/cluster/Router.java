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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Router. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Routing">Routing</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory)
 * @see org.apache.dubbo.rpc.cluster.Directory#list(Invocation)
 */
public interface Router extends Comparable<Router> {
    /**
     * 路由规则：官方文档
     * https://dubbo.apache.org/zh/docs/v2.7/user/examples/routing-rule-deprecated/
     * 1）=> 之前的为消费者匹配条件，=> 之后为提供者地址列表的过滤条件
     * 2）如果匹配条件为空，表示对所有消费方应用，如果过滤条件为空，表示禁止访问
     * 3）通过Dubbo中的路由规则做服务治理，路由规则在发起一次RPC调用前起到过滤目标服务器地址的作用，过滤后的地址列表，将作为消费端最终发起RPC调用的备选地址。
     */

    int DEFAULT_PRIORITY = Integer.MAX_VALUE;

    /**
     * Get the router url.
     *
     * @return url
     */
    URL getUrl();

    /**
     * Filter invokers with current routing rule and only return the invokers that comply（遵守） with the rule.
     * （用路由规则过滤提供者的invoker列表，返回与路由规则匹配的invoker列表）
     *
     * @param invokers   invoker list 提供者对应的invoker列表
     * @param url        refer url 消费端引用的url，如"consumer://"
     * @param invocation invocation
     * @return routed invokers
     * @throws RpcException
     */
    <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException;


    /**
     * Notify the router the invoker list（通知路由器关联的invoker列表）. Invoker list may change from time to time（可能会是不是更改）. This method gives the router a
     * chance to prepare before {@link Router#route(List, URL, Invocation)} gets called.
     *
     * @param invokers invoker list
     * @param <T>      invoker's type
     */
    default <T> void notify(List<Invoker<T>> invokers) {

    }

    /**
     * To decide whether this router need to execute every time an RPC comes or should only execute when addresses or
     * rule change.
     *
     * @return true if the router need to execute every time.
     */
    boolean isRuntime();

    /**
     * To decide whether this router should take effect when none of the invoker can match the router rule, which
     * means the {@link #route(List, URL, Invocation)} would be empty. Most of time, most router implementation would
     * default this value to false.
     *
     * @return true to execute if none of invokers matches the current router
     */
    boolean isForce();

    /**
     * Router's priority, used to sort routers.
     *
     * @return router's priority
     */
    int getPriority();

    @Override
    default int compareTo(Router o) {
        if (o == null) {
            throw new IllegalArgumentException();
        }
        return Integer.compare(this.getPriority(), o.getPriority()); //将优先级进行比较
    }
}
