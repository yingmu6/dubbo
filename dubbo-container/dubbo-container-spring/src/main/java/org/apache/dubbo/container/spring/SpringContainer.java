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
package org.apache.dubbo.container.spring;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.container.Container;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * SpringContainer. (SPI, Singleton, ThreadSafe)
 *
 * The container class implementation for Spring
 */
public class SpringContainer implements Container {

    public static final String SPRING_CONFIG = "dubbo.spring.config";
    public static final String DEFAULT_SPRING_CONFIG = "classpath*:META-INF/spring/*.xml";
    private static final Logger logger = LoggerFactory.getLogger(SpringContainer.class);
    static ClassPathXmlApplicationContext context;

    public static ClassPathXmlApplicationContext getContext() {
        return context;
    }

    /**
     * 思路&流程
     * 1）从系统中获取配置文件的路径，若没有则取默认配置的文件路径
     * 2）使用spring容器加载指定路径xml内容，并启动容器
     */
    @Override
    public void start() {
        String configPath = ConfigUtils.getProperty(SPRING_CONFIG);
        if (StringUtils.isEmpty(configPath)) {
            configPath = DEFAULT_SPRING_CONFIG;
        }
        context = new ClassPathXmlApplicationContext(configPath.split("[,\\s]+"), false);
        context.refresh();
        context.start();
    }

    /**
     * 思路&流程
     * 停止spring容器，并将容器置为空
     */
    @Override
    public void stop() {
        try {
            if (context != null) {
                context.stop();
                context.close();
                context = null;
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

}
