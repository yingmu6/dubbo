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

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.jcl.JclLoggerAdapter;
import org.apache.dubbo.common.logger.jdk.JdkLoggerAdapter;
import org.apache.dubbo.common.logger.log4j.Log4jLoggerAdapter;
import org.apache.dubbo.common.logger.log4j2.Log4j2LoggerAdapter;
import org.apache.dubbo.common.logger.slf4j.Slf4jLoggerAdapter;
import org.apache.dubbo.common.logger.support.FailsafeLogger;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Logger factory （日志工厂）
 */
public class LoggerFactory { //日志工厂

    private static final ConcurrentMap<String, FailsafeLogger> LOGGERS = new ConcurrentHashMap<>(); //使用日志的类名与日志处理器的映射
    private static volatile LoggerAdapter LOGGER_ADAPTER;

    // search common-used logging frameworks
    static { // 静态方法（类加载时，进行初始化）
        String logger = System.getProperty("dubbo.application.logger", ""); // 获取系统属性中日志相关的值
        switch (logger) {
            case "slf4j":
                setLoggerAdapter(new Slf4jLoggerAdapter());
                break;
            case "jcl":
                setLoggerAdapter(new JclLoggerAdapter());
                break;
            case "log4j":
                setLoggerAdapter(new Log4jLoggerAdapter());
                break;
            case "jdk":
                setLoggerAdapter(new JdkLoggerAdapter());
                break;
            case "log4j2":
                setLoggerAdapter(new Log4j2LoggerAdapter());
                break;
            default:
                List<Class<? extends LoggerAdapter>> candidates = Arrays.asList( //将日志适配器都放在候选列表中
                        Log4jLoggerAdapter.class, //把log4j放在候选列表的第一个，所以未设置日志适配器时，默认为log4j
                        Slf4jLoggerAdapter.class,
                        Log4j2LoggerAdapter.class,
                        JclLoggerAdapter.class,
                        JdkLoggerAdapter.class
                );
                for (Class<? extends LoggerAdapter> clazz : candidates) { //从候选的日志适配器中，依次尝试设置日志处理器
                    try {
                        setLoggerAdapter(clazz.newInstance());
                        break;
                    } catch (Throwable ignored) {
                    }
                }
        }
    }

    /**
     * 流程分析：dubbo日志打印的流程 @pause 07/11
     */
    private LoggerFactory() {
    }

    public static void setLoggerAdapter(String loggerAdapter) {
        if (loggerAdapter != null && loggerAdapter.length() > 0) {
            setLoggerAdapter(ExtensionLoader.getExtensionLoader(LoggerAdapter.class).getExtension(loggerAdapter));
        }
    }

    /**
     * Set logger provider
     *
     * @param loggerAdapter logger provider
     */
    public static void setLoggerAdapter(LoggerAdapter loggerAdapter) { //设置日志适配器类
        if (loggerAdapter != null) {
            Logger logger = loggerAdapter.getLogger(LoggerFactory.class.getName());
            logger.info("using logger: " + loggerAdapter.getClass().getName());
            LoggerFactory.LOGGER_ADAPTER = loggerAdapter; //记录日志处理的适配器类
            for (Map.Entry<String, FailsafeLogger> entry : LOGGERS.entrySet()) {
                entry.getValue().setLogger(LOGGER_ADAPTER.getLogger(entry.getKey()));
            }
        }
    }

    /**
     * Get logger provider
     *
     * @param key the returned logger will be named after clazz
     * @return logger
     */
    public static Logger getLogger(Class<?> key) { //统一使用FailsafeLogger做封装，具体的日志处理器可配置、可选择LOGGER_ADAPTER
        return LOGGERS.computeIfAbsent(key.getName(), name -> new FailsafeLogger(LOGGER_ADAPTER.getLogger(name)));
    }

    /**
     * Get logger provider
     *
     * @param key the returned logger will be named after key
     * @return logger provider
     */
    public static Logger getLogger(String key) {
        return LOGGERS.computeIfAbsent(key, k -> new FailsafeLogger(LOGGER_ADAPTER.getLogger(k)));
    }

    /**
     * Get logging level
     *
     * @return logging level
     */
    public static Level getLevel() {
        return LOGGER_ADAPTER.getLevel();
    }

    /**
     * Set the current logging level
     *
     * @param level logging level
     */
    public static void setLevel(Level level) {
        LOGGER_ADAPTER.setLevel(level);
    }

    /**
     * Get the current logging file
     *
     * @return current logging file
     */
    public static File getFile() {
        return LOGGER_ADAPTER.getFile();
    }

}
