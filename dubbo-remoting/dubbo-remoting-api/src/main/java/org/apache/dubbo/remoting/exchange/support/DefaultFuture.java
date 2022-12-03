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
package org.apache.dubbo.remoting.exchange.support;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.ThreadlessExecutor;
import org.apache.dubbo.common.timer.HashedWheelTimer;
import org.apache.dubbo.common.timer.Timeout;
import org.apache.dubbo.common.timer.Timer;
import org.apache.dubbo.common.timer.TimerTask;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.TimeoutException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * DefaultFuture.
 */
public class DefaultFuture extends CompletableFuture<Object> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFuture.class);

    private static final Map<Long, Channel> CHANNELS = new ConcurrentHashMap<>(); //请求id与请求通道Channel实例的缓存映射

    private static final Map<Long, DefaultFuture> FUTURES = new ConcurrentHashMap<>(); //请求id与DefaultFuture实例的缓存映射（用来将请求与响应关联起来）

    public static final Timer TIME_OUT_TIMER = new HashedWheelTimer(      //设置定时器（使用HashedWheelTimer管理定时器）
            new NamedThreadFactory("dubbo-future-timeout", true),
            30,
            TimeUnit.MILLISECONDS); //可以指定Future的超时时间

    // invoke id.
    private final Long id;
    private final Channel channel; //通道对象
    private final Request request; //请求对象
    private final int timeout;
    private final long start = System.currentTimeMillis();
    private volatile long sent; //发送时对应的时间戳
    private Timeout timeoutCheckTask; //超时检测任务

    private ExecutorService executor;

    public ExecutorService getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    private DefaultFuture(Channel channel, Request request, int timeout) { //构造函数是私有的，外部不能直接调用创建对象，内部可以调用private方法
        this.channel = channel;
        this.request = request;
        this.id = request.getId();
        this.timeout = timeout > 0 ? timeout : channel.getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT); //若指定了超时时间，则使用指定的值，否则取通道url中设置的值
        // put into waiting map.
        FUTURES.put(id, this); //每创建一个新的DefaultFuture对象，就将请求id与DefaultFuture缓存起来
        CHANNELS.put(id, channel); //将请求id与指定的Channel信息进行缓存
    }

    /**
     * check time out of the future
     */
    private static void timeoutCheck(DefaultFuture future) {
        TimeoutCheckTask task = new TimeoutCheckTask(future.getId()); //创建超时检测任务
        future.timeoutCheckTask = TIME_OUT_TIMER.newTimeout(task, future.getTimeout(), TimeUnit.MILLISECONDS);
    }

    /**
     * init a DefaultFuture
     * 1.init a DefaultFuture
     * 2.timeout check
     *
     * @param channel channel
     * @param request the request
     * @param timeout timeout
     * @return a new DefaultFuture
     */
    public static DefaultFuture newFuture(Channel channel, Request request, int timeout, ExecutorService executor) { //使用静态方法创建DefaultFuture（一个类，就一个对象）
        final DefaultFuture future = new DefaultFuture(channel, request, timeout);
        future.setExecutor(executor);
        // threadlessexecutor needs to hold the waiting future in case of circuit return. （circuit：循环地）
        if (executor instanceof ThreadlessExecutor) {
            ((ThreadlessExecutor) executor).setWaitingFuture(future); //设置等待的DefaultFuture，避免循环地返回
        }
        // timeout check
        timeoutCheck(future); //启动定时检查任务
        return future;
    }

    public static DefaultFuture getFuture(long id) { //从缓存中获取请求id对应的DefaultFuture
        return FUTURES.get(id);
    }

    public static boolean hasFuture(Channel channel) {
        return CHANNELS.containsValue(channel);
    }

    public static void sent(Channel channel, Request request) { //发送请求信息
        DefaultFuture future = FUTURES.get(request.getId());
        if (future != null) {
            future.doSent();
        }
    }

    /**
     * close a channel when a channel is inactive
     * directly return the unfinished requests.
     *
     * @param channel channel to close
     */
    public static void closeChannel(Channel channel) { //关闭通道
        for (Map.Entry<Long, Channel> entry : CHANNELS.entrySet()) {
            if (channel.equals(entry.getValue())) {
                DefaultFuture future = getFuture(entry.getKey());
                if (future != null && !future.isDone()) {
                    ExecutorService futureExecutor = future.getExecutor();
                    if (futureExecutor != null && !futureExecutor.isTerminated()) {
                        futureExecutor.shutdownNow();
                    }

                    Response disconnectResponse = new Response(future.getId());
                    disconnectResponse.setStatus(Response.CHANNEL_INACTIVE);
                    disconnectResponse.setErrorMessage("Channel " +
                            channel +
                            " is inactive. Directly return the unFinished request : " +
                            future.getRequest());
                    DefaultFuture.received(channel, disconnectResponse);
                }
            }
        }
    }

    public static void received(Channel channel, Response response) {
        received(channel, response, false);
    }

    public static void received(Channel channel, Response response, boolean timeout) {
        try {
            DefaultFuture future = FUTURES.remove(response.getId()); //Future处理后要对应移除掉（Map的remove方法，能移除元素，并且返回之前值）
            if (future != null) {
                Timeout t = future.timeoutCheckTask;
                if (!timeout) { //若任务没有超时，则尝试取消任务
                    // decrease Time
                    t.cancel();
                }
                future.doReceived(response);
            } else {
                logger.warn("The timeout response finally returned at "
                        + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                        + ", response status is " + response.getStatus()
                        + (channel == null ? "" : ", channel: " + channel.getLocalAddress()
                        + " -> " + channel.getRemoteAddress()) + ", please check provider side for detailed result.");
            }
        } finally {
            CHANNELS.remove(response.getId()); //处理后，移除对应请求id对应的通道Channel
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) { //重写了CompletableFuture的cancel()方法
        Response errorResult = new Response(id);
        errorResult.setStatus(Response.CLIENT_ERROR);
        errorResult.setErrorMessage("request future has been canceled.");
        this.doReceived(errorResult);
        FUTURES.remove(id); //取消任务时，移除对应的缓存
        CHANNELS.remove(id);
        return true; //处理过程中不抛出异常，能正常使用，即可正常取消
    }

    public void cancel() {
        this.cancel(true);
    }

    private void doReceived(Response res) { //接收响应的信息，并做处理
        if (res == null) {
            throw new IllegalStateException("response cannot be null");
        }
        if (res.getStatus() == Response.OK) { //正常的响应
            this.complete(res.getResult());
        } else if (res.getStatus() == Response.CLIENT_TIMEOUT || res.getStatus() == Response.SERVER_TIMEOUT) { //带有超时异常的响应
            this.completeExceptionally(new TimeoutException(res.getStatus() == Response.SERVER_TIMEOUT, channel, res.getErrorMessage()));
        } else {
            this.completeExceptionally(new RemotingException(channel, res.getErrorMessage())); //其它情况的响应，统一为：RemotingException
        }

        // the result is returning, but the caller thread may still waiting
        // to avoid endless waiting for whatever reason, notify caller thread to return.
        if (executor != null && executor instanceof ThreadlessExecutor) {
            ThreadlessExecutor threadlessExecutor = (ThreadlessExecutor) executor;
            if (threadlessExecutor.isWaiting()) {
                threadlessExecutor.notifyReturn(new IllegalStateException("The result has returned, but the biz thread is still waiting" +
                        " which is not an expected state, interrupt the thread manually by returning an exception."));  //返回异常信息，提示为：非期待的状态，返回异常终止线程
            }
        }
    }

    private long getId() {
        return id;
    }

    private Channel getChannel() {
        return channel;
    }

    private boolean isSent() {
        return sent > 0;
    }

    public Request getRequest() {
        return request;
    }

    private int getTimeout() {
        return timeout;
    }

    private void doSent() {
        sent = System.currentTimeMillis();
    }

    private String getTimeoutMessage(boolean scan) { //超时提示信息（包含发送请求超时或等待服务响应超时）
        long nowTimestamp = System.currentTimeMillis();
        return (sent > 0 ? "Waiting server-side response timeout" : "Sending request timeout in client-side")  //以sent是否有值来判断是响应超时还是请求超时
                + (scan ? " by scan timer" : "") + ". start time: "
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(start))) + ", end time: "
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(nowTimestamp))) + ","
                + (sent > 0 ? " client elapsed: " + (sent - start)
                + " ms, server elapsed: " + (nowTimestamp - sent)
                : " elapsed: " + (nowTimestamp - start)) + " ms, timeout: "
                + timeout + " ms, request: " + (logger.isDebugEnabled() ? request : getRequestWithoutData()) + ", channel: " + channel.getLocalAddress()
                + " -> " + channel.getRemoteAddress();
    }

    private Request getRequestWithoutData() {
        Request newRequest = request;
        newRequest.setData(null);
        return newRequest;
    }

    private static class TimeoutCheckTask implements TimerTask { //超时检查任务

        private final Long requestID;

        TimeoutCheckTask(Long requestID) {
            this.requestID = requestID;
        }

        @Override
        public void run(Timeout timeout) { //任务超时的时候，该方法会被调用
            DefaultFuture future = DefaultFuture.getFuture(requestID); //从缓存中获取请求id对应的DefaultFuture
            if (future == null || future.isDone()) {
                return;
            }

            if (future.getExecutor() != null) { //若线程池不为空，则使用线程池异步进行超时的通知
                future.getExecutor().execute(() -> notifyTimeout(future));
            } else {
                notifyTimeout(future); //未设置线程池，同步进行超时的通知
            }
        }

        private void notifyTimeout(DefaultFuture future) { //超时对应的通知
            // create exception response.
            Response timeoutResponse = new Response(future.getId()); //请求和响应维护着同一个id值
            // set timeout status.
            timeoutResponse.setStatus(future.isSent() ? Response.SERVER_TIMEOUT : Response.CLIENT_TIMEOUT); //判断是服务端超时，还是客户端超时
            timeoutResponse.setErrorMessage(future.getTimeoutMessage(true));
            // handle response.（处理响应）
            DefaultFuture.received(future.getChannel(), timeoutResponse, true);
        }
    }
}
