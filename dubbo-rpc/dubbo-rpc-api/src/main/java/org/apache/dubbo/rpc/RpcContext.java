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
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.InternalThreadLocal;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.rpc.Constants.ASYNC_KEY;
import static org.apache.dubbo.rpc.Constants.RETURN_KEY;


/**
 * Thread local context. (API, ThreadLocal, ThreadSafe)
 * <p>
 * Note: RpcContext is a temporary state holder（临时状态记录器）. States in RpcContext changes every time when request is sent or received.（每次发送或接收请求时，RpcContext中的状态都会发生变化。）
 * For example: A invokes B, then B invokes C. On service B, RpcContext saves invocation info from A to B before B
 * starts invoking C, and saves invocation info from B to C after B invokes C.
 *
 * @export
 * @see org.apache.dubbo.rpc.filter.ContextFilter
 */
public class RpcContext { //上下文信息

    /**
     * use internal thread local to improve performance （使用内部的线程提高性能）
     */
    // FIXME REQUEST_CONTEXT
    private static final InternalThreadLocal<RpcContext> LOCAL = new InternalThreadLocal<RpcContext>() { //客户端的上下文缓存（static变量：类加载时就会执行，静态变量为共享变量，类的所有对象都拥有）
        @Override
        protected RpcContext initialValue() { //重写了初始化方法
            return new RpcContext();
        }
    };

    // FIXME RESPONSE_CONTEXT
    private static final InternalThreadLocal<RpcContext> SERVER_LOCAL = new InternalThreadLocal<RpcContext>() { //服务端的上下文缓存
        @Override
        protected RpcContext initialValue() {
            return new RpcContext();
        }
    };

    protected final Map<String, Object> attachments = new HashMap<>(); //存储附加参数
    private final Map<String, Object> values = new HashMap<String, Object>();

    private List<URL> urls;

    private URL url; //调用的url信息

    private String methodName; // 来自于setInvocation(Invocation invocation) 设置

    private Class<?>[] parameterTypes; //同上

    private Object[] arguments; //同上

    private InetSocketAddress localAddress; //本地地址

    private InetSocketAddress remoteAddress; //远程地址

    private String remoteApplicationName; //远程的应用名

    @Deprecated
    private List<Invoker<?>> invokers;
    @Deprecated
    private Invoker<?> invoker;
    @Deprecated
    private Invocation invocation; //调用信息

    // now we don't use the 'values' map to hold these objects
    // we want these objects to be as generic as possible （尽可能通用）
    private Object request; //请求信息
    private Object response; //响应信息
    private AsyncContext asyncContext; //异步上下文信息

    private boolean remove = true; //是否移除的标识（是否在每次调用后移除上下文）


    protected RpcContext() { //未提供公有的构造函数，可使用static方法getServerContext()获取
    }

    /**
     * get server side context.
     *
     * @return server context
     */
    public static RpcContext getServerContext() { //获取服务端的上下文
        return SERVER_LOCAL.get();
    }

    public static void restoreServerContext(RpcContext oldServerContext) {
        SERVER_LOCAL.set(oldServerContext);
    }

    /**
     * remove server side context.
     *
     * @see org.apache.dubbo.rpc.filter.ContextFilter
     */
    public static void removeServerContext() {
        SERVER_LOCAL.remove();
    }

    /**
     * get context.
     *
     * @return context
     */
    public static RpcContext getContext() { //获取上下文实例（使用InternalThreadLocal存储上下文RpcContext的实例）
        return LOCAL.get();
    }

    public boolean canRemove() {
        return remove;
    }

    public void clearAfterEachInvoke(boolean remove) { //是否在每次调用后移除上下文
        this.remove = remove;
    }

    public static void restoreContext(RpcContext oldContext) {
        LOCAL.set(oldContext);
    }

    /**
     * remove context.
     *
     * @see org.apache.dubbo.rpc.filter.ContextFilter
     */
    public static void removeContext() {
        removeContext(false);
    }

    /**
     * customized for internal use.
     *
     * @param checkCanRemove if need check before remove
     */
    public static void removeContext(boolean checkCanRemove) { //没有根据传入的值来判断是否可移除
        if (LOCAL.get().canRemove()) { //若能移除，则对应移除上下文
            LOCAL.remove();
        }
    }

    /**
     * Get the request object of the underlying RPC protocol, e.g. HttpServletRequest
     *
     * @return null if the underlying protocol doesn't provide support for getting request
     */
    public Object getRequest() {
        return request;
    }

    public void setRequest(Object request) { //设置请求对象
        this.request = request;
    }

    /**
     * Get the request object of the underlying（底层的） RPC protocol, e.g. HttpServletRequest
     * （获取底层的RPC协议请求对象）
     *
     * @return null if the underlying protocol doesn't provide support for getting request or the request is not of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> T getRequest(Class<T> clazz) {
        return (request != null && clazz.isAssignableFrom(request.getClass())) ? (T) request : null;
    }

    /**
     * Get the response object of the underlying RPC protocol, e.g. HttpServletResponse
     *
     * @return null if the underlying protocol doesn't provide support for getting response
     */
    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    /**
     * Get the response object of the underlying RPC protocol, e.g. HttpServletResponse
     *
     * @return null if the underlying protocol doesn't provide support for getting response or the response is not of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> T getResponse(Class<T> clazz) {
        return (response != null && clazz.isAssignableFrom(response.getClass())) ? (T) response : null;
    }

    /**
     * is provider side.
     *
     * @return provider side.
     */
    public boolean isProviderSide() {
        return !isConsumerSide();
    }

    /**
     * is consumer side.
     *
     * @return consumer side.
     */
    public boolean isConsumerSide() { //判断是否是消费端
        return getUrl().getParameter(SIDE_KEY, PROVIDER_SIDE).equals(CONSUMER_SIDE); //从url中获取参数side的值，默认值为provider，即默认为提供者端
    }

    /**
     * get CompletableFuture.
     *
     * @param <T>
     * @return future
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getCompletableFuture() {
        return FutureContext.getContext().getCompletableFuture();
    }

    /**
     * get future.
     *
     * @param <T>
     * @return future
     */
    @SuppressWarnings("unchecked")
    public <T> Future<T> getFuture() {
        return FutureContext.getContext().getCompletableFuture();
    }

    /**
     * set future.
     *
     * @param future
     */
    public void setFuture(CompletableFuture<?> future) {
        FutureContext.getContext().setFuture(future);
    }

    public List<URL> getUrls() {
        return urls == null && url != null ? (List<URL>) Arrays.asList(url) : urls;
    }

    public void setUrls(List<URL> urls) {
        this.urls = urls;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    /**
     * get method name.
     *
     * @return method name.
     */
    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * get parameter types.
     *
     * @serial
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    /**
     * get arguments.
     *
     * @return arguments.
     */
    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    /**
     * set local address.
     *
     * @param host
     * @param port
     * @return context
     */
    public RpcContext setLocalAddress(String host, int port) {
        if (port < 0) {
            port = 0;
        }
        this.localAddress = InetSocketAddress.createUnresolved(host, port); //创建一个未解析的Socket地址
        return this;
    }

    /**
     * get local address.
     *
     * @return local address
     */
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * set local address.
     *
     * @param address
     * @return context
     */
    public RpcContext setLocalAddress(InetSocketAddress address) {
        this.localAddress = address;
        return this;
    }

    public String getLocalAddressString() { //获取本地地址（"hostname:port"拼接的字符串）
        return getLocalHost() + ":" + getLocalPort();
    }

    /**
     * get local host name.
     *
     * @return local host name
     */
    public String getLocalHostName() {
        String host = localAddress == null ? null : localAddress.getHostName();
        if (StringUtils.isEmpty(host)) {
            return getLocalHost();
        }
        return host;
    }

    /**
     * set remote address.
     *
     * @param host
     * @param port
     * @return context
     */
    public RpcContext setRemoteAddress(String host, int port) {
        if (port < 0) { //端口号小于0，会被置为0
            port = 0;
        }
        this.remoteAddress = InetSocketAddress.createUnresolved(host, port);
        return this;
    }

    /**
     * get remote address.
     *
     * @return remote address
     */
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * set remote address.
     *
     * @param address
     * @return context
     */
    public RpcContext setRemoteAddress(InetSocketAddress address) {
        this.remoteAddress = address;
        return this;
    }

    public String getRemoteApplicationName() {
        return remoteApplicationName;
    }

    public RpcContext setRemoteApplicationName(String remoteApplicationName) {
        this.remoteApplicationName = remoteApplicationName;
        return this;
    }

    /**
     * get remote address string.
     *
     * @return remote address string.
     */
    public String getRemoteAddressString() {
        return getRemoteHost() + ":" + getRemotePort();
    }

    /**
     * get remote host name.
     *
     * @return remote host name
     */
    public String getRemoteHostName() {
        return remoteAddress == null ? null : remoteAddress.getHostName();
    }

    /**
     * get local host.
     *
     * @return local host
     */
    public String getLocalHost() {
        String host = localAddress == null ? null :
                localAddress.getAddress() == null ? localAddress.getHostName()
                        : NetUtils.filterLocalHost(localAddress.getAddress().getHostAddress());
        if (host == null || host.length() == 0) {
            return NetUtils.getLocalHost();
        }
        return host;
    }

    /**
     * get local port.
     *
     * @return port
     */
    public int getLocalPort() {
        return localAddress == null ? 0 : localAddress.getPort();
    }

    /**
     * get remote host.
     *
     * @return remote host
     */
    public String getRemoteHost() {
        return remoteAddress == null ? null :
                remoteAddress.getAddress() == null ? remoteAddress.getHostName()
                        : NetUtils.filterLocalHost(remoteAddress.getAddress().getHostAddress());
    }

    /**
     * get remote port.
     *
     * @return remote port
     */
    public int getRemotePort() {
        return remoteAddress == null ? 0 : remoteAddress.getPort();
    }

    /**
     * also see {@link #getObjectAttachment(String)}.
     *
     * @param key
     * @return attachment
     */
    public String getAttachment(String key) { //获取参数key对应的字符串值
        Object value = attachments.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null; // or JSON.toString(value);
    }

    /**
     * get attachment.
     *
     * @param key
     * @return attachment
     */
    @Experimental("Experiment api for supporting Object transmission")
    public Object getObjectAttachment(String key) {
        return attachments.get(key);
    }

    /**
     * set attachment.
     *
     * @param key
     * @param value
     * @return context
     */
    public RpcContext setAttachment(String key, String value) {
        return setObjectAttachment(key, (Object) value);
    }

    public RpcContext setAttachment(String key, Object value) {
        return setObjectAttachment(key, value);
    }

    @Experimental("Experiment api for supporting Object transmission")
    public RpcContext setObjectAttachment(String key, Object value) {
        if (value == null) {
            attachments.remove(key);
        } else {
            attachments.put(key, value);
        }
        return this;
    }

    /**
     * remove attachment.
     *
     * @param key
     * @return context
     */
    public RpcContext removeAttachment(String key) {
        attachments.remove(key);
        return this;
    }

    /**
     * get attachments.
     *
     * @return attachments
     */
    @Deprecated
    public Map<String, String> getAttachments() {
        return new AttachmentsAdapter.ObjectToStringMap(this.getObjectAttachments());
    }

    /**
     * get attachments.
     *
     * @return attachments
     */
    @Experimental("Experiment api for supporting Object transmission")
    public Map<String, Object> getObjectAttachments() {
        return attachments;
    }

    /**
     * set attachments
     *
     * @param attachment
     * @return context
     */
    public RpcContext setAttachments(Map<String, String> attachment) {
        this.attachments.clear();
        if (attachment != null && attachment.size() > 0) {
            this.attachments.putAll(attachment);
        }
        return this;
    }

    /**
     * set attachments
     *
     * @param attachment
     * @return context
     */
    @Experimental("Experiment api for supporting Object transmission")
    public RpcContext setObjectAttachments(Map<String, Object> attachment) { //设置附加参数
        this.attachments.clear(); // 1）先清除参数Map的值
        if (attachment != null && attachment.size() > 0) {
            this.attachments.putAll(attachment); // 2）再将值设置到参数Map中
        }
        return this;
    }

    public void clearAttachments() {
        this.attachments.clear();
    }

    /**
     * get values.
     *
     * @return values
     */
    public Map<String, Object> get() {
        return values;
    }

    /**
     * set value.
     *
     * @param key
     * @param value
     * @return context
     */
    public RpcContext set(String key, Object value) { //设置值
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
        return this;
    }

    /**
     * remove value.
     *
     * @param key
     * @return value
     */
    public RpcContext remove(String key) {
        values.remove(key);
        return this;
    }

    /**
     * get value.
     *
     * @param key
     * @return value
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * @deprecated Replace to isProviderSide()
     */
    @Deprecated
    public boolean isServerSide() {
        return isProviderSide();
    }

    /**
     * @deprecated Replace to isConsumerSide()
     */
    @Deprecated
    public boolean isClientSide() {
        return isConsumerSide();
    }

    /**
     * @deprecated Replace to getUrls()
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Invoker<?>> getInvokers() {
        return invokers == null && invoker != null ? (List) Arrays.asList(invoker) : invokers;
    }

    public RpcContext setInvokers(List<Invoker<?>> invokers) {
        this.invokers = invokers;
        if (CollectionUtils.isNotEmpty(invokers)) {
            List<URL> urls = new ArrayList<URL>(invokers.size());
            for (Invoker<?> invoker : invokers) {
                urls.add(invoker.getUrl());
            }
            setUrls(urls);
        }
        return this;
    }

    /**
     * @deprecated Replace to getUrl()
     */
    @Deprecated
    public Invoker<?> getInvoker() {
        return invoker;
    }

    public RpcContext setInvoker(Invoker<?> invoker) {
        this.invoker = invoker;
        if (invoker != null) {
            setUrl(invoker.getUrl());
        }
        return this;
    }

    /**
     * @deprecated Replace to getMethodName(), getParameterTypes(), getArguments()
     */
    @Deprecated
    public Invocation getInvocation() {
        return invocation;
    }

    public RpcContext setInvocation(Invocation invocation) { //设置调用信息
        this.invocation = invocation;
        if (invocation != null) {
            setMethodName(invocation.getMethodName()); //方法名
            setParameterTypes(invocation.getParameterTypes()); //参数类型列表
            setArguments(invocation.getArguments()); //参数值列表
        }
        return this;
    }

    /**
     * Async invocation. Timeout will be handled even if <code>Future.get()</code> is not called.
     *
     * @param callable
     * @return get the return result from <code>future.get()</code>
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> asyncCall(Callable<T> callable) { //异步调用
        try {
            try {
                setAttachment(ASYNC_KEY, Boolean.TRUE.toString()); //在执行前，设置参数async
                final T o = callable.call(); //异步执行线程调用（执行线程体的内容，如call()方法 ）
                //local invoke will return directly
                if (o != null) {
                    if (o instanceof CompletableFuture) {
                        return (CompletableFuture<T>) o;
                    }
                    return CompletableFuture.completedFuture(o); //构建CompletableFuture对象
                } else {
                    // The service has a normal sync method signature, should get future from RpcContext.
                }
            } catch (Exception e) {
                throw new RpcException(e);
            } finally {
                removeAttachment(ASYNC_KEY); //在执行后，移除参数async
            }
        } catch (final RpcException e) { //有异常时，返回带有异常的CompletableFuture
            CompletableFuture<T> exceptionFuture = new CompletableFuture<>();
            exceptionFuture.completeExceptionally(e);
            return exceptionFuture;
        }
        return ((CompletableFuture<T>) getContext().getFuture());
    }

    /**
     * one way async call, send request only, and result is not required
     *
     * @param runnable
     */
    public void asyncCall(Runnable runnable) {
        try {
            setAttachment(RETURN_KEY, Boolean.FALSE.toString());
            runnable.run();
        } catch (Throwable e) {
            // FIXME should put exception in future?
            throw new RpcException("oneway call error ." + e.getMessage(), e);
        } finally {
            removeAttachment(RETURN_KEY);
        }
    }

    /**
     * @return
     * @throws IllegalStateException
     */
    @SuppressWarnings("unchecked")
    public static AsyncContext startAsync() throws IllegalStateException { //启动异步上下文
        RpcContext currentContext = getContext();
        if (currentContext.asyncContext == null) {
            currentContext.asyncContext = new AsyncContextImpl();
        }
        currentContext.asyncContext.start();
        return currentContext.asyncContext;
    }

    protected void setAsyncContext(AsyncContext asyncContext) {
        this.asyncContext = asyncContext;
    }

    public boolean isAsyncStarted() {
        if (this.asyncContext == null) {
            return false;
        }
        return asyncContext.isAsyncStarted();
    }

    public boolean stopAsync() {
        return asyncContext.stop();
    }

    public AsyncContext getAsyncContext() {
        return asyncContext;
    }

}