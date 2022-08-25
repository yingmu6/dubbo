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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;

import java.io.IOException;

@SPI
public interface Codec2 {

    /**
     * 编解码问题点
     * 1）为啥需要编解码
     * <p>
     * 2）Codec2的继承关系是怎样的？
     * <p>
     * 3）dubbo是怎么处理半包、粘包的？
     * <p>
     * 4）网络传输为啥需要序列化、反序列化？
     * 解答：
     * a）由于在系统底层，数据的传输形式是简单的字节序列形式传递，即在底层，系统不认识对象，只认识字节序列，而为了达到进程通讯的目的，需要先将数据序列化，
     * 而序列化就是将对象转化字节序列的过程。相反地，当字节序列被运到相应的进程的时候，进程为了识别这些数据，就要将其反序列化，即把字节序列转化为对象
     * <p>
     * b）无论是在进程间通信、本地数据存储又或者是网络数据传输都离不开序列化的支持。而针对不同场景选择合适的序列化方案对于应用的性能有着极大的影响
     * https://juejin.cn/post/6895434705915609101  网络传输: 序列化与反序列化
     * https://tech.meituan.com/2015/02/26/serialization-vs-deserialization.html
     * <p>
     * 5）Dubbo是如何序列化、反序列化的？
     * 解答：https://www.jianshu.com/p/608195a1767a
     * <p>
     * 在客户端发送数据时，实际是把数据写入到了TCP发送缓存里面的。关于拆包和粘包，其实是出现在TCP链接发送数据时的现象
     * 半包：如果发送的包的大小比TCP发送缓存的容量大，那么这个数据包就会被分成多个包，这时候就产生了半包现象，
     * 半包不是说只收到了全包的一半，是说收到了全包的一部分。
     * 粘包：如果发送的包的大小比TCP发送缓存容量小，并且TCP缓存可以存放多个包，这时候就出现了粘包现象。
     */

    // 功能描述：将对象序列化为字节数组，然后写到buffer中
    @Adaptive({Constants.CODEC_KEY})
    void encode(Channel channel, ChannelBuffer buffer, Object message) throws IOException; // 通常我们也习惯将编码（Encode）称为序列化（serialization），它将对象序列化为字节数组，用于网络传输、数据持久化或者其它用途。

    // 功能描述：从buffer中读取字节数组，然后反序列化为对象并返回
    @Adaptive({Constants.CODEC_KEY})
    Object decode(Channel channel, ChannelBuffer buffer) throws IOException; // 解码（Decode）/反序列化（deserialization）把从网络、磁盘等读取的字节数组还原成原始对象（通常是原始对象的拷贝），以方便后续的业务逻辑操作

    enum DecodeResult { //解码枚举
        NEED_MORE_INPUT, SKIP_SOME_INPUT
    }

    /**
     *
     * Java序列化的优缺点：
     * Java默认提供的序列化机制，需要序列化的Java对象只需要实现 java.io.Serializable接口并生成序列化ID，这个类就能够通过java.io.ObjectInput和 java.io.ObjectOutput序列化和反序列化。
     * 由于使用简单，开发门槛低，Java序列化得到了广泛的应用，但是由于它自身存在很多缺点，因此大多数的RPC框架并没有选择它。Java序列化的主要缺点如下：
     * 1）无法跨语言：是Java序列化最致命的问题。对于跨进程的服务调用，服务提供者可能会使用C++或者其它语言开发，当我们需要和异构语言进程交互 时，Java序列化就难以胜任。由于Java序列化技术是Java语言内部的私有协议，其它语言并不支持，对于用户来说它完全是黑盒。Java序列化后的 字节数组，别的语言无法进行反序列化，这就严重阻碍了它的应用范围；
     * 2）序列化后的码流太大: 例如使用二进制编解码技术对同一个复杂的POJO对象进行编码，它的码流仅仅为Java序列化之后的20%左右；目前主流的编解码框架，序列化之后的码流都远远小于原生的Java序列化；
     * 3）序列化效率差：在相同的硬件条件下、对同一个POJO对象做100W次序列化，二进制编码和Java原生序列化的性能对比测试如下图所示：Java原生序列化的耗时是二进制编码的16.2倍，效率非常差
     *
     * https://blog.51cto.com/u_15061944/2593174  Netty编解码框架分析
     */
}

