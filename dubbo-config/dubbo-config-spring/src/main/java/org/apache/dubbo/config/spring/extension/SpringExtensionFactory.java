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
package org.apache.dubbo.config.spring.extension;

import com.alibaba.spring.util.BeanFactoryUtils;
import org.apache.dubbo.common.extension.ExtensionFactory;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Set;

/**
 * SpringExtensionFactory
 */
public class SpringExtensionFactory implements ExtensionFactory { //通过Spring的ApplicationContext获取实例
    private static final Logger logger = LoggerFactory.getLogger(SpringExtensionFactory.class);

    /**
     * ConcurrentHashSet：是apache的工具类，内部是使用Java的 ConcurrentMap<E, Object>实现的
     * 即add()的时候，将添加的元素作为Map的key，既保证Set的不重复性，也能使用ConcurrentMap的线程安全特性
     */
    private static final Set<ApplicationContext> CONTEXTS = new ConcurrentHashSet<ApplicationContext>(); //Concurrent: [kənˈkʌrənt] 并存的，同时发生的；

    /**
     * ConfigurableApplicationContext：（大多数应用程序上下文都将实现的SPI接口，提供了配置应用程序上下文的工具）
     * 1）ConfigurableApplicationContext 接口的作用就是设置上下文ID，设置父应用上下文，添加监听器，刷新容器，关闭，判断是否活跃等方法
     * 2）ConfigurableApplicationContext 直接继承了 ApplicationContext, Lifecycle, Closeable 接口，所以 ApplicationContext 是 ApplicationContext 的子类。
     * 3）ApplicationContext 接口就会发现里面之后get方法，没有set方法，所以子接口就提供了set方法。
     * <p>
     * JVM钩子函数：
     * 1）在某些情况下，我们需要在JVM关闭时做些扫尾的工作，比如删除临时文件、停止日志服务以及内存数据写到磁盘等，为此JVM提供了关闭钩子（shutdown hooks）来做这些事情
     * 2）Runtime封装Java应用运行时的环境。通过Runtime实例，使得应用程序和其运行环境相连接。Runtime是在应用启动期间自动建立，应用程序不能够创建Runtime
     * 但是我们可以通过Runtime.getRuntime()来获得当前应用的Runtime对象引用，通过该引用我们可以获得当前运行环境的相关信息，比如空闲内存、最大内存以及为当前虚拟机添加关闭钩子
     */
    public static void addApplicationContext(ApplicationContext context) { //把ApplicationContext添加到本地缓存，并注册钩子函数
        CONTEXTS.add(context);
        if (context instanceof ConfigurableApplicationContext) { //registerShutdownHook: 注册钩子函数，在应用关闭前，会做一些清理操作，比如销毁应用中的所有bean，改变激活标志等
            ((ConfigurableApplicationContext) context).registerShutdownHook();
        }
    }

    public static void removeApplicationContext(ApplicationContext context) {
        CONTEXTS.remove(context);
    }

    public static Set<ApplicationContext> getContexts() {
        return CONTEXTS;
    }

    // currently for test purpose
    public static void clearContexts() {
        CONTEXTS.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getExtension(Class<T> type, String name) { //从spring容器中查找指定名称、指定类型的bean

        //SPI should be get from SpiExtensionFactory
        if (type.isInterface() && type.isAnnotationPresent(SPI.class)) { //SPI接口不处理，应该有SpiExtensionFactory处理
            return null;
        }

        for (ApplicationContext context : CONTEXTS) { //遍历应用上下文，从上下文中去获取指定name、type对应的实例bean
            T bean = BeanFactoryUtils.getOptionalBean(context, name, type); //bean的名称会做检测：1）不能是空字符串，2）不能包含空格
            if (bean != null) {
                return bean;
            }
        }

        //logger.warn("No spring extension (bean) named:" + name + ", try to find an extension (bean) of type " + type.getName());

        return null;
    }
}
