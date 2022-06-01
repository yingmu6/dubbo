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

import com.google.gson.Gson;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.definition.model.MethodDefinition;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;
import org.apache.dubbo.metadata.definition.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 2015/1/27.
 */
public final class ServiceDefinitionBuilder {

    /**
     * Describe a Java interface in {@link ServiceDefinition}.
     *
     * @return Service description
     */
    public static ServiceDefinition build(final Class<?> interfaceClass) {
        ServiceDefinition sd = new ServiceDefinition();
        build(sd, interfaceClass);
        return sd;
    }

    public static FullServiceDefinition buildFullDefinition(final Class<?> interfaceClass) {
        FullServiceDefinition sd = new FullServiceDefinition();
        build(sd, interfaceClass);
        return sd;
    }

    public static FullServiceDefinition buildFullDefinition(final Class<?> interfaceClass, Map<String, String> params) {
        FullServiceDefinition sd = new FullServiceDefinition();
        build(sd, interfaceClass);
        sd.setParameters(params);
        return sd;
    }

    /**
     * 通过反射机制，解析出Class类中方法名、参数类型等，并设置组装起来返回
     */
    public static <T extends ServiceDefinition> void build(T sd, final Class<?> interfaceClass) { //构建服务定义数据ServiceDefinition
        sd.setCanonicalName(interfaceClass.getCanonicalName());
        sd.setCodeSource(ClassUtils.getCodeSource(interfaceClass));

        TypeDefinitionBuilder builder = new TypeDefinitionBuilder();
        List<Method> methods = ClassUtils.getPublicNonStaticMethods(interfaceClass);
        for (Method method : methods) { //遍历暴露接口的所有公有且非静态的方法，提取Method的属性值，并设置到方法模型中MethodDefinition
            MethodDefinition md = new MethodDefinition();
            md.setName(method.getName());

            // Process parameter types.
            Class<?>[] paramTypes = method.getParameterTypes();
            Type[] genericParamTypes = method.getGenericParameterTypes();

            String[] parameterTypes = new String[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                TypeDefinition td = builder.build(genericParamTypes[i], paramTypes[i]);
                parameterTypes[i] = td.getType();
            }
            md.setParameterTypes(parameterTypes);

            // Process return type. 处理返回类型
            TypeDefinition td = builder.build(method.getGenericReturnType(), method.getReturnType());
            md.setReturnType(td.getType());

            sd.getMethods().add(md); //为ServiceDefinition添加MethodDefinition
        }

        sd.setTypes(builder.getTypeDefinitions());
    }

    /**
     * Describe a Java interface in Json schema.
     *
     * @return Service description
     */
    public static String schema(final Class<?> clazz) {
        ServiceDefinition sd = build(clazz);
        Gson gson = new Gson();
        return gson.toJson(sd);
    }

    private ServiceDefinitionBuilder() {
    }
}


