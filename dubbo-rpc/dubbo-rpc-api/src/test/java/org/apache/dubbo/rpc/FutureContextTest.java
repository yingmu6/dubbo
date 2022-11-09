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
package org.apache.dubbo.rpc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class FutureContextTest {

    @Test
    public void testFutureContext() throws Exception {
        Thread thread1 = new Thread(() -> { //线程执行体
            FutureContext.getContext().setFuture(CompletableFuture.completedFuture("future from thread1")); //设置FutureContext维护的CompletableFuture变量值
            try {
                Thread.sleep(500);
                Assertions.assertEquals("future from thread1", FutureContext.getContext().getCompletableFuture().get()); //因为设置了CompletableFuture的值，所以此处能取到值
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread1.start();

        Thread.sleep(100);

        Thread thread2 = new Thread(() -> {
            CompletableFuture future = FutureContext.getContext().getCompletableFuture();
            Assertions.assertNull(future); //此处因为没有设置CompletableFuture值，所以获取时值为null
            FutureContext.getContext().setFuture(CompletableFuture.completedFuture("future from thread2"));

            // 此处时新增加的测试内容
            try {
                Assertions.assertEquals("future from thread2", FutureContext.getContext().getCompletableFuture().get());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        thread2.start();

        Thread.sleep(1000);
    }
}
