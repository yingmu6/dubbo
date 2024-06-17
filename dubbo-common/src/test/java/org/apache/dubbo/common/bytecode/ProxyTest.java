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

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProxyTest { //@DtY-Doing

    /**
     * 知识点：
     *
     * 知识点概括：
     * 1）
     */

    @Test
    public void testMain() throws Exception {

        /**
         * 接口代理对象的产生及使用流程：
         * 1）Proxy.getProxy(接口列表)：会通过javassist创建两个类的Class，一个是接口代理对象的Class，一个是Proxy的Class，并返回Proxy的实例。
         *   其中接口代理对象Class的构造函数包含InvocationHandler，方法体中都是接收传入的参数，然后传入InvocationHandler的invoke方法实现调用；
         *
         * 2）Proxy的newInstance方法体中会将InvocationHandler实例传入接口代理对象的构造函数中，最终创建接口代理的实例。
         *
         * 3）进行接口实例对象调用时，如instance.setName(...)，会进入javassist产生的代理对象的实例中，然后代理对象实例中的方法逻辑，都会组装参数，
         *    最终传入InvocationHandler的invoke方法，所以调用方只要对应实现InvocationHandler的invoke逻辑即可，而产生代理对象的逻辑由Dubbo框架封装了。
         *
         * 注明：不管接口代理实例调用哪个方法，最终都会进入到InvocationHandler的invoke方法中，在该方法中，根据参数的不同，可实现各个方法的差异处理。
         *      本质就是接口代理实例中方法的处理逻辑，委派给InvocationHandler处理了。
         */
        Proxy proxy = Proxy.getProxy(ITest.class, ITest.class);
        ITest instance = (ITest) proxy.newInstance((proxy1, method, args) -> { //此处的lambda表达式，表示的是一个InvocationHandler实例
            if ("getName".equals(method.getName())) {
                assertEquals(args.length, 0);
            } else if ("setName".equals(method.getName())) {
                assertEquals(args.length, 2);
                assertEquals(args[0], "qianlei");
                assertEquals(args[1], "hello");
            }
            return null;
        });

        assertNull(instance.getName());
        instance.setName("qianlei", "hello"); //执行具体实例的具体方法调用
    }

    @Test
    public void testCglibProxy() throws Exception {
        ITest test = (ITest) Proxy.getProxy(ITest.class).newInstance((proxy, method, args) -> {
            System.out.println(method.getName());
            return null;
        });

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(test.getClass());
        enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> null);
        try {
            enhancer.create();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Assertions.fail();
        }
    }

    public interface ITest {
        String getName();

        void setName(String name, String name2);

        static String sayBye() {
            return "Bye!";
        }
    }
}