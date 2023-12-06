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
package org.apache.dubbo.common.extension.support;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.SPI;

import java.util.Comparator;

/**
 * OrderComparator
 */
public class WrapperComparator implements Comparator<Object> {

    public static final Comparator<Object> COMPARATOR = new WrapperComparator();

    @Override
    public int compare(Object o1, Object o2) { //返回的数值说明，负整数：第一个参数小于第二参数，0：等于，正整数：大于
        if (o1 == null && o2 == null) {
            return 0;
        }
        if (o1 == null) {
            return -1;
        }
        if (o2 == null) { //哪个参数为null，哪个就小，两个都为null，就相等
            return 1;
        }
        if (o1.equals(o2)) { //equals比较两个对象是否相等，若不等，再基于排序参数order比较
            return 0;
        }

        Class clazz1 = (Class) o1;
        Class clazz2 = (Class) o2;

        Class<?> inf = findSpi(clazz1);

        OrderInfo a1 = parseOrder(clazz1); //将对象解析为ObjectInfo
        OrderInfo a2 = parseOrder(clazz2);

        int n1 = a1 == null ? 0 : a1.order;
        int n2 = a2 == null ? 0 : a2.order;
        // never return 0 even if n1 equals n2, otherwise, o1 and o2 will override each other in collection like HashSet（若使用order值来比较，只返回1或-1，避免返回0，在HashSet等集合被由于元素相等被覆盖掉）
        return n1 > n2 ? 1 : -1;
    }

    private Class<?> findSpi(Class clazz) {
        if (clazz.getInterfaces().length == 0) {
            return null;
        }

        for (Class<?> intf : clazz.getInterfaces()) { //找到扩展类实现的SPI接口
            if (intf.isAnnotationPresent(SPI.class)) {
                return intf;
            } else {
                Class result = findSpi(intf); //递归寻找SPI接口
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private OrderInfo parseOrder(Class<?> clazz) { //将扩展类对象转换为OrderInfo，用于排序
        OrderInfo info = new OrderInfo();
        if (clazz.isAnnotationPresent(Activate.class)) { //@Activate注解中的order值用于排序
            Activate activate = clazz.getAnnotation(Activate.class);
            info.order = activate.order();
        } else if (clazz.isAnnotationPresent(com.alibaba.dubbo.common.extension.Activate.class)) {
            com.alibaba.dubbo.common.extension.Activate activate = clazz.getAnnotation(
                    com.alibaba.dubbo.common.extension.Activate.class);
            info.order = activate.order();
        }
        return info;
    }

    private static class OrderInfo {
        private int order;
    }
}