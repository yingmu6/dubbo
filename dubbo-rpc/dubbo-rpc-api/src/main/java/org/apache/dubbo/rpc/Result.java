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

import org.apache.dubbo.common.Experimental;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;


/**
 * (API, Prototype（原型）, NonThreadSafe（非线程安全）)
 * <p>
 * An RPC {@link Result}.
 * <p>
 * Known implementations are:
 * 1. {@link AsyncRpcResult}, it's a {@link CompletionStage} whose underlying value signifies（表示） the return value of an RPC call
 * 2. {@link AppResponse}, it inevitably inherits（不可避免地继承） {@link CompletionStage} and {@link Future}, but you should never treat（对待） AppResponse as a type of Future,
 * instead, it is a normal concrete（具体的） type.
 *
 * @serial Don't change the class name and package name.
 * @see org.apache.dubbo.rpc.Invoker#invoke(Invocation)
 * @see AppResponse
 */
public interface Result extends Serializable { //RPC调用结果的接口

    /**
     * Result的几个实现类，各有什么用途？做下比较
     * 解答：主要有AsyncRpcResult、AppResponse
     *
     * 把RpcResult替换为AppResponse的原因：
     * 为了更好的支持异步回调。RpcResult被替换成了AppResponse，而Filter链路上传递的对象变成了AsyncRpcResult，
     * 这个修改其实是需要用户明确理解的（主要是对扩展Filter的用户），所以选择删除RpcResult其中一个重要的目的也是为了让升级者明确的感知到以上变化的存在，防止误用
     *
     * Dubbo2.7.x异步改造是对Dubbo2.6.x异步功能的增强，引入的 CompletableFuture既支持Future又支持Callback的调用方式，使用方可以根据需要自行选择。
     * https://gentryhuang.com/posts/c812f120/index.html
     *
     *
     * Dubbo 的远程调用中大致可以分为以上 4 种调用方式：
     * oneway: 客户端发送消息后，不需要接收响应。对于不需要关心服务响应结果的请求适合 oneway 通信。
     * sync: Dubbo 默认的通信方式，即同步调用。
     * async: 异步调用范畴，使用 Future 的方式获取结果。
     * future: 异步调用范畴，使用 CompletableFuture 获取结果，也支持通过 Future 的方式获取结果。
     *
     */


    /**
     * Get invoke result.
     *
     * @return result. if no result return null.
     */
    Object getValue(); //获取调用结果值

    void setValue(Object value); //设置结果值

    /**
     * Get exception.
     *
     * @return exception. if no exception return null.
     */
    Throwable getException();

    void setException(Throwable t);

    /**
     * Has exception.
     *
     * @return has exception.
     */
    boolean hasException();

    /**
     * Recreate.
     * <p>
     * <code>
     * if (hasException()) {  //若有异常则抛出异常，没有异常则返回具体值
     * throw getException();
     * } else {
     * return getValue();
     * }
     * </code>
     *
     * @return result.
     * @throws if has exception throw it.
     */
    Object recreate() throws Throwable; //@csy-02-22 重新创建是怎样的逻辑？解：若有异常则设置异常栈并抛出，否则返回维护的result值

    /**
     * get attachments.
     *
     * @return attachments.
     */
    Map<String, String> getAttachments(); //获取附加参数（即AppResponse维护的成员属性值 Map<String, Object> attachments，将值转换为String格式）

    // ------以下是相对于2.5.6新增的内容------
    /**
     * get attachments.
     *
     * @return attachments.
     */
    @Experimental("Experiment api for supporting Object transmission")
    Map<String, Object> getObjectAttachments(); //获取附加参数（即AppResponse维护的成员属性值 Map<String, Object> attachments，直接取值）

    /**
     * Add the specified map to existing attachments in this instance.
     *
     * @param map
     */
    void addAttachments(Map<String, String> map);

    /**
     * Add the specified map to existing attachments in this instance.
     *
     * @param map（支持使用对象传输）
     */
    @Experimental("Experiment api for supporting Object transmission")
    void addObjectAttachments(Map<String, Object> map);

    /**
     * Replace the existing attachments with the specified param.
     *
     * @param map
     */
    void setAttachments(Map<String, String> map);

    /**
     * Replace the existing attachments with the specified param.
     *
     * @param map
     */
    @Experimental("Experiment api for supporting Object transmission")
    void setObjectAttachments(Map<String, Object> map);

    /**
     * get attachment by key.
     *
     * @return attachment value.
     */
    String getAttachment(String key);

    /**
     * get attachment by key.
     *
     * @return attachment value.
     */
    @Experimental("Experiment api for supporting Object transmission")
    Object getObjectAttachment(String key);

    /**
     * get attachment by key with default value.
     *
     * @return attachment value.
     */
    String getAttachment(String key, String defaultValue);

    /**
     * get attachment by key with default value.
     *
     * @return attachment value.
     */
    @Experimental("Experiment api for supporting Object transmission")
    Object getObjectAttachment(String key, Object defaultValue);

    void setAttachment(String key, String value);

    @Experimental("Experiment api for supporting Object transmission")
    void setAttachment(String key, Object value);

    @Experimental("Experiment api for supporting Object transmission")
    void setObjectAttachment(String key, Object value);

    /**
     * Add a callback which can be triggered when the RPC call finishes.
     * (添加方法回调，在RPC完成调用后触发)
     * <p>
     * Just as the method name implies（意味着）, this method will guarantee the callback being triggered under the same context as when the call was started,
     * see implementation in {@link Result#whenCompleteWithContext(BiConsumer)}
     *
     * @param fn
     * @return
     */
    Result whenCompleteWithContext(BiConsumer<Result, Throwable> fn); //在完成调用时，主动进行方法回调（该方法的对应实现在AsyncRpcResult）

    <U> CompletableFuture<U> thenApply(Function<Result, ? extends U> fn); //返回异步调用的结果对应的CompletableFuture

    Result get() throws InterruptedException, ExecutionException; //获取异步调用的结果（该方法的对应实现在AsyncRpcResult）

    Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException; //按指定的等待时间获取异步调用的结果（该方法的对应实现在AsyncRpcResult）
}