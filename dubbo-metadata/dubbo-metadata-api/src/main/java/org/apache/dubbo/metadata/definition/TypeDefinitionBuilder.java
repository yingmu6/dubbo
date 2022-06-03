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
package org.apache.dubbo.metadata.definition;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.metadata.definition.builder.DefaultTypeBuilder;
import org.apache.dubbo.metadata.definition.builder.TypeBuilder;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;

import java.lang.reflect.Type;
import java.util.*;

import static org.apache.dubbo.common.utils.ClassUtils.isSimpleType;

/**
 * 2015/1/27.
 */
public class TypeDefinitionBuilder {
    private static final Logger logger = LoggerFactory.getLogger(TypeDefinitionBuilder.class);
    static final List<TypeBuilder> BUILDERS;

    static { //静态块：类加载时就会执行，
        ExtensionLoader<TypeBuilder> extensionLoader = ExtensionLoader.getExtensionLoader(TypeBuilder.class);
        Set<TypeBuilder> tbs = extensionLoader.getSupportedExtensionInstances(); //TypeBuilder是SPI接口，此处会取出TypeBuilder支持的扩展实例
        BUILDERS = new ArrayList<>(tbs);
    }

    // 构建类型定义TypeDefinition
    public static TypeDefinition build(Type type, Class<?> clazz, Map<Class<?>, TypeDefinition> typeCache) {
        TypeBuilder builder = getGenericTypeBuilder(type, clazz);
        TypeDefinition td;
        if (builder != null) {
            td = builder.build(type, clazz, typeCache); //传入的type为数组类型时，匹配到构建器ArrayTypeBuilder
            td.setTypeBuilderName(builder.getClass().getName()); //设置类型构建器名称
        } else { //若没有找到构建器，则使用默认构建器（对象类型会使用默认类型构建器）
            td = DefaultTypeBuilder.build(clazz, typeCache);
            td.setTypeBuilderName(DefaultTypeBuilder.class.getName());
        }
        if (isSimpleType(clazz)) { // changed since 2.7.6
            td.setProperties(null);
        }
        return td;
    }

    private static TypeBuilder getGenericTypeBuilder(Type type, Class<?> clazz) { //获取通用的类型构造器
        for (TypeBuilder builder : BUILDERS) {
            try {
                if (builder.accept(type, clazz)) { //查找符合条件的类型构建器
                    return builder;
                }
            } catch (NoClassDefFoundError cnfe) {
                //ignore
                logger.info("Throw classNotFound (" + cnfe.getMessage() + ") in " + builder.getClass());
            }
        }
        return null;
    }

    private Map<Class<?>, TypeDefinition> typeCache = new HashMap<>(); //将TypeDefinition进行缓存

    public TypeDefinition build(Type type, Class<?> clazz) {
        return build(type, clazz, typeCache);
    }

    public List<TypeDefinition> getTypeDefinitions() {
        return new ArrayList<>(typeCache.values());
    }

}
