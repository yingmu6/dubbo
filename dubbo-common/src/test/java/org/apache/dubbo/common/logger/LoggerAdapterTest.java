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
package org.apache.dubbo.common.logger;

import org.apache.dubbo.common.logger.jcl.JclLogger;
import org.apache.dubbo.common.logger.jcl.JclLoggerAdapter;
import org.apache.dubbo.common.logger.jdk.JdkLogger;
import org.apache.dubbo.common.logger.jdk.JdkLoggerAdapter;
import org.apache.dubbo.common.logger.log4j.Log4jLogger;
import org.apache.dubbo.common.logger.log4j.Log4jLoggerAdapter;
import org.apache.dubbo.common.logger.log4j2.Log4j2Logger;
import org.apache.dubbo.common.logger.log4j2.Log4j2LoggerAdapter;
import org.apache.dubbo.common.logger.slf4j.Slf4jLogger;
import org.apache.dubbo.common.logger.slf4j.Slf4jLoggerAdapter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class LoggerAdapterTest { //@DtY-Doing

    /**
     * 知识点：日志适配器
     *
     * 知识点概括：
     * 1）LoggerAdapter是SPI接口，在具体的实现类中，如Log4jLoggerAdapter，适配
     *   了具体的日志实例Logger。
     *
     * 2）Logger对各种日志功能进行了抽象，如jdk、slf4j等，然后在具体实例中JdkLogger、Slf4jLogger
     *    与真正使用的日志进行API接口调用的适配
     *
     * 关联点学习：
     * 1）junit的@ParameterizedTest传入参数_学习及实践（Doing）
     * 2）jcl、jdk、log4j、slf4j、log4j2等日志组件的使用及对比（Doing）
     *
     * 问题点答疑：
     * 1）SPI接口LoggerAdapter是在代码哪里进行使用的？
     * 2）JclLoggerAdapter、JdkLoggerAdapter等成员属性file的用途是什么？
     */

    static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of(JclLoggerAdapter.class, JclLogger.class),
                Arguments.of(JdkLoggerAdapter.class, JdkLogger.class),
                Arguments.of(Log4jLoggerAdapter.class, Log4jLogger.class),
                Arguments.of(Slf4jLoggerAdapter.class, Slf4jLogger.class),
                Arguments.of(Log4j2LoggerAdapter.class, Log4j2Logger.class)
        );
    }

    @ParameterizedTest
    @MethodSource("data") //Done
    public void testGetLogger(Class<? extends LoggerAdapter> loggerAdapterClass, Class<? extends Logger> loggerClass) throws IllegalAccessException, InstantiationException {
        LoggerAdapter loggerAdapter = loggerAdapterClass.newInstance();
        Logger logger = loggerAdapter.getLogger(this.getClass());
        assertThat(logger.getClass().isAssignableFrom(loggerClass), is(true));

        logger = loggerAdapter.getLogger(this.getClass().getSimpleName());
        assertThat(logger.getClass().isAssignableFrom(loggerClass), is(true));

        /**
         * 结果分析：
         * 1）此处采用junit的参数化的传递，会把data方法中构建的参数依次传入当前测试方法
         *
         * 2）会通过日志适配器LoggerAdapter的getLogger(...)获取对应的日志实例Logger
         */
    }

    @ParameterizedTest
    @MethodSource("data") //Done
    public void testLevel(Class<? extends LoggerAdapter> loggerAdapterClass) throws IllegalAccessException, InstantiationException {
        LoggerAdapter loggerAdapter = loggerAdapterClass.newInstance();
        for (Level targetLevel : Level.values()) {
            loggerAdapter.setLevel(targetLevel);
            assertThat(loggerAdapter.getLevel(), is(targetLevel));
        }

        /**
         * 结果分析：
         * 1）日志适配器LoggerAdapter可以设置和获取日志级别Level
         *
         */
    }

    /**
     * 新增场景：测试LoggerAdapter创建逻辑
     */
    @ParameterizedTest
    @MethodSource("data") //Done
    public void testLoggerAdapterCreate(Class<? extends LoggerAdapter> loggerAdapterClass) throws InstantiationException, IllegalAccessException {
        LoggerAdapter loggerAdapter = loggerAdapterClass.newInstance();
        assertThat(loggerAdapter, not(nullValue()));

        /**
         * 结果分析：
         * 1）newInstance()创建日志适配器实例是，会进行相关的初始化。对于jdk、log4j等会将日志写到文件中的
         *   日志组件，还会查找并设置相关的日志文件
         */
    }
}