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
package org.apache.dubbo.registry.client.event;

import org.apache.dubbo.event.Event;
import org.apache.dubbo.registry.client.ServiceInstance;

/**
 * The {@link Event Dubbo event} for {@link ServiceInstance an service instance}
 *
 * @since 2.7.5
 */
public abstract class ServiceInstanceEvent extends Event { //服务实例事件

    /**
     * ServiceInstanceEvent的实现类有：
     * 1）ServiceInstancePreRegisteredEvent：服务实例在注册中心注册前的事件
     * 2）ServiceInstancePreUnregisteredEvent：服务实例在注册中心取消注册前的事件
     * 3）ServiceInstanceRegisteredEvent：服务实例在注册中心注册后的事件
     * 4）ServiceInstanceUnregisteredEvent：服务实例在注册中心取消注册后的事件
     */
    private final ServiceInstance serviceInstance; //维护着服务实例对象

    /**
     * @param serviceInstance {@link ServiceInstance an service instance}
     */
    public ServiceInstanceEvent(Object source, ServiceInstance serviceInstance) {
        super(source);
        this.serviceInstance = serviceInstance;
    }

    /**
     * Get current {@link ServiceInstance service instance}
     *
     * @return current {@link ServiceInstance service instance}
     */
    public ServiceInstance getServiceInstance() {
        return serviceInstance;
    }
}
