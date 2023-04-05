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

import org.apache.dubbo.common.utils.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WrapperTest {

    @Test
    public void testMain() throws Exception { //创建接口的Wrapper
        Wrapper w = Wrapper.getWrapper(I1.class);
        String[] ns = w.getDeclaredMethodNames();//获取被封装的类中声明的方法
        assertEquals(ns.length, 5);
        ns = w.getMethodNames();//获取被封装的类中的方法（包含继承的方法）
        assertEquals(ns.length, 6);

        Object obj = new Impl1();
        assertEquals(w.getPropertyValue(obj, "name"), "you name"); //获取属性值

        w.setPropertyValue(obj, "name", "changed"); //设置属性值
        assertEquals(w.getPropertyValue(obj, "name"), "changed");

        w.invokeMethod(obj, "hello", new Class<?>[]{String.class}, new Object[]{"qianlei"}); //调用目标类的目标方法
    }

    // bug: DUBBO-132
    @Test
    public void test_unwantedArgument() throws Exception { //测试 未找到对应参数的方法
        Wrapper w = Wrapper.getWrapper(I1.class);
        Object obj = new Impl1();
        try {
            w.invokeMethod(obj, "hello", new Class<?>[]{String.class, String.class},
                    new Object[]{"qianlei", "badboy"});
            fail();
        } catch (NoSuchMethodException expected) {
            System.out.println("未找到方法");
        }
    }

    //bug: DUBBO-425
    @Test
    public void test_makeEmptyClass() throws Exception { //创建类的Wrapper
        Wrapper wrapper = Wrapper.getWrapper(EmptyServiceImpl.class);
        Assert.notNull(wrapper, "获取信息异常");
    }

    @Test
    public void testHasMethod() throws Exception { //测试是否存在方法
        Wrapper w = Wrapper.getWrapper(I1.class);
        Assertions.assertTrue(w.hasMethod("setName"));
        Assertions.assertTrue(w.hasMethod("hello"));
        Assertions.assertTrue(w.hasMethod("showInt"));
        Assertions.assertTrue(w.hasMethod("getFloat"));
        Assertions.assertTrue(w.hasMethod("setFloat"));
        Assertions.assertFalse(w.hasMethod("setFloatXXX")); //不存在此方法
    }

    @Test
    public void testWrapperObject() throws Exception { //测试Object对应的Wrapper
        Wrapper w = Wrapper.getWrapper(Object.class); //对Object进行封装，返回特定的封装类
        Assertions.assertEquals(4, w.getMethodNames().length);
        Assertions.assertEquals(0, w.getPropertyNames().length);
        Assertions.assertNull(w.getPropertyType(null));
    }

    @Test
    public void testGetPropertyValue() throws Exception { //调用Object对应的Wrapper的getPropertyValue方法会抛出异常
        Assertions.assertThrows(NoSuchPropertyException.class, () -> {  //抛出预期的异常
            Wrapper w = Wrapper.getWrapper(Object.class);
            w.getPropertyValue(null, null);
        });
    }

    @Test
    public void testSetPropertyValue() throws Exception { //调用Object对应的Wrapper的setPropertyValue方法会抛出异常
        Assertions.assertThrows(NoSuchPropertyException.class, () -> {
            Wrapper w = Wrapper.getWrapper(Object.class);
            w.setPropertyValue(null, null, null);
        });
    }

    @Test
    public void testInvokeWrapperObject() throws Exception { //测试Object对应的封装类Wrapper
        Wrapper w = Wrapper.getWrapper(Object.class); //Object对应的Wrapper，只对部分方法处理，如getClass()、hashCode()等，且未做额外业务处理
        Object instance = new Object();
        Assertions.assertEquals(instance.getClass(), (Class<?>) w.invokeMethod(instance, "getClass", null, null));
        Assertions.assertEquals(instance.hashCode(), (int) w.invokeMethod(instance, "hashCode", null, null));
        Assertions.assertEquals(instance.toString(), (String) w.invokeMethod(instance, "toString", null, null));
        Assertions.assertTrue((boolean)w.invokeMethod(instance, "equals", null, new Object[] {instance}));
    }

    @Test
    public void testNoSuchMethod() throws Exception { //测试未找到调用方法的场景
        Assertions.assertThrows(NoSuchMethodException.class, () -> {
            Wrapper w = Wrapper.getWrapper(Object.class);
            w.invokeMethod(new Object(), "__XX__", null, null);
        });
    }

    @Test
    public void test_getDeclaredMethodNames_ContainExtendsParentMethods() throws Exception { //测试封装类的声明方法以及继承方法
        assertArrayEquals(new String[]{"hello",}, Wrapper.getWrapper(Parent1.class).getMethodNames()); //获取封装类的方法（包含继承的方法）

        assertArrayEquals(new String[]{}, Wrapper.getWrapper(Son.class).getDeclaredMethodNames()); //获取封装类中声明的方法（不包含继承的方法）
    }

    @Test
    public void test_getMethodNames_ContainExtendsParentMethods() throws Exception {
        assertArrayEquals(new String[]{"hello", "world"}, Wrapper.getWrapper(Son.class).getMethodNames()); //Son继承了Parent1、Parent2，所以包含了父类的方法
    }

    public interface I0 {
        String getName();
    }

    public interface I1 extends I0 {
        void setName(String name);

        void hello(String name);

        int showInt(int v);

        float getFloat();

        void setFloat(float f);
    }

    public interface EmptyService {
    }

    public interface Parent1 {
        void hello();
    }


    public interface Parent2 {
        void world();
    }

    public interface Son extends Parent1, Parent2 {

    }

    public static class Impl0 {
        public float a, b, c;
    }

    public static class Impl1 implements I1 {
        private String name = "you name";

        private float fv = 0;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void hello(String name) {
            System.out.println("hello " + name);
        }

        public int showInt(int v) {
            return v;
        }

        public float getFloat() {
            return fv;
        }

        public void setFloat(float f) {
            fv = f;
        }
    }

    public static class EmptyServiceImpl implements EmptyService {
    }
}