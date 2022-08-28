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
package org.apache.dubbo.common.serialize;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serialization strategy interface that specifies a serializer. (SPI, Singleton, ThreadSafe)
 * <p>
 * The default extension is hessian2 and the default serialization implementation of the dubbo protocol.
 * <pre>
 *     e.g. &lt;dubbo:protocol serialization="xxx" /&gt;
 * </pre>
 */
@SPI("hessian2")
public interface Serialization { // 序列化对应的策略接口
    //都有哪些序列化方式？ 可以看具体的序列化策略，比如protobuf、fastJson、hessian2等

    /**
     * 1）先获取到序列化、发序列化对应的输出流ObjectOutput、输入流ObjectInput
     * 2）然后再执行对应的读readObject()、写操作writeObject()
     */

    /**
     * Get content type unique id, recommended that custom implementations use values different with
     * any value of {@link Constants} and don't greater than ExchangeCodec.SERIALIZATION_MASK (31)
     * because dubbo protocol use 5 bits to record serialization ID in header.
     *
     * @return content type id
     */
    byte getContentTypeId(); //获取序列化方式对应的id

    /**
     * Get content type（获取序列化对应的方式）
     *
     * @return content type
     */
    String getContentType();

    /**
     * Get a serialization implementation instance
     * （通过自适应方式获取序列化实例，从参数url获取序列化实例，默认hessian2，找到实例后再执行serialize()方法）
     *
     * @param url    URL address for the remote service
     * @param output the underlying output stream
     * @return serializer
     * @throws IOException
     */
    @Adaptive
    ObjectOutput serialize(URL url, OutputStream output) throws IOException; //获取指定序列化对应的输出流实例

    /**
     * Get a deserialization implementation instance （获取反序列化实现实例：即为反序列化对应的输入流）
     *
     * @param url   URL address for the remote service
     * @param input the underlying input stream （底层输入流）
     * @return deserializer
     * @throws IOException
     */
    @Adaptive
    ObjectInput deserialize(URL url, InputStream input) throws IOException; //获取指定序列化对应的输入流实例

}
