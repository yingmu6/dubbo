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
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Router chain
 */
public class RouterChain<T> { //路由链：由多个路由器组成
    /**
     * 路由规则是什么？路由链是怎么使用的？
     * 解答：1）通过Dubbo中的路由规则可以做服务治理，路由规则在发起一次RPC调用前起到过滤目标服务器地址的作用，过滤后的地址列表，将作为消费端最终发起RPC调用的备选地址。
     * 2）可分为条件路由和标签路由。条件路由：支持以服务或Consumer应用为粒度配置路由规则。标签路由。以Provider应用为粒度配置路由规则
     * <p>
     * https://dubbo.apache.org/zh/docs/v2.7/user/examples/routing-rule/#m-zhdocsv27userexamplesrouting-rule 官方文档
     * https://blog.csdn.net/anLA_/article/details/101233619 博客文档
     * 3）在RegistryProtocol#doRefer中会使用路由链
     */

    // full list of addresses from registry, classified by method name.
    private List<Invoker<T>> invokers = Collections.emptyList(); //维护着从注册中心获取的invoker列表（Invoker的类型如：RegistryDirectory$InvokerDelegate）

    // containing all routers, reconstruct every time 'route://' urls change.
    private volatile List<Router> routers = Collections.emptyList(); //维护着所有的路由规则列表

    // Fixed router instances: ConfigConditionRouter, TagRouter, e.g., the rule for each instance may change but the
    // instance will never delete or recreate.
    private List<Router> builtinRouters = Collections.emptyList(); //内置的路由实例，包含MockInvokersSelector、TagRouter、AppRouter、ServiceRouter等

    public static <T> RouterChain<T> buildChain(URL url) { //构建路由链RouterChain
        return new RouterChain<>(url);
    }

    private RouterChain(URL url) { //构建路由链
        List<RouterFactory> extensionFactories = ExtensionLoader.getExtensionLoader(RouterFactory.class)
                .getActivateExtension(url, "router"); //获取满足条件的路由工厂列表

        List<Router> routers = extensionFactories.stream()
                .map(factory -> factory.getRouter(url))
                .collect(Collectors.toList()); //依次通过路由工厂创建路由实例

        initWithRouters(routers);
    }

    /**
     * the resident routers must being initialized before address notification.
     * FIXME: this method should not be public
     */
    public void initWithRouters(List<Router> builtinRouters) {
        this.builtinRouters = builtinRouters;
        this.routers = new ArrayList<>(builtinRouters);
        this.sort();
    }

    /**
     * If we use route:// protocol in version before 2.7.0, each URL will generate a Router instance（每个路由的URL会产生一个路由实例）, so we should
     * keep the routers up to date（保持最新）, that is, each time router URLs changes, we should update the routers list, only
     * keep the builtinRouters which are available all the time and the latest notified routers which are generated
     * from URLs.
     *
     * @param routers routers from 'router://' rules in 2.6.x or before.
     */
    public void addRouters(List<Router> routers) { //添加路由器列表：包含内置的和外部的路由列表
        List<Router> newRouters = new ArrayList<>();
        newRouters.addAll(builtinRouters);
        newRouters.addAll(routers);
        CollectionUtils.sort(newRouters);
        this.routers = newRouters;
    }

    private void sort() {
        Collections.sort(routers);
    }

    /**
     * @param url
     * @param invocation
     * @return
     */
    public List<Invoker<T>> route(URL url, Invocation invocation) { //将invoker列表，依次按路由规则进行筛选过滤
        List<Invoker<T>> finalInvokers = invokers; //invokers值会RegistryDirectory.refreshInvoker中进行设置
        for (Router router : routers) { //Router的实例是在哪里选择的？解：在RegistryDirectory#notify中会调用addRouters()方法添加路由列表
            finalInvokers = router.route(finalInvokers, url, invocation); //将invoker列表依次经过路由链做过滤筛选（依次将上次的处理结果，作为下次路由的输入）
        }
        return finalInvokers;
    }

    /**
     * Notify router chain of the initial addresses from registry at the first time.
     * Notify whenever（每当） addresses in registry change.
     */
    public void setInvokers(List<Invoker<T>> invokers) {
        this.invokers = (invokers == null ? Collections.emptyList() : invokers);
        routers.forEach(router -> router.notify(this.invokers)); //依次通过路由器做通知
    }
}
