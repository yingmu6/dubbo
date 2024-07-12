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

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class LoggerFactoryTest { //@DtY-Doing

    /**
     * 知识点：日志工厂
     *
     * 知识点概括：
     * 1）
     *
     * 问题点答疑：
     * 1）使用LoggerFactory#setLoggerAdapter(...)打印日志时，没有看到具体输出，要怎么配置？
     */

    @Test
    public void testLoggerLevel() { //Done
        LoggerFactory.setLevel(Level.INFO);
        Level level = LoggerFactory.getLevel();

        assertThat(level, is(Level.INFO));

        /**
         * 结果分析：
         * 1）Level是Dubbo定义的日志级别，会与具体日志组件级别进行适配转换，
         *    如Log4jLoggerAdapter中toLog4jLevel(...)和fromLog4jLevel(...)
         *    就是将Dubbo定义的日志级别与log4j日志级别进行适配转换
         */
    }

    @Test
    public void testGetLogFile() { //Done
        LoggerFactory.setLoggerAdapter("slf4j");
        File file = LoggerFactory.getFile();

        assertThat(file, is(nullValue()));

        /**
         * 结果分析：
         * 1）setLoggerAdapter("slf4j")中根据扩展名找到日志适配器，因为LoggerAdapter是SPI接口
         *    所以可以根据SPI机制来找到对应的实例
         *
         * 2）本例中的日志适配器是Slf4jLoggerAdapter，其初始化时没有处理成员属性file，所以值为null
         */
    }

    @Test
    public void testAllLogLevel() { //Done
        for (Level targetLevel : Level.values()) {
            LoggerFactory.setLevel(targetLevel);
            Level level = LoggerFactory.getLevel();

            assertThat(level, is(targetLevel));
        }

        /**
         * 结果分析：
         * 1）遍历dubbo所设置的日志级别，依次与具体日志组件转换
         *
         * 2）日志组件的日志级别是支持多次更新的，保留上一次设置的日志级别
         */
    }

    @Test
    public void testGetLogger() { //Done
        Logger logger1 = LoggerFactory.getLogger(this.getClass());
        Logger logger2 = LoggerFactory.getLogger(this.getClass());

        assertThat(logger1, is(logger2));

        // 增加场景
        Logger logger3 = LoggerFactory.getLogger(LoggerTest.class);
        assertThat(logger1, not(logger3)); //此处class不一样，所以对应的Logger实例就不一样

        /**
         * 结果分析：
         * 1）通过getLogger(Class<?> key) 获取日志处理器Logger时，会从LoggerFactory的缓存
         *    LOGGERS中查找，只要class相同，得到的Logger就相同
         */
    }

    @Test
    public void shouldReturnSameLogger() { //Done
        Logger logger1 = LoggerFactory.getLogger(this.getClass().getName());
        Logger logger2 = LoggerFactory.getLogger(this.getClass().getName());

        assertThat(logger1, is(logger2));

        /**
         * 结果分析
         * 1）getLogger(String key)方法做了重载，支持传入字符串形式，所以只要字符串key
         *   相等，从缓存中LOGGERS得到的Logger也相等
         */
    }
}
