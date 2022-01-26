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
package org.apache.dubbo.remoting.exchange;

import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.telnet.TelnetHandler;

import java.util.concurrent.CompletableFuture;

/**
 * ExchangeHandler. (API, Prototype, ThreadSafe)
 */
public interface ExchangeHandler extends ChannelHandler, TelnetHandler {

    /**
     * ExchangeHandler实际上是为信息交换层提供了一个供上传直接调用的reply(ExchangeChannel, Object)方法，它屏蔽了本层的实现细节
     * 从ExchangeHandler来看，可以针对任意类型的request类型为Object做应答处理。
     *
     * 为了应对这种泛型化，派发器模型又再一次被搬上舞台，利用它来解决根据入参类型提供不同版本的Replier接口实现问题，框架层的抽象层次更高，
     * 类结构更清晰，也化解了上层需要大量使用IF-ELSE的粗笨编码形式
     *
     * https://zhuanlan.zhihu.com/p/100792117
     *
     *
     * 通道监听者派发器 ExchangeHandlerDispatcher
     * 同传输层一样，信息交换层同样存在一个派发器ExchangeHandlerDispatcher，它是ExchangeHandler的装饰器实现，当然也是''ChannelHandler'' 和''TelnetHandler''的装饰器实现，
     * 原因是interface ExchangeHandler extends ChannelHandler, TelnetHandler，因此它将5个基础 网络I/O事件回调委托给了更下层的ChannelHandlerDispatcher
     *
     */

    /**
     * reply.
     *
     * @param channel
     * @param request
     * @return response
     * @throws RemotingException
     */
    CompletableFuture<Object> reply(ExchangeChannel channel, Object request) throws RemotingException; //应答处理
}