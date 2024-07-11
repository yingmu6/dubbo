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

import org.apache.dubbo.common.logger.jcl.JclLoggerAdapter;
import org.apache.dubbo.common.logger.jdk.JdkLoggerAdapter;
import org.apache.dubbo.common.logger.log4j.Log4jLoggerAdapter;
import org.apache.dubbo.common.logger.log4j2.Log4j2LoggerAdapter;
import org.apache.dubbo.common.logger.slf4j.Slf4jLoggerAdapter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class LoggerTest { //@DtY-Doing

    /**
     * 知识点：Logger
     *
     * 知识点概括：
     * 1）
     *
     * 问题点答疑：
     * 1）LoggerAdapter中的日志级别是怎么使用上的？
     *   解答：LoggerAdapter的具体实例中，有做Dubbo日志级别与具体日志级别的转换适配，
     *        例如：JdkLoggerAdapter中的toJdkLevel(...)和fromJdkLevel(...)方法
     *        从已有的日志适配器来看，jdk和log4j与dubbo定义的日志级别有差异，所以需要
     *        进行适配转换，而jck、log4j2、slf4j与dubbo定义的日志级别相同，就不要转换了
     *        （其实Dubbo就是选择一些通用的组件，定义自己的数据模型，然后在适配其它的组件）
     *
     *
     */

    static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of(JclLoggerAdapter.class),
                Arguments.of(JdkLoggerAdapter.class),
                Arguments.of(Log4jLoggerAdapter.class),
                Arguments.of(Slf4jLoggerAdapter.class),
                Arguments.of(Log4j2LoggerAdapter.class)
        );
    }

    @ParameterizedTest
    @MethodSource("data") //Done
    public void testAllLogMethod(Class<? extends LoggerAdapter> loggerAdapter) throws Exception {
        LoggerAdapter adapter = loggerAdapter.newInstance();
        adapter.setLevel(Level.ALL);
        Logger logger = adapter.getLogger(this.getClass());
        logger.error("error");
        logger.warn("warn");
        logger.info("info");
        logger.debug("debug");
        logger.trace("info");

        logger.error(new Exception("error"));
        logger.warn(new Exception("warn"));
        logger.info(new Exception("info"));
        logger.debug(new Exception("debug"));
        logger.trace(new Exception("trace"));

        logger.error("error_msg", new Exception("error")); //输出异常信息，并带有异常轨迹
        logger.warn("warn_msg", new Exception("warn"));
        logger.info("info", new Exception("info"));
        logger.debug("debug", new Exception("debug"));
        logger.trace("trace", new Exception("trace"));

        /**
         * 结果分析：
         * 1）从控制台输出的结果来看，有些日志输出是要有日志配置文件的，比如Jdk日志
         *   "No such logging.properties in classpath for jdk logging config!"
         *
         * 2）设置日志级别后adapter.setLevel(Level.ALL)，相应级别的日志能够显示
         *    （在具体的日志适配器中，会做日志级别的转换）
         *
         */
    }

    @ParameterizedTest
    @MethodSource("data") //Done
    public void testLevelEnable(Class<? extends LoggerAdapter> loggerAdapter) throws IllegalAccessException, InstantiationException {
        LoggerAdapter adapter = loggerAdapter.newInstance();
        adapter.setLevel(Level.ALL);
        Logger logger = adapter.getLogger(this.getClass());
        assertThat(logger.isWarnEnabled(), not(nullValue()));
        assertThat(logger.isTraceEnabled(), not(nullValue()));
        assertThat(logger.isErrorEnabled(), not(nullValue()));
        assertThat(logger.isInfoEnabled(), not(nullValue()));
        assertThat(logger.isDebugEnabled(), not(nullValue()));

        /**
         * 结果分析：
         * 1）isWarnEnabled：判断是否启动warn级别的日志
         *    以log4j内部逻辑为例：
         *    a）会判断是否禁用warn日志
         *    b）若没禁用，再判断warn级别对应的值，是否 大于或等于 设置的日志级别对应的值
         *      （所以经常遇到的设置：比如设置info级别，那么大于或等于info级别的日志都会打印出来）
         *
         */
    }
}