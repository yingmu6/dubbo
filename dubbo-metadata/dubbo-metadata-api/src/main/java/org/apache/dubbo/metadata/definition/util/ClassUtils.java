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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * 2015/1/27.
 */
public final class ClassUtils {

    /**
     * Get the code source file or class path of the Class passed in.
     * （获取传入的Class的代码源文件或类路径）
     *
     * @param clazz
     * @return Jar file name or class path.
     */
    public static String getCodeSource(Class<?> clazz) {// 获取当前clazz类字节码所在的位置
        ProtectionDomain protectionDomain = clazz.getProtectionDomain();
        if (protectionDomain == null || protectionDomain.getCodeSource() == null) {
            return null;
        }

        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        URL location = codeSource.getLocation();
        if (location == null) {
            return null;
        }

        String path = codeSource.getLocation().toExternalForm(); // 值如："file:/Users/chenshengyong/self-db/dubbo/dubbo-common/target/classes/"

        if (path.endsWith(".jar") && path.contains("/")) {
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return path;
    }

    /**
     * Get all non-static fields of the Class passed in or its super classes.
     * <p>
     *
     * @param clazz Class to parse.
     * @return field list
     */
    public static List<Field> getNonStaticFields(final Class<?> clazz) { //获取类的非静态字段
        List<Field> result = new ArrayList<>();
        Class<?> target = clazz;
        while (target != null) {
            if (JaketConfigurationUtils.isExcludedType(target)) {
                break;
            }

            Field[] fields = target.getDeclaredFields();
            for (Field field : fields) {
                int modifiers = field.getModifiers(); //获取修饰符对应的值
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) { //将修饰符对应的值进行比较
                    continue;
                }

                result.add(field);
            }
            target = target.getSuperclass();
        }

        return result;
    }

    /**
     * Get all public, non-static methods of the Class passed in.
     * <p>
     *
     * @param clazz Class to parse.
     * @return methods list
     */
    public static List<Method> getPublicNonStaticMethods(final Class<?> clazz) { // 获取指定类的所有公共非静态方法
        List<Method> result = new ArrayList<Method>();

        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            int mod = method.getModifiers();
            if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) { //公有的、非静态方法
                result.add(method);
            }
        }
        return result;
    }

    private ClassUtils() {
    }
}
