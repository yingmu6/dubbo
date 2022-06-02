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
package org.apache.dubbo.metadata.definition.util;

import org.apache.dubbo.common.utils.StringUtils;

import java.io.InputStream;
import java.util.Properties;

/**
 * 2015/1/27.
 */
public class JaketConfigurationUtils {

    private static final String CONFIGURATION_FILE = "jaket.properties";

    private static String[] includedInterfacePackages;
    private static String[] includedTypePackages;
    private static String[] closedTypes;

    static { //类加载时就执行了，在对象方法执行前就已经处理好了
        Properties props = new Properties();
        InputStream inStream = JaketConfigurationUtils.class.getClassLoader().getResourceAsStream(CONFIGURATION_FILE);
        try {
            props.load(inStream); //把文件中的内容加载到Properties对象中
            String value = (String) props.get("included_interface_packages"); //待配置符合条件的文件用于测试
            if (StringUtils.isNotEmpty(value)) { //解析属性的值
                includedInterfacePackages = value.split(",");
            }

            value = props.getProperty("included_type_packages");
            if (StringUtils.isNotEmpty(value)) {
                includedTypePackages = value.split(",");
            }

            value = props.getProperty("closed_types");
            if (StringUtils.isNotEmpty(value)) {
                closedTypes = value.split(",");
            }

        } catch (Throwable e) {
            // Ignore it.
        }
    }

    public static boolean isExcludedInterface(Class<?> clazz) {
        if (includedInterfacePackages == null || includedInterfacePackages.length == 0) {
            return false;
        }

        for (String packagePrefix : includedInterfacePackages) {
            if (clazz.getCanonicalName().startsWith(packagePrefix)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isExcludedType(Class<?> clazz) { //判断是否是排除的类型（待构建符合条件数据，进行分析）
        if (includedTypePackages == null || includedTypePackages.length == 0) {
            return false;
        }

        for (String packagePrefix : includedTypePackages) {
            if (clazz.getCanonicalName().startsWith(packagePrefix)) {
                return false;
            }
        }

        return true;
    }

    public static boolean needAnalyzing(Class<?> clazz) {
        String canonicalName = clazz.getCanonicalName(); //基本类型也能输出，如int

        if (closedTypes != null && closedTypes.length > 0) {
            for (String type : closedTypes) {
                if (canonicalName.startsWith(type)) {
                    return false;
                }
            }
        }

        return !isExcludedType(clazz);
    }

}
