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

package com.alibaba.dubbo.rpc.service;

@Deprecated
public interface EchoService extends org.apache.dubbo.rpc.service.EchoService {
    //为啥EchoService没有具体的apache实现？解：所有服务自动实现 EchoService 接口，只需将任意服务引用强制转型为 EchoService，即可使用。
    //是在哪里体现代理类实现EchoService接口的？解：在AbstractProxyFactory中的成员变量Class<?>[] INTERNAL_INTERFACES中
}
