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

import org.apache.dubbo.common.threadlocal.InternalThreadLocal;
import org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter;

import java.util.concurrent.CompletableFuture;

/**
 * Used for async call scenario（用于异步调用场景）. But if the method you are calling has a {@link CompletableFuture<?>} signature
 * you do not need to use this class since you will get a Future response directly.（如果调用的方法签名上包含CompletableFuture，就不需要用当前类了，直接可以用Future响应了）
 * <p>
 * Remember to save the Future reference before making another call using the same thread, otherwise,
 * the current Future will be override by the new one, which means you will lose the chance get the return value.
 * （注意：在同一个线程中调用另一个线程时，需要保存Future引用，否则会被覆盖更新）
 */
public class FutureContext {

    private static InternalThreadLocal<FutureContext> futureTL = new InternalThreadLocal<FutureContext>() { //维护的线程局部变量
        @Override
        protected FutureContext initialValue() {
            return new FutureContext();
        }
    };

    public static FutureContext getContext() { //提供静态方法，获取FutureContext实例
        return futureTL.get();
    }

    private CompletableFuture<?> future;
    private CompletableFuture<?> compatibleFuture; //维护了CompletableFuture变量

    /**
     * get future.
     *
     * @param <T>
     * @return future
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getCompletableFuture() {
        return (CompletableFuture<T>) future;
    }

    /**
     * set future.
     *
     * @param future
     */
    public void setFuture(CompletableFuture<?> future) {
        this.future = future;
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getCompatibleCompletableFuture() {
        return (CompletableFuture<T>) compatibleFuture;
    }

    /**
     * Guarantee（确保） 'using org.apache.dubbo.rpc.RpcContext.getFuture() before proxy returns' can work,（确保代理返回之前使用RpcContext.getFuture()能正常工作 ） a typical scenario is（一个典型的场景是）:
     * <pre>{@code
     *      public final class TracingFilter implements Filter {
     *          public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
     *              Result result = invoker.invoke(invocation);
     *              Future<Object> future = rpcContext.getFuture();
     *              if (future instanceof FutureAdapter) { //2.7.3版本之前，需要从结果中拿到Future，然后再做转换，最后设置回调
     *                  ((FutureAdapter) future).getFuture().setCallback(new FinishSpanCallback(span));
     *               }
     *              ......
     *          }
     *      }
     * }</pre>
     *
     * Start from 2.7.3, you don't have to get Future from RpcContext, we recommend using Result directly:
     * <pre>{@code
     *      public final class TracingFilter implements Filter {
     *          public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
     *              Result result = invoker.invoke(invocation);
     *              result.getResponseFuture().whenComplete(new FinishSpanCallback(span)); //2.7.3版本以后，返回的Result中是包含CompletableFuture，可以直接设置回调
     *              ......
     *          }
     *      }
     * }</pre>
     *
     */
    @Deprecated
    public void setCompatibleFuture(CompletableFuture<?> compatibleFuture) {
        this.compatibleFuture = compatibleFuture;
        if (compatibleFuture != null) {
            this.setFuture(new FutureAdapter(compatibleFuture));
        }
    }

}
