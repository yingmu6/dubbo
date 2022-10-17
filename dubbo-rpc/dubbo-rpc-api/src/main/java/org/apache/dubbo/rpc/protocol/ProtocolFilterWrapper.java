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
package org.apache.dubbo.rpc.protocol;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.rpc.*;

import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.REFERENCE_FILTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SERVICE_FILTER_KEY;

/**
 * ListenerProtocol
 */
@Activate(order = 100)
public class ProtocolFilterWrapper implements Protocol { //org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper 在org.apache.dubbo.rpc.Protocol文件中配置了

    private final Protocol protocol; //对协议Protocol进行封装（持有ProtocolListenerWrapper实例）

    public ProtocolFilterWrapper(Protocol protocol) { //对Protocol进行封装
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }

    /**
     * 构造调用链讲解，参考：https://www.jianshu.com/p/09edc9549b8e
     * 因为new Invoker<T>{...} 是个匿名类，编译后为：ProtocolFilterWrapper$1，构造函数为：ProtocolFilterWrapper$1(final Invoker val$invoker, final Filter val$filter, final Invoker val$next)
     * 模拟逻辑为：
     * 1）若过滤器依次为： A => B => C => D => E
     * 2）构造过滤链的流程为：
     * <p>
     * 为了方便记忆，把每次循环编号，比如Loop(i) 表示第一次循环，last(i)表示第 i 次循环后的的last值。
     * Loop(1)   last 1 = new ProtocolFilterWrapper$1( invoker , E , invoker )，
     * Loop(2)   last 2 = new ProtocolFilterWrapper$1( invoker , D ,  last 1 )；
     * Loop(3)   last 3 = new ProtocolFilterWrapper$1( invoker , C, last 2 )；
     * Loop(4)   last 4 = new ProtocolFilterWrapper$1( invoker , B, last 3 )；
     * Loop(5)   last 5 = new ProtocolFilterWrapper$1( invoker , A , last 4 )；
     * <p>
     * 最后 return last 5，这样就把所有filter串起来了，最终的Invoker chain顺序是 last 5 -> last 4 -> last 3 -> last 2 -> last 1(即invoker本身)。
     */
    private static <T> Invoker<T> buildInvokerChain(final Invoker<T> invoker, String key, String group) { //构建调用链，并返回头结点
        Invoker<T> last = invoker;
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), key, group); //获取满足条件的过滤器Filter列表

        if (!filters.isEmpty()) {
            for (int i = filters.size() - 1; i >= 0; i--) { //从后往前遍历，最后一个就是头结点
                final Filter filter = filters.get(i);
                final Invoker<T> next = last;
                last = new Invoker<T>() { //将filter封装为invoker（使用匿名类创建）

                    @Override
                    public Class<T> getInterface() {
                        return invoker.getInterface();
                    }

                    @Override
                    public URL getUrl() {
                        return invoker.getUrl();
                    }

                    @Override
                    public boolean isAvailable() {
                        return invoker.isAvailable();
                    }

                    @Override
                    public Result invoke(Invocation invocation) throws RpcException {
                        Result asyncResult;
                        try {
                            asyncResult = filter.invoke(next, invocation); //使用过滤器Filter执行调用
                        } catch (Exception e) { // 对过滤器链中filter调用异常进行处理
                            /**
                             * 此处为什么会出现异常？都有哪些异常的？出现异常的处理逻辑是怎样的？
                             * 解答：从方法org.apache.dubbo.rpc.Filter#invoke声明上看，是会抛出RpcException异常的
                             * 具体的异常，看具体的实现类，如GenericFilter#invoke
                             */

                            if (filter instanceof ListenableFilter) { //若过滤器是ListenableFilter，则回调onError()方法响应错误
                                ListenableFilter listenableFilter = ((ListenableFilter) filter);
                                try {
                                    Filter.Listener listener = listenableFilter.listener(invocation);
                                    if (listener != null) {
                                        listener.onError(e, invoker, invocation); //使用监听器通知异常信息
                                    }
                                } finally {
                                    listenableFilter.removeListener(invocation); //Listener处理完成后，将其移除
                                }
                            } else if (filter instanceof Filter.Listener) {
                                Filter.Listener listener = (Filter.Listener) filter;
                                listener.onError(e, invoker, invocation);
                            }
                            throw e; //若有监听器，使用监听器回调通知，否则直接抛出异常
                        } finally {

                        }
                        return asyncResult.whenCompleteWithContext((r, t) -> { //此处的处理逻辑是怎样的？解答：添加回调方法，在RPC完成调用时，对响应的内容进行处理
                            if (filter instanceof ListenableFilter) { //响应结果
                                ListenableFilter listenableFilter = ((ListenableFilter) filter);
                                Filter.Listener listener = listenableFilter.listener(invocation);
                                try {
                                    if (listener != null) {
                                        if (t == null) {
                                            listener.onResponse(r, invoker, invocation); //进行接口回调
                                        } else {
                                            listener.onError(t, invoker, invocation);
                                        }
                                    }
                                } finally {
                                    listenableFilter.removeListener(invocation);
                                }
                            } else if (filter instanceof Filter.Listener) {
                                Filter.Listener listener = (Filter.Listener) filter;
                                if (t == null) {
                                    listener.onResponse(r, invoker, invocation);
                                } else {
                                    listener.onError(t, invoker, invocation);
                                }
                            }
                        });
                    }

                    @Override
                    public void destroy() {
                        invoker.destroy();
                    }

                    @Override
                    public String toString() {
                        return invoker.toString();
                    }
                };
            }
        }

        return last;
    }

    @Override
    public int getDefaultPort() {
        return protocol.getDefaultPort();
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException { //在协议暴露时，构建invoker对应的调用链路
        if (UrlUtils.isRegistry(invoker.getUrl())) { //若url对应的协议是注册协议，则不构建调用链
            return protocol.export(invoker);
        }
        return protocol.export(buildInvokerChain(invoker, SERVICE_FILTER_KEY, CommonConstants.PROVIDER));
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException { //在协议引用时，构建invoker对应的调用链路
        if (UrlUtils.isRegistry(url)) {
            return protocol.refer(type, url);
        }
        // 提供者暴露服务、消费者引用服务，都需要经过过滤链，使用的过滤器，会根据group、value进行匹配
        return buildInvokerChain(protocol.refer(type, url), REFERENCE_FILTER_KEY, CommonConstants.CONSUMER);
    }

    @Override
    public void destroy() {
        protocol.destroy();
    }

    @Override
    public List<ProtocolServer> getServers() {
        return protocol.getServers();
    }

}
