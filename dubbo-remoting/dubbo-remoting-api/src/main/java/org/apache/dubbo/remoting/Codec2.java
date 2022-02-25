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
     * todo @csy-001 编解码问题点
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

    @Adaptive({Constants.CODEC_KEY})
    void encode(Channel channel, ChannelBuffer buffer, Object message) throws IOException;

    @Adaptive({Constants.CODEC_KEY})
    Object decode(Channel channel, ChannelBuffer buffer) throws IOException; //todo @csy-02-25 哪里进入编解码逻辑的？

    // todo @csy-02-25 解码响应的结果要回执到哪里的？


    enum DecodeResult { //解码枚举
        NEED_MORE_INPUT, SKIP_SOME_INPUT
    }

}

