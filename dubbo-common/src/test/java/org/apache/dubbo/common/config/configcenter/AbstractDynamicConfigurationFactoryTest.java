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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.nop.NopDynamicConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AbstractDynamicConfigurationFactory} Test
 *
 * @see AbstractDynamicConfigurationFactory
 * @since 2.7.5
 */
public class AbstractDynamicConfigurationFactoryTest { //@DbT done

    private AbstractDynamicConfigurationFactory factory;

    @BeforeEach //每次测试用例调用前，都会执行
    public void init() {
        factory = new AbstractDynamicConfigurationFactory() { //匿名类
            @Override
            protected DynamicConfiguration createDynamicConfiguration(URL url) {
                return new NopDynamicConfiguration(url);
            }
        };
    }

    @Test
    public void testGetDynamicConfiguration() { //done
        URL url = URL.valueOf("nop://127.0.0.1");
        assertEquals(factory.getDynamicConfiguration(url), factory.getDynamicConfiguration(url));

        /**
         * 输出结果：
         * assertEquals执行正确，无异常
         *
         * 结果分析：
         * 1）执行getDynamicConfiguration(url)时，会判断缓存AbstractDynamicConfigurationFactory#dynamicConfigurations中是否存在服务url对应的
         *    动态配置，若没有则会调用AbstractDynamicConfigurationFactory具体实现类的createDynamicConfiguration创建对应实例
         *
         * 2）同一个url对应的动态配置DynamicConfiguration实例是相同的
         */
    }
}
