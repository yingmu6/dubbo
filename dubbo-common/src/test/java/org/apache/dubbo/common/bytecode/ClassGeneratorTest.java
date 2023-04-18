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
package org.apache.dubbo.common.bytecode;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

interface Builder<T> {
    T getName(Bean bean);

    void setName(Bean bean, T name);
}

public class ClassGeneratorTest { //类生成器测试

    @SuppressWarnings("unchecked")
    @Test
    public void testMain() throws Exception {
        Bean b = new Bean();
        Field fname = null, fs[] = Bean.class.getDeclaredFields();
        for (Field f : fs) {
            f.setAccessible(true); // 设置字段的可见性
            if (f.getName().equals("name")) //找出名称为name的字段
                fname = f;
        }

        ClassGenerator cg = ClassGenerator.newInstance();
        cg.setClassName(Bean.class.getName() + "$Builder"); //指定Class名称
        cg.addInterface(Builder.class); //指定Class实现的接口

        cg.addField("public static java.lang.reflect.Field FNAME;"); //在Class中添加字段，即ClassGenerator#mFields字段中

        cg.addMethod("public Object getName(" + Bean.class.getName() + " o){ boolean[][][] bs = new boolean[0][][]; return (String)FNAME.get($1); }");
        cg.addMethod("public void setName(" + Bean.class.getName() + " o, Object name){ FNAME.set($1, $2); }"); //在Class中添加方法

        cg.addDefaultConstructor(); //添加默认构造函数
        Class<?> cl = cg.toClass(); //转化为Class对象（重点逻辑）
        cl.getField("FNAME").set(null, fname); //创建好Class对象后，可以按Class对应操作

        System.out.println("输出点一：" +cl.getName());
        Builder<String> builder = (Builder<String>) cl.newInstance();
        System.out.println("输出点二：" + b.getName());
        builder.setName(b, "ok");
        System.out.println("输出点三：" + b.getName());

//        System.in.read();
    }

    @Test
    public void testMain0() throws Exception {
        Bean b = new Bean();
        Field fname = null, fs[] = Bean.class.getDeclaredFields();
        for (Field f : fs) { //从Bean中查找字段名为"name"的字段
            f.setAccessible(true);
            if (f.getName().equals("name")) {
                fname = f;
            }
        }

        ClassGenerator cg = ClassGenerator.newInstance();
        cg.setClassName(Bean.class.getName() + "$Builder2"); //设置动态类的名称
        cg.addInterface(Builder.class);

        cg.addField("FNAME", Modifier.PUBLIC | Modifier.STATIC, java.lang.reflect.Field.class);

        cg.addMethod("public Object getName(" + Bean.class.getName() + " o){ boolean[][][] bs = new boolean[0][][]; return (String)FNAME.get($1); }");
        cg.addMethod("public void setName(" + Bean.class.getName() + " o, Object name){ FNAME.set($1, $2); }");

        cg.addDefaultConstructor();

        Class<?> cl = cg.toClass();
        cl.getField("FNAME").set(null, fname);

        System.out.println(cl.getName());
        Builder<String> builder = (Builder<String>) cl.newInstance();
        System.out.println(b.getName());
        builder.setName(b, "ok");
        System.out.println(b.getName());
    }


    @Test
    public void test() throws InstantiationException, IllegalAccessException {
        ClassGenerator classGenerator = ClassGenerator.newInstance();
//        classGenerator.setClassName(Bean.class.getName()); //此处会出现"duplicate class definition"（因为Bean类在当前包下已经定义了）
        classGenerator.setClassName("org.apache.dubbo.common.bytecode.Bean2");

        // 给Bean类加上一个属性 double weight，设置值，并读出值
        classGenerator.addField("private double weight = 5.3;"); //会进行语法编译
        classGenerator.addMethod("public double getWeight() { return weight;}");
        classGenerator.addMethod("public void setWeight(double weight) { this.weight = weight;}");

        // 设置实现的接口
        classGenerator.addInterface(UserInfo.class);
        Class cls = classGenerator.toClass();

        // 通过接口调用具体的方法
        UserInfo userInfo = (UserInfo) cls.newInstance();
        userInfo.setWeight(7.8);
        System.out.println(userInfo.getWeight());
    }

}

interface UserInfo {
    double getWeight();

    void setWeight(double weight);
}

class Bean {
    int age = 30;

    private String name = "qianlei44";

    public int getAge() {
        return age;
    }

    public String getName() {
        return name;
    }

    public static volatile String abc = "df";
}