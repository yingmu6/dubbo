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

public interface Constants { //注册中心用到的常量
    // 以接口形式定义常量，因为接口中的常量默认就是 public static final的，所以与常量的概念比较吻合
    // 还有就是接口的变量只能是public的不能用其它的修饰符，若变量定义到类中，变量的访问属性就不受控制了

    String REGISTER_IP_KEY = "register.ip";

    String REGISTER_KEY = "register";

    String SUBSCRIBE_KEY = "subscribe";

    String DEFAULT_REGISTRY = "dubbo";

    String REGISTER = "register";

    String UNREGISTER = "unregister";

    String SUBSCRIBE = "subscribe";

    String UNSUBSCRIBE = "unsubscribe";

    String CONFIGURATORS_SUFFIX = ".configurators";

    String ADMIN_PROTOCOL = "admin";

    String PROVIDER_PROTOCOL = "provider";

    String CONSUMER_PROTOCOL = "consumer";

    String SCRIPT_PROTOCOL = "script";

    String CONDITION_PROTOCOL = "condition";
    String TRACE_PROTOCOL = "trace";
    /**
     * simple the registry for provider.
     *
     * @since 2.7.0
     */
    String SIMPLIFIED_KEY = "simplified";

    /**
     * To decide whether register center saves file synchronously（同步的）, the default value is asynchronously（异步的）
     */
    String REGISTRY_FILESAVE_SYNC_KEY = "save.file";

    /**
     * Whether to cache locally, default is true
     */
    String REGISTRY__LOCAL_FILE_CACHE_ENABLED = "file.cache";

    /**
     * Reconnection period in milliseconds for register center
     */
    String REGISTRY_RECONNECT_PERIOD_KEY = "reconnect.period";

    int DEFAULT_SESSION_TIMEOUT = 60 * 1000;

    /**
     * 默认重试次数：3次
     * Default value for the times of retry: 3
     */
    int DEFAULT_REGISTRY_RETRY_TIMES = 3;

    int DEFAULT_REGISTRY_RECONNECT_PERIOD = 3 * 1000;

    /**
     * 默认重试时间间隔：5秒
     * Default value for the period of retry interval in milliseconds: 5000
     */
    int DEFAULT_REGISTRY_RETRY_PERIOD = 5 * 1000;

    /**
     * Most retry times
     */
    String REGISTRY_RETRY_TIMES_KEY = "retry.times";

    /**
     * Period of registry center's retry interval
     */
    String REGISTRY_RETRY_PERIOD_KEY = "retry.period";

    String SESSION_TIMEOUT_KEY = "session";
}
