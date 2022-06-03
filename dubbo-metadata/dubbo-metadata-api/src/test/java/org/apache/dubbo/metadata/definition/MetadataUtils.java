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

import org.apache.dubbo.metadata.definition.model.MethodDefinition;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;
import org.apache.dubbo.metadata.definition.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * generate metadata
 * <p>
 * 2017-4-17 14:33:24
 */
public class MetadataUtils {

    /**
     * com.taobao.hsf.metadata.store.MetadataInfoStoreServiceRedis.publishClassInfo(ServiceMetadata) 生成元数据的代码
     */
    public static ServiceDefinition generateMetadata(Class<?> interfaceClass) { //为指定Class产生ServiceDefinition
        ServiceDefinition sd = new ServiceDefinition();
        sd.setCanonicalName(interfaceClass.getCanonicalName()); //CanonicalName：规范的名称
        sd.setCodeSource(ClassUtils.getCodeSource(interfaceClass)); //codeSource：字节码对应位置，如file:/Users/chenshengyong/self-db/dubbo/dubbo-metadata/dubbo-metadata-api/target/test-classes/

        TypeDefinitionBuilder builder = new TypeDefinitionBuilder();
        List<Method> methods = ClassUtils.getPublicNonStaticMethods(interfaceClass); //获取公有且非静态的方法列表
        for (Method method : methods) {
            MethodDefinition md = new MethodDefinition();
            md.setName(method.getName());

            Class<?>[] paramTypes = method.getParameterTypes();
            Type[] genericParamTypes = method.getGenericParameterTypes();

            String[] parameterTypes = new String[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) { //处理参数列表类型
                try {
                    TypeDefinition td = builder.build(genericParamTypes[i], paramTypes[i]);
                    parameterTypes[i] = td.getType();
                } catch (Exception e) { //若构建异常，则直接取反射获取到的类型
                    parameterTypes[i] = paramTypes[i].getName();
                }
            }
            md.setParameterTypes(parameterTypes); //设置参数类型
            try {
                TypeDefinition td = builder.build(method.getGenericReturnType(), method.getReturnType()); //处理返回值类型
                md.setReturnType(td.getType());  //设置返回类型
            } catch (Exception e) {
                md.setReturnType(method.getReturnType().getName());
            }

            sd.getMethods().add(md);
        }

        sd.setTypes(builder.getTypeDefinitions());
        return sd;
    }
}
