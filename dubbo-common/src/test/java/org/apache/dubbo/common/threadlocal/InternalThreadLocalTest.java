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

package org.apache.dubbo.common.threadlocal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class InternalThreadLocalTest {

    private static final int THREADS = 10;

    private static final int PERFORMANCE_THREAD_COUNT = 1000; //performance: 高性能的

    private static final int GET_COUNT = 1000000;

    @Test
    public void testInternalThreadLocal() throws InterruptedException { //测试InternalThreadLocal初始化以及使用
        final AtomicInteger index = new AtomicInteger(0);

        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>() { //使用匿名类的方式创建对象（此处只是声明定义，还未真正调用）

            @Override
            protected Integer initialValue() throws Exception { //重写初始化方法，此处InternalThreadLocal维护的值的类型为Integer
                Integer v = index.getAndIncrement();
                System.out.println("thread : " + Thread.currentThread().getName() + " init value : " + v);
                return v;
            }
        };

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(internalThreadLocal::get); //将internalThreadLocal放入线程中执行（internalThreadLocal::get方法会调用initialValue()方法）
            t.start();
        }

        Thread.sleep(2000);
    }

    @Test
    public void testRemoveAll() throws InterruptedException { //测试移除所有维护的InternalThreadLocal变量值
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        internalThreadLocal.set(1); //设置int值
        Assertions.assertEquals(1, (int)internalThreadLocal.get(), "set failed"); //获取值

        internalThreadLocal.set(3);
        Assertions.assertEquals(3, (int)internalThreadLocal.get(), "set failed"); //同一个InternalThreadLocal对象设值，值会被覆盖

        final InternalThreadLocal<String> internalThreadLocalString = new InternalThreadLocal<String>();
        internalThreadLocalString.set("value"); //设置String值
        Assertions.assertEquals("value", internalThreadLocalString.get(), "set failed"); //不同的InternalThreadLocal对象维护的值互不干扰

        final InternalThreadLocal<Double> internalThreadLocalDouble = new InternalThreadLocal<Double>();
        internalThreadLocalDouble.set(22.11); //设置String值
        Assertions.assertEquals(22.11, internalThreadLocalDouble.get(), "set failed");

        /**
         * 到此处时InternalThreadLocalMap中的元素类型为：（InternalThreadLocal设置的值，最终的都会放在InternalThreadLocalMap存储下来）
         * 1）Collections$SetFromMap@1597 （InternalThreadLocal类型的缓存集合）
         * 2）Integer@1598
         * 3）"value"
         * 4）Double@1600
         * 5）UNSET对象，如Object@1601
         * 6）UNSET对象，如Object@1601
         * ......
         * 31）UNSET对象，如Object@1601
         */
        InternalThreadLocal.removeAll(); //移除了所有值后，InternalThreadLocalMap维护的值为UNSET
        Assertions.assertNull(internalThreadLocal.get(), "removeAll failed!"); // 因为当前的InternalThreadLocal没有重新初始化方法initialize，所以默认值为null
        Assertions.assertNull(internalThreadLocalString.get(), "removeAll failed!");
    }

    @Test
    public void testSize() throws InterruptedException { //测试缓存对象的数量
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        internalThreadLocal.set(1);
        Assertions.assertEquals(1, InternalThreadLocal.size(), "size method is wrong!");

        final InternalThreadLocal<String> internalThreadLocalString = new InternalThreadLocal<String>();
        internalThreadLocalString.set("value");
        Assertions.assertEquals(2, InternalThreadLocal.size(), "size method is wrong!");
    }

    @Test
    public void testSetAndGet() { //测试设置值和获取值
        final Integer testVal = 10;
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        internalThreadLocal.set(testVal);
        Assertions.assertEquals(testVal, internalThreadLocal.get(), "set is not equals get");
    }

    @Test
    public void testRemove() { //测试单个值移除
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        internalThreadLocal.set(1);
        Assertions.assertEquals(1, (int)internalThreadLocal.get(), "get method false!");

        internalThreadLocal.remove();
        Assertions.assertNull(internalThreadLocal.get(), "remove failed!");
    }

    @Test
    public void testOnRemove() { //测试单个值移除（子类重写了onRemoval方法）
        final Integer[] valueToRemove = {null};
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>() {
            @Override
            protected void onRemoval(Integer value) throws Exception { //
                //value calculate
                valueToRemove[0] = value + 1;
            }
        };
        internalThreadLocal.set(1);
        Assertions.assertEquals(1, (int)internalThreadLocal.get(), "get method false!");

        internalThreadLocal.remove(); //进行移除，会回调子类重写的onRemoval方法
        Assertions.assertEquals(2, (int)valueToRemove[0], "onRemove method failed!");
    }

    @Test
    public void testMultiThreadSetAndGet() throws InterruptedException { //测试多线程下的线程变量设置和获取
        final Integer testVal1 = 10;
        final Integer testVal2 = 20;
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {

                internalThreadLocal.set(testVal1); //各个线程维护自己的变量值，互不干扰
                Assertions.assertEquals(testVal1, internalThreadLocal.get(), "set is not equals get");
                countDownLatch.countDown();
            }
        });
        t1.start();

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                internalThreadLocal.set(testVal2); //多线程下，使用同一个InternalThreadLocal设置值
                Assertions.assertEquals(testVal2, internalThreadLocal.get(), "set is not equals get");
                countDownLatch.countDown();
            }
        });
        t2.start();
        countDownLatch.await();
    }

    /**
     * print
     * take[2689]ms
     * <p></p>
     * This test is based on a Machine with 4 core and 16g memory.
     */
    @Test
    public void testPerformanceTradition() { //测试性能
        final ThreadLocal<String>[] caches1 = new ThreadLocal[PERFORMANCE_THREAD_COUNT];
        final Thread mainThread = Thread.currentThread();
        for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
            caches1[i] = new ThreadLocal<String>();
        }
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    caches1[i].set("float.lu");
                }
                long start = System.nanoTime();
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    for (int j = 0; j < GET_COUNT; j++) {
                        caches1[i].get();
                    }
                }
                long end = System.nanoTime();
                System.out.println("take[" + TimeUnit.NANOSECONDS.toMillis(end - start) +
                        "]ms");
                LockSupport.unpark(mainThread);
            }
        });
        t1.start();
        LockSupport.park(mainThread);
    }

    /**
     * print
     * take[14]ms
     * <p></p>
     * This test is based on a Machine with 4 core and 16g memory.
     */
    @Test
    public void testPerformance() { //测试InternalThreadLocal的性能
        final InternalThreadLocal<String>[] caches = new InternalThreadLocal[PERFORMANCE_THREAD_COUNT];
        final Thread mainThread = Thread.currentThread();
        for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
            caches[i] = new InternalThreadLocal<String>();
        }
        Thread t = new InternalThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    caches[i].set("float.lu");
                }
                long start = System.nanoTime();
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    for (int j = 0; j < GET_COUNT; j++) {
                        caches[i].get();
                    }
                }
                long end = System.nanoTime();
                System.out.println("take[" + TimeUnit.NANOSECONDS.toMillis(end - start) +
                        "]ms");
                LockSupport.unpark(mainThread);
            }
        });
        t.start();
        LockSupport.park(mainThread);
    }
}