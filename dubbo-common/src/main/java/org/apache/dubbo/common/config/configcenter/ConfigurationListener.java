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
package org.apache.dubbo.common.config.configcenter;

import java.util.EventListener;

/**
 * Config listener, will get notified when the config it listens on changes.（当配置发生变更时，能收到通知）
 */
public interface ConfigurationListener extends EventListener { //配置监听器，EventListener：事件标记接口

    /**
     * Listener call back method（回调方法）. Listener gets notified（监听器收到通知） by this method once there's any change happens（任意变化发生） on the config
     * the listener listens on（监听器监听的配置）.
     *
     * @param event config change event
     */
    void process(ConfigChangedEvent event);
}
