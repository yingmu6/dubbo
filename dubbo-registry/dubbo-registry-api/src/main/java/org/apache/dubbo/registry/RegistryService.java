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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;

import java.util.List;

/**
 * RegistryService. (SPI, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.Registry
 * @see org.apache.dubbo.registry.RegistryFactory#getRegistry(URL)
 */
public interface RegistryService { //注册服务，包含注册数据、订阅数据、查询数据的功能

    /**
     * Register data（注册的数据包含）, such as : provider service, consumer address, route rule（路由规则）, override rule（覆盖规则） and other data.
     * <p>
     * Registering is required（必须） to support the contract（契约）:<br>
     * 1. When the URL sets the check=false parameter. When the registration fails（注册失败）, the exception is not thrown（异常不抛出） and retried in the background（后台重试）. Otherwise, the exception will be thrown.<br>
     * 2. When URL sets the dynamic=false parameter（数据是否是动态处理，即数据为持久、临时数据）, it needs to be stored persistently（持久化存储）, otherwise, it should be deleted automatically when the registrant has an abnormal exit（异常退出）.<br>
     * 3. When the URL sets category=routers, it means classified storage（分类存储）, the default category is providers, and the data can be notified by the classified section. <br> //数据分类，可最为判断变更推送的条件
     * 4. When the registry is restarted, network jitter（网络抖动）, data can not be lost（数据不能丢失）, including automatically deleting data from the broken line.<br>
     * 5. Allow URLs which have the same URL but different parameters to coexist（允许共存）,they can't cover each other（不互相覆盖）.<br>
     *
     * @param url Registration information , is not allowed to be empty, e.g: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void register(URL url); //@csy 此处是否会发起远程注册中心的注册连接的？ 还是本地缓存处理？解：有发起远程调用，如Zookeeper实现方式，会在远程创建节点。 本地也有缓存，在AbstractRegistry中

    /**
     * Unregister
     * <p>
     * Unregistering is required to support the contract:<br>
     * 1. If it is the persistent stored data of dynamic=false, the registration data can not be found（没找到注册数据）, then the IllegalStateException is thrown, otherwise it is ignored.<br>
     * （取消注册时若dynamic=false，持久节点的数据还保留着的，找不到则抛出异常）
     * 2. Unregister according to the full url match（完整url匹配）.<br>
     *
     * @param url Registration information , is not allowed to be empty, e.g: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void unregister(URL url);

    /**
     * Subscribe to eligible（符合条件的） registered data and automatically push（自动推送） when the registered data is changed（被注册的数据变更）.
     * <p>
     * Subscribing need to support contracts:<br>
     * 1. When the URL sets the check=false parameter. When the registration fails, the exception is not thrown and retried in the background. <br>
     * 2. When URL sets category=routers, it only notifies the specified classification data（仅通知指定的分类数据）. Multiple classifications are separated by commas, and allows asterisk（星号） to match, which indicates that all categorical data are subscribed.<br>
     * （可以按category来订阅数据，若category=*，表明匹配所有分类）
     * 3. Allow interface, group, version, and classifier as a conditional query（条件查询）, e.g.: interface=org.apache.dubbo.foo.BarService&version=1.0.0<br>
     * 4. And the query conditions allow the asterisk to be matched, subscribe to all versions of all the packets of all interfaces, e.g. :interface=*&group=*&version=*&classifier=*<br>
     * 5. When the registry is restarted and network jitter（网络抖动）, it is necessary to automatically restore（自动恢复） the subscription request（订阅请求）.<br>
     * 6. Allow URLs which have the same URL but different parameters to coexist,they can't cover each other.<br>
     * 7. The subscription process must be blocked（被阻塞）, when the first notice is finished and then returned.<br>
     *
     * @param url      Subscription condition（订阅条件）, not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener A listener of the change event（事件变更监听器）, not allowed to be empty
     */
    void subscribe(URL url, NotifyListener listener); //因为订阅时，指定了监听器，所以url有变更时，会收到变更通知

    /**
     * Unsubscribe
     * <p>
     * Unsubscribing is required to support the contract:<br>
     * 1. If don't subscribe, ignore it directly（未订阅，直接忽略）.<br>
     * 2. Unsubscribe by full URL match.<br> （按完整url进行匹配）
     *
     * @param url      Subscription condition, not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener A listener of the change event, not allowed to be empty
     */
    void unsubscribe(URL url, NotifyListener listener);

    /**
     * Query the registered data that matches the conditions（查询符合条件的注册数据）. Corresponding（相应地） to the push mode of the subscription, this is the pull mode and returns only one result.
     *
     * @param url Query condition, is not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @return The registered information list, which may be empty, the meaning is the same as the parameters of {@link org.apache.dubbo.registry.NotifyListener#notify(List<URL>)}.
     * @see org.apache.dubbo.registry.NotifyListener#notify(List)
     */
    List<URL> lookup(URL url);

}