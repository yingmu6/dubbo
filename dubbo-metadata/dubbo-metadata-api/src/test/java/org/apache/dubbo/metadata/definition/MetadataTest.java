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
import org.apache.dubbo.metadata.definition.builder.CollectionTypeBuilder;
import org.apache.dubbo.metadata.definition.builder.DefaultTypeBuilder;
import org.apache.dubbo.metadata.definition.builder.EnumTypeBuilder;
import org.apache.dubbo.metadata.definition.builder.MapTypeBuilder;
import org.apache.dubbo.metadata.definition.common.*;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TypeDefinitionBuilder
 * <p>
 * 16/9/22.
 */
public class MetadataTest {

    /**
     *
     */
    @Test
    public void testInnerClassType() { //已测，测试内部类
        TypeDefinitionBuilder builder = new TypeDefinitionBuilder();
        TypeDefinition td = builder.build(OuterClass.InnerClass.class, OuterClass.InnerClass.class);
        System.out.println(">> testInnerClassType: " + new Gson().toJson(td)); //值如：{"type":"org.apache.dubbo.metadata.definition.common.OuterClass$InnerClass","properties":{"name":{"type":"java.lang.String","typeBuilderName":"org.apache.dubbo.metadata.definition.builder.DefaultTypeBuilder"}},"typeBuilderName":"org.apache.dubbo.metadata.definition.builder.DefaultTypeBuilder"}

        Assertions.assertEquals("org.apache.dubbo.metadata.definition.common.OuterClass$InnerClass", td.getType()); //type存储的是class类名，如
        Assertions.assertEquals(1, td.getProperties().size());
        Assertions.assertNotNull(td.getProperties().get("name")); //DefaultTypeBuilder中build()方法会设置Properties值
        Assertions.assertEquals(DefaultTypeBuilder.class.getName(), td.getTypeBuilderName()); //此处使用的是默认构建器DefaultTypeBuilder
        ServiceDefinition sd = MetadataUtils.generateMetadata(TestService.class); // 将Class类转化为元数据ServiceDefinition类型
        System.out.println(">> testInnerClassType: " + new Gson().toJson(sd));

        Assertions.assertEquals(TestService.class.getName(), sd.getCanonicalName());
        Assertions.assertEquals(TestService.class.getMethods().length, sd.getMethods().size()); //判断构建的元数据的方法个数是否与原来的相同
        boolean containsType = false;
        for (TypeDefinition type : sd.getTypes()) {
            if (type.getType().equals("org.apache.dubbo.metadata.definition.common.OuterClass$InnerClass")) {
                containsType = true;
                break;
            }
        }
        Assertions.assertTrue(containsType);
    }

    /**
     */
    @Test
    public void testRawMap() { //已测
//        Map和List类型对应的TypeDefinition数据格式
//        { 
//            "type":"org.apache.dubbo.metadata.definition.common.ResultWithRawCollections",  // 类的名称
//            "properties":{ //类的成员属性
//               "list":{ //类的成员属性名称
//                   "type":"java.util.List",  //类的属性类型
//                    "typeBuilderName":"org.apache.dubbo.metadata.definition.builder.CollectionTypeBuilder" //类的属性使用类型构建器
//                },
//                "map":{
//                   "type":"java.util.Map",
//                   "typeBuilderName":"org.apache.dubbo.metadata.definition.builder.MapTypeBuilder"
//            }
//        },
//            "typeBuilderName":"org.apache.dubbo.metadata.definition.builder.DefaultTypeBuilder" // 类使用的类型构建器
//        }

        TypeDefinitionBuilder builder = new TypeDefinitionBuilder();
        TypeDefinition td = builder.build(ResultWithRawCollections.class, ResultWithRawCollections.class); //包含Map、List成员变量
        System.out.println(">> testRawMap: " + new Gson().toJson(td));

        Assertions.assertEquals("org.apache.dubbo.metadata.definition.common.ResultWithRawCollections", td.getType());
        Assertions.assertEquals(2, td.getProperties().size()); //此处的td是ResultWithRawCollections对应的TypeDefinition，所以此处的properties值的值该类中的成员属性
        Assertions.assertEquals("java.util.Map", td.getProperties().get("map").getType());
        Assertions.assertEquals(MapTypeBuilder.class.getName(), td.getProperties().get("map").getTypeBuilderName());
        Assertions.assertEquals("java.util.List", td.getProperties().get("list").getType());
        Assertions.assertEquals(CollectionTypeBuilder.class.getName(), td.getProperties().get("list").getTypeBuilderName()); //成员属性判断

        ServiceDefinition sd = MetadataUtils.generateMetadata(TestService.class);
        System.out.println(">> testRawMap: " + new Gson().toJson(sd));

        Assertions.assertEquals(TestService.class.getName(), sd.getCanonicalName());
        Assertions.assertEquals(TestService.class.getMethods().length, sd.getMethods().size());
        boolean containsType = false;
        for (TypeDefinition type : sd.getTypes()) {
            if (type.getType().equals("org.apache.dubbo.metadata.definition.common.ResultWithRawCollections")) {
                containsType = true;
                break;
            }
        }
        Assertions.assertTrue(containsType);
    }

    @Test
    public void testEnum() { //todo @pause
//        Enum对应的TypeDefinition数据格式
//        {
//            "type":"org.apache.dubbo.metadata.definition.common.ColorEnum",
//                "enum":[
//                     "RED",
//                    "YELLOW",
//                    "BLUE"
//                     ],
//            "typeBuilderName":"org.apache.dubbo.metadata.definition.builder.EnumTypeBuilder"
//        }
        TypeDefinitionBuilder builder = new TypeDefinitionBuilder();
        TypeDefinition td = builder.build(ColorEnum.class, ColorEnum.class);
        System.out.println(">> testEnum: " + new Gson().toJson(td));

        Assertions.assertEquals("org.apache.dubbo.metadata.definition.common.ColorEnum", td.getType());
        Assertions.assertEquals(EnumTypeBuilder.class.getName(), td.getTypeBuilderName());
        Assertions.assertEquals(3, td.getEnums().size());
        Assertions.assertTrue(td.getEnums().contains("RED"));
        Assertions.assertTrue(td.getEnums().contains("YELLOW"));
        Assertions.assertTrue(td.getEnums().contains("BLUE"));

        ServiceDefinition sd = MetadataUtils.generateMetadata(TestService.class);
        System.out.println(">> testEnum: " + new Gson().toJson(sd));

        Assertions.assertEquals(TestService.class.getName(), sd.getCanonicalName());
        Assertions.assertEquals(TestService.class.getMethods().length, sd.getMethods().size());
        boolean containsType = false;
        for (TypeDefinition type : sd.getTypes()) {
            if (type.getType().equals("org.apache.dubbo.metadata.definition.common.ColorEnum")) {
                containsType = true;
                break;
            }
        }
        Assertions.assertTrue(containsType);
    }

    @Test
    public void testExtendsMap() {
        TypeDefinitionBuilder builder = new TypeDefinitionBuilder();
        TypeDefinition td = builder.build(ClassExtendsMap.class, ClassExtendsMap.class);
        System.out.println(">> testExtendsMap: " + new Gson().toJson(td));

        Assertions.assertEquals("org.apache.dubbo.metadata.definition.common.ClassExtendsMap", td.getType());
        Assertions.assertEquals(MapTypeBuilder.class.getName(), td.getTypeBuilderName());
        Assertions.assertEquals(0, td.getProperties().size());

        ServiceDefinition sd = MetadataUtils.generateMetadata(TestService.class);
        System.out.println(">> testExtendsMap: " + new Gson().toJson(sd));

        Assertions.assertEquals(TestService.class.getName(), sd.getCanonicalName());
        Assertions.assertEquals(TestService.class.getMethods().length, sd.getMethods().size());
        boolean containsType = false;
        for (TypeDefinition type : sd.getTypes()) {
            if (type.getType().equals("org.apache.dubbo.metadata.definition.common.ClassExtendsMap")) {
                containsType = true;
                break;
            }
        }
        Assertions.assertFalse(containsType);
    }
}
