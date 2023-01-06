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

import org.apache.dubbo.common.URL;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class RpcContextTest {

    @Test
    public void testGetContext() { //获取RpcContext

        /**
         * 1）RpcContext.getContext()从InternalThreadLocal获取上下文实例，若值为UNSET，会回调initialValue()进行初始化
         * 2）RpcContext.removeContext()或RpcContext.getServerContext()移除上下文，会将InternalThreadLocalMap中维护的对应index的值设置为UNSET
         * 3）因为移除上下文后，InternalThreadLocalMap中维护的元素为UNSET元素，所以会回调initialValue()进行初始化，所以产生的RpcContext实例就不一样
         *
         * 特别说明：
         * 如果在debug时，选择"Add to Watchers"查看方法的执行结果时，相当于会把方法执行一遍，所以debug时若对方法观察，需要对这一点进行注意
         * （应该是启动了另外线程执行了，因为dubug时看不到对应的执行）
         */
        RpcContext rpcContext = RpcContext.getContext();
        Assertions.assertNotNull(rpcContext); //若当前线程没有设置上下文信息，会进行初始化处理，所以不为null

        Assertions.assertEquals(rpcContext, RpcContext.getContext()); //todo @pause

        RpcContext.removeContext(); //移除上下文以后，执行RpcContext.getContext()会产生新的RpcContext对象，不移除上下文的话，不管调用多少次RpcContext.getContext()，返回的都是同一个对象
        // if null, will return the initialize value.
        //Assertions.assertNull(RpcContext.getContext());
        Assertions.assertNotNull(RpcContext.getContext());
        Assertions.assertNotEquals(rpcContext, RpcContext.getContext()); //RpcContext实例时不相同

        RpcContext serverRpcContext = RpcContext.getServerContext(); //获取服务端对应的上下文
        Assertions.assertNotNull(serverRpcContext);

        RpcContext.removeServerContext(); //移除服务端对应的上下文
        Assertions.assertNotEquals(serverRpcContext, RpcContext.getServerContext());

    }

    @Test
    public void testAddress() { //测试RpcContext设置的地址信息
        RpcContext context = RpcContext.getContext(); //获取RpcContext实例
        context.setLocalAddress("127.0.0.1", 20880); //会创建一个InetSocketAddress进行存储
        Assertions.assertEquals(20880, context.getLocalAddress().getPort());
        Assertions.assertEquals("127.0.0.1:20880", context.getLocalAddressString()); //获取本地地址，即hostname与port拼接的字符串

        context.setRemoteAddress("127.0.0.1", 20880); //与LocalAddress处理方式相同
        Assertions.assertEquals(20880, context.getRemoteAddress().getPort());
        Assertions.assertEquals("127.0.0.1:20880", context.getRemoteAddressString());

        context.setRemoteAddress("127.0.0.1", -1); //端口号小于0，会被置为0
        context.setLocalAddress("127.0.0.1", -1);
        Assertions.assertEquals(0, context.getRemoteAddress().getPort());
        Assertions.assertEquals(0, context.getLocalAddress().getPort());   //获取port
        Assertions.assertEquals("127.0.0.1", context.getRemoteHostName()); //获取hostname
        Assertions.assertEquals("127.0.0.1", context.getLocalHostName());
    }

    @Test
    public void testCheckSide() { //检查端侧

        RpcContext context = RpcContext.getContext();

        //TODO fix npe
        //context.isProviderSide();

        context.setUrl(URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1")); //设置调用的url信息
        Assertions.assertFalse(context.isConsumerSide()); //url中没有设置side参数值，默认为provider，即提供者端
        Assertions.assertTrue(context.isProviderSide());

        context.setUrl(URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&side=consumer"));
        Assertions.assertTrue(context.isConsumerSide()); //url中设置了side参数值，取对应的参数值做判断
        Assertions.assertFalse(context.isProviderSide());
    }

    @Test
    public void testAttachments() { //附加参数处理

        RpcContext context = RpcContext.getContext();
        Map<String, Object> map = new HashMap<>();
        map.put("_11", "1111");
        map.put("_22", "2222");
        map.put(".33", "3333");

        context.setObjectAttachments(map); //设置附加参数
        Assertions.assertEquals(map, context.getObjectAttachments()); //map进行equals比较时，会通过AbstractMap中重写的equals()方法，对Map中的元素依次比较

        Assertions.assertEquals("1111", context.getAttachment("_11"));
        context.setAttachment("_11", "11.11"); //数据会进行更新
        Assertions.assertEquals("11.11", context.getAttachment("_11"));

        context.setAttachment(null, "22222"); //key可以为null
        context.setAttachment("_22", null); //值设置为null
        Assertions.assertEquals("22222", context.getAttachment(null)); //附加参数使用的是HashMap，可以设置
        Assertions.assertNull(context.getAttachment("_22"));

        Assertions.assertNull(context.getAttachment("_33"));
        Assertions.assertEquals("3333", context.getAttachment(".33"));

        context.clearAttachments(); //清除附加参数
        Assertions.assertNull(context.getAttachment("_11"));
    }

    @Test
    public void testObject() { //设置对象值

        RpcContext context = RpcContext.getContext();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_11", "1111");
        map.put("_22", "2222");
        map.put(".33", "3333");

        map.forEach(context::set); //

        Assertions.assertEquals(map, context.get());

        Assertions.assertEquals("1111", context.get("_11"));
        context.set("_11", "11.11");
        Assertions.assertEquals("11.11", context.get("_11"));

        context.set(null, "22222");
        context.set("_22", null);
        Assertions.assertEquals("22222", context.get(null));
        Assertions.assertNull(context.get("_22"));

        Assertions.assertNull(context.get("_33"));
        Assertions.assertEquals("3333", context.get(".33"));

        map.keySet().forEach(context::remove);
        Assertions.assertNull(context.get("_11"));
    }

    @Test
    public void testAsync() { //测试异步上下文

        RpcContext rpcContext = RpcContext.getContext();
        Assertions.assertFalse(rpcContext.isAsyncStarted());

        AsyncContext asyncContext = RpcContext.startAsync(); //启动异步后，异步启动标志就会置为true
        Assertions.assertTrue(rpcContext.isAsyncStarted());

        asyncContext.write(new Object()); //写入AsyncContextImpl.CompletableFuture<Object> 异步结果值
        Assertions.assertTrue(((AsyncContextImpl) asyncContext).getInternalFuture().isDone()); //isDone()是按CompletableFuture中维护的result是否为空判断

        rpcContext.stopAsync();
        Assertions.assertTrue(rpcContext.isAsyncStarted()); //stopAsync()只影响结束标志，不影响开始标志
        RpcContext.removeContext();
    }

    @Test
    public void testAsyncCall() { //异步调用
        CompletableFuture<String> rpcFuture = RpcContext.getContext().asyncCall(() -> {
            throw new NullPointerException(); //执行的线程出现异常，所以会返回带有异常的CompletableFuture
//            return "hhaaaaa";
        });

        rpcFuture.whenComplete((rpcResult, throwable) -> { //当异步处理完成，返回结果或异常信息
            System.out.println("haha:" + throwable.toString());
            Assertions.assertNull(rpcResult); //结果为空，因为有异常
            Assertions.assertTrue(throwable instanceof RpcException);
            Assertions.assertTrue(throwable.getCause() instanceof NullPointerException);
        });

        Assertions.assertThrows(ExecutionException.class, rpcFuture::get);

        rpcFuture = rpcFuture.exceptionally(throwable -> "mock success");

        Assertions.assertEquals("mock success", rpcFuture.join());
    }

    @Test
    public void testObjectAttachment() { //测试附加参数
        RpcContext rpcContext = RpcContext.getContext();

        rpcContext.setAttachment("objectKey1", "value1");
        rpcContext.setAttachment("objectKey2", "value2");
        rpcContext.setAttachment("objectKey3", 1); // 设置附加参数值

        Assertions.assertEquals("value1", rpcContext.getObjectAttachment("objectKey1"));
        Assertions.assertEquals("value2", rpcContext.getAttachment("objectKey2")); //获取参数对应的字符串值
        Assertions.assertNull(rpcContext.getAttachment("objectKey3")); //因为key对应的value不是字符串类型，所以返回null
        Assertions.assertEquals(1, rpcContext.getObjectAttachment("objectKey3"));
        Assertions.assertEquals(3, rpcContext.getObjectAttachments().size());

        rpcContext.clearAttachments(); //清除Map中参数值，size会被置为0
        Assertions.assertEquals(0, rpcContext.getObjectAttachments().size());

        HashMap<String, Object> map = new HashMap<>();
        map.put("mapKey1", 1);
        map.put("mapKey2", "mapValue2");
        rpcContext.setObjectAttachments(map);
        Assertions.assertEquals(map, rpcContext.getObjectAttachments());
    }
}
