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

import org.apache.dubbo.common.extension.SPI;

/**
 * Extension for intercepting（拦截） the invocation for both service provider and consumer, （拦截服务提供者和调用者的调用信息）
 * furthermore（此外）, most of functions in dubbo are implemented base on the same mechanism（机制）. Since every time when remote method is
 * invoked, the filter extensions will be executed too, the corresponding penalty should be considered before
 * more filters are added.（在添加更多过滤器前，更多考虑代价）
 * <pre>
 *  They way filter work from sequence point of view is
 *    <b>
 *    ...code before filter ...
 *          invoker.invoke(invocation) //filter work in a filter implementation class
 *          ...code after filter ...
 *    </b>
 *    Caching is implemented in dubbo using filter approach. If cache is configured for invocation then before
 *    remote call configured caching type's (e.g. Thread Local, JCache etc) implementation invoke method gets called.
 * </pre>
 * Filter. (SPI, Singleton, ThreadSafe)
 *
 * @see org.apache.dubbo.rpc.filter.GenericFilter
 * @see org.apache.dubbo.rpc.filter.EchoFilter
 * @see org.apache.dubbo.rpc.filter.TokenFilter
 * @see org.apache.dubbo.rpc.filter.TpsLimitFilter
 */
@SPI
public interface Filter {
    /**
     * Make sure call invoker.invoke() in your implementation.
     */
    Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException;

    /**
     * 什么地方会引用？解：实现类可以选择是否实现，如ActiveLimitFilter实现了Filter.Listener但AccessLogFilter没实现
     * 相比2.5.6，多了信息响应以及异常处理
     */
    interface Listener {
        void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation);

        void onError(Throwable t, Invoker<?> invoker, Invocation invocation);
    }
}