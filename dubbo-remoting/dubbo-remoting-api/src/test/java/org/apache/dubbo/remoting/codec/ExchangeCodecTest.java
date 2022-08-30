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
package org.apache.dubbo.remoting.codec;


import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.UnsafeByteArrayOutputStream;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.buffer.ChannelBuffers;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.codec.ExchangeCodec;
import org.apache.dubbo.remoting.telnet.codec.TelnetCodec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.READONLY_EVENT;
import static org.junit.jupiter.api.Assertions.fail;

/**
 *
 *         byte 16
 *         0-1 magic code
 *         2 flag
 *         8 - 1-request/0-response
 *         7 - two way
 *         6 - heartbeat
 *         1-5 serialization id
 *         3 status
 *         20 ok
 *         90 error?
 *         4-11 id (long)
 *         12 -15 datalength
 */
public class ExchangeCodecTest extends TelnetCodecTest { // Codec编码测试（测试类也继承）
    // magic header.
    private static final short MAGIC = (short) 0xdabb;
    private static final byte MAGIC_HIGH = (byte) Bytes.short2bytes(MAGIC)[0];
    private static final byte MAGIC_LOW = (byte) Bytes.short2bytes(MAGIC)[1];
    Serialization serialization = getSerialization(Constants.DEFAULT_REMOTING_SERIALIZATION);

    private static Serialization getSerialization(String name) {
        Serialization serialization = ExtensionLoader.getExtensionLoader(Serialization.class).getExtension(name);
        return serialization;
    }

    private Object decode(byte[] request) throws IOException {
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(request); //将byte数组封装为ChannelBuffer实例
        AbstractMockChannel channel = getServerSideChannel(url);
        //decode
        Object obj = codec.decode(channel, buffer);
        return obj;
    }

    private byte[] getRequestBytes(Object obj, byte[] header) throws IOException { //构造请求头，请求体用输出流进行序列化
        // encode request data.
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(1024);
        ObjectOutput out = serialization.serialize(url, bos);
        out.writeObject(obj); //将对象写到输出流

        out.flushBuffer();
        bos.flush();
        bos.close();
        byte[] data = bos.toByteArray();
        byte[] len = Bytes.int2bytes(data.length);
        System.arraycopy(len, 0, header, 12, 4); //设置请求体长度
        byte[] request = join(header, data); //将请求头+请求体对应的字节数组拼接
        return request;
    }

    private byte[] assemblyDataProtocol(byte[] header) { //assembly：装配
        Person request = new Person();
        byte[] newbuf = join(header, objectToByte(request)); //拼接请求头 + 请求体的字节数组
        return newbuf;
    }
    //===================================================================================

    @BeforeEach
    public void setUp() throws Exception {
        codec = new ExchangeCodec();
    }

    @Test
    public void test_Decode_Error_MagicNum() throws IOException { //测试请求报文中没有包含完整的魔法数
        /**
         * 功能描述：当请求报文中没有完整的魔法数，即连着的Oxdabb，则交由TelnetCodec来解码。按命令解码时，如果没有包含指令的指令，会返回DecodeResult.NEED_MORE_INPUT信息
         */
        HashMap<byte[], Object> inputBytes = new HashMap<byte[], Object>();
        inputBytes.put(new byte[] {0}, TelnetCodec.DecodeResult.NEED_MORE_INPUT); //请求头中没有魔法数
        inputBytes.put(new byte[] {MAGIC_HIGH, 0}, TelnetCodec.DecodeResult.NEED_MORE_INPUT); //请求头中只有魔法数高位， DecodeResult是Codec2的内部枚举，TelnetCodec继承了Codec2，所以可以引用
        inputBytes.put(new byte[] {0, MAGIC_LOW}, TelnetCodec.DecodeResult.NEED_MORE_INPUT); //只有魔法数低位

        for (Map.Entry<byte[], Object> entry : inputBytes.entrySet()) {
            testDecode_assertEquals(assemblyDataProtocol(entry.getKey()), entry.getValue());
        }
    }

    @Test
    public void test_Decode_Error_Length() throws IOException { //测试在请求报文中附加额外的数据
        /**
         * 功能描述：请求报文若附加了额外的数据，不会被解析，解析时会严格按照请求头指定的body长度来解析
         */
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}; //请求头
        Person person = new Person(); //请求体
        byte[] request = getRequestBytes(person, header); //将请求体序列化后与请求头字节数组拼接（模拟编码过程）

        Channel channel = getServerSideChannel(url);
        byte[] baddata = new byte[] {1, 2}; //错误的数据，超出了编码请求头中的 指定长度，解码时就会丢弃
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(join(request, baddata)); //想把baddata拼接到报文对应的字节数组的尾部
        Response obj = (Response) codec.decode(channel, buffer);
        Assertions.assertEquals(person, obj.getResult());
        //only decode necessary bytes
        Assertions.assertEquals(request.length, buffer.readerIndex());
    }

    @Test
    public void test_Decode_Error_Response_Object() throws IOException { //解析将请求体中的内容变更
        /**
         * 功能描述：覆盖了请求体的内容，就无法反序列化出对象，会解析异常，最后会返回客户端异常90的状态值
         */

        //00000010-response/oneway/hearbeat=true |20-stats=ok|id=0|length=0  (对请求头进行解读，按占据多少bit来看待请求头，8bit=1byte)
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);
        //bad object
        byte[] badbytes = new byte[] {-1, -2, -3, -4, -3, -4, -3, -4, -3, -4, -3, -4};
        System.arraycopy(badbytes, 0, request, 21, badbytes.length); //想把报文对应的字节数组的中间的字节替换掉

        Response obj = (Response) decode(request);
        Assertions.assertEquals(90, obj.getStatus()); //90 - CLIENT_ERROR（因为更改了内容，无法反序列化为原来的对象，就会报反序列化异常，捕获后就以90 客户端异常抛出来）
    }

    @Test
    public void testInvalidSerializaitonId() throws Exception { //测试无效的序列化id
        // 0x8F对应的十进制143，二进制为10001111，后5个bit是序列化id，值为15（该值是没有的序列化id，参见org.apache.dubbo.common.serialize.Constants）
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0x8F, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Object obj = decode(header);
        Assertions.assertTrue(obj instanceof Request); //解析出错时，还是返回Request/Response对象，设置标志或状态
        Request request = (Request) obj;
        Assertions.assertTrue(request.isBroken());
        Assertions.assertTrue(request.getData() instanceof IOException); // 由于序列化id不存在，所以没有找到序列化实例，就抛出异常了

        // 0x1F对应的十进制为31，二进制为00011111，后5个bit是序列化id，值为31（该序列化id也是不存在的，因为第一位是0，会构建Response对象）
        header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0x1F, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        obj = decode(header);
        Assertions.assertTrue(obj instanceof Response);
        Response response = (Response) obj;
        Assertions.assertEquals(response.getStatus(), Response.CLIENT_ERROR);
        Assertions.assertTrue(response.getErrorMessage().contains("IOException"));
    }

    @Test
    public void test_Decode_Check_Payload() throws IOException { //测试 解码时检查负载大小的功能
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        byte[] request = assemblyDataProtocol(header);
        try {
            testDecode_assertEquals(request, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
            fail(); // 主动抛出异常
        } catch (IOException expected) {
            Assertions.assertTrue(expected.getMessage().startsWith("Data length too large: " + Bytes.bytes2int(new byte[] {1, 1, 1, 1})));
        }
    }

    @Test
    public void test_Decode_Header_Need_Readmore() throws IOException { //测试请求头长度不足16字节场景
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        testDecode_assertEquals(header, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
    }

    @Test
    public void test_Decode_Body_Need_Readmore() throws IOException { //测试请求体body的实际可读数不足的场景
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 'a', 'a'};
        testDecode_assertEquals(header, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
    }

    @Test
    public void test_Decode_MigicCodec_Contain_ExchangeHeader() throws IOException { //测试魔法数在中间的情况（按TelnetCodec解析）
        byte[] header = new byte[] {0, 0, MAGIC_HIGH, MAGIC_LOW, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        Channel channel = getServerSideChannel(url);
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(header);
        Object obj = codec.decode(channel, buffer);
        Assertions.assertEquals(TelnetCodec.DecodeResult.NEED_MORE_INPUT, obj);
        //If the telnet data and request data are in the same data packet, we should guarantee that the receipt of request data won't be affected by the factor that telnet does not have an end characters.
        // (如果 telnet 数据和请求数据在同一个数据包中，我们应该保证请求数据的接收不会受到 telnet 没有结束字符的因素的影响。)
        Assertions.assertEquals(2, buffer.readerIndex());
    }

    @Test
    public void test_Decode_Return_Response_Person() throws IOException { //测试正常的解码（返回正常状态以及指定对象）
        //00000010-response/oneway/hearbeat=false/hessian |20-stats=ok|id=0|length=0 （将请求头先解析出来，明确具体含义）
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Response obj = (Response) decode(request);
        Assertions.assertEquals(20, obj.getStatus());
        Assertions.assertEquals(person, obj.getResult());
        System.out.println(obj);
    }

    @Test //The status input has a problem, and the read information is wrong when the serialization is serialized.
    public void test_Decode_Return_Response_Error() throws IOException { //测试请求头的status为非正常时，解码返回错误描述信息
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 2, 90, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        String errorString = "encode request data error ";
        byte[] request = getRequestBytes(errorString, header);
        Response obj = (Response) decode(request);
        Assertions.assertEquals(90, obj.getStatus()); //当请求头的status为非正常状态时，输入的信息会作为错误描述信息直接返回
        Assertions.assertEquals(errorString, obj.getErrorMessage());
    }

    @Test
    public void test_Decode_Return_Request_Event_Object() throws IOException { //测试正常的请求Request返回
        //|11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Request obj = (Request) decode(request);
        Assertions.assertEquals(person, obj.getData());
        Assertions.assertTrue(obj.isTwoWay());
        Assertions.assertTrue(obj.isEvent());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj);
    }

    @Test
    public void test_Decode_Return_Request_Event_String() throws IOException { // 测试请求体内容为字符串场景
        //|11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        String event = READONLY_EVENT;
        byte[] request = getRequestBytes(event, header); //body内容是字符串

        Request obj = (Request) decode(request);
        Assertions.assertEquals(event, obj.getData());
        Assertions.assertTrue(obj.isTwoWay());
        Assertions.assertTrue(obj.isEvent());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj); //返回的Request中data为null
    }

    @Test
    public void test_Decode_Return_Request_Heartbeat_Object() throws IOException { //测试请求体内容为null场景
        //|11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] request = getRequestBytes(null, header); //请求body为null
        Request obj = (Request) decode(request);
        Assertions.assertNull(obj.getData());
        Assertions.assertTrue(obj.isTwoWay());
        Assertions.assertTrue(obj.isHeartbeat());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj); //返回的Request中的data为null
    }

    @Test
    public void test_Decode_Return_Request_Object() throws IOException { //测试正常的请求返回
        //11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Request obj = (Request) decode(request);
        Assertions.assertEquals(person, obj.getData());
        Assertions.assertTrue(obj.isTwoWay());
        Assertions.assertFalse(obj.isHeartbeat());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj);
    }

    @Test
    public void test_Decode_Error_Request_Object() throws IOException { //测试请求体内容被改变，反序列化异常的场景
        //11100010-response/oneway/hearbeat=true |20-stats=ok|id=0|length=0
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);
        //bad object
        byte[] badbytes = new byte[] {-1, -2, -3, -4, -3, -4, -3, -4, -3, -4, -3, -4};
        System.arraycopy(badbytes, 0, request, 21, badbytes.length); //请求body的内容被篡改了，反序列化时解析不出来，抛出异常，dubbo进行捕获，并标记broken值

        Request obj = (Request) decode(request);
        Assertions.assertTrue(obj.isBroken());
        Assertions.assertTrue(obj.getData() instanceof Throwable);
    }

    @Test
    public void test_Header_Response_NoSerializationFlag() throws IOException { //方法名称描述有问，该请求头是有序列化id的
        //00000010-response/oneway/hearbeat=false/noset |20-stats=ok|id=0|length=0
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, (byte) 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Response obj = (Response) decode(request);
        Assertions.assertEquals(20, obj.getStatus());
        Assertions.assertEquals(person, obj.getResult());
        System.out.println(obj);
    }

    @Test
    public void test_Header_Response_Heartbeat() throws IOException { //测试正常响应解码
        //00000010-response/oneway/hearbeat=true |20-stats=ok|id=0|length=0
        byte[] header = new byte[] {MAGIC_HIGH, MAGIC_LOW, 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Response obj = (Response) decode(request);
        Assertions.assertEquals(20, obj.getStatus());
        Assertions.assertEquals(person, obj.getResult());
        System.out.println(obj);
    }

    @Test
    public void test_Encode_Request() throws IOException { // 测试对请求对象Request的编码
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(2014); //未指定ChannelBufferFactory时，默认使用HeapChannelBufferFactory创建
        Channel channel = getCliendSideChannel(url);
        Request request = new Request();
        Person person = new Person();
        request.setData(person);

        codec.encode(channel, encodeBuffer, request); //将请求对象的内容序列化为字节数组，并写到channelBuffer中

        //encode resault check need decode
        byte[] data = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(data); //从ChannelBuffer中读取字节数组内容，并写到目标数组中
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(data);
        Request obj = (Request) codec.decode(channel, decodeBuffer);
        Assertions.assertEquals(request.isBroken(), obj.isBroken()); //比较编码前的Request数据和解码后decode的数据
        Assertions.assertEquals(request.isHeartbeat(), obj.isHeartbeat());
        Assertions.assertEquals(request.isTwoWay(), obj.isTwoWay());
        Assertions.assertEquals(person, obj.getData());
    }

    @Test
    public void test_Encode_Response() throws IOException { //对响应对象Response进行编码（todo @pause）
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(1024);
        Channel channel = getCliendSideChannel(url);
        Response response = new Response();
        response.setHeartbeat(true);
        response.setId(1001L);
        response.setStatus((byte) 20);
        response.setVersion("11");
        Person person = new Person();
        response.setResult(person);

        codec.encode(channel, encodeBuffer, response); //编码响应对象
        byte[] data = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(data);

        //encode resault check need decode
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(data);
        Response obj = (Response) codec.decode(channel, decodeBuffer);

        Assertions.assertEquals(response.getId(), obj.getId());
        Assertions.assertEquals(response.getStatus(), obj.getStatus());
        Assertions.assertEquals(response.isHeartbeat(), obj.isHeartbeat());
        Assertions.assertEquals(person, obj.getResult());
        // encode response verson ??
//        Assertions.assertEquals(response.getProtocolVersion(), obj.getVersion());

    }

    @Test
    public void test_Encode_Error_Response() throws IOException { // 测试含有异常信息的Response编码
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(1024);
        Channel channel = getCliendSideChannel(url);
        Response response = new Response();
        response.setHeartbeat(true);
        response.setId(1001L);
        response.setStatus((byte) 10); //正常状态为20，响应异常
        response.setVersion("11");
        String badString = "bad";
        response.setErrorMessage(badString); //异常的信息会写到errorMessage中
        Person person = new Person();
        response.setResult(person);

        codec.encode(channel, encodeBuffer, response);
        byte[] data = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(data); //从buffer中读取内容，写到目标数组中

        //encode resault check need decode
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(data);
        Response obj = (Response) codec.decode(channel, decodeBuffer);
        Assertions.assertEquals(response.getId(), obj.getId());
        Assertions.assertEquals(response.getStatus(), obj.getStatus());
        Assertions.assertEquals(response.isHeartbeat(), obj.isHeartbeat());
        Assertions.assertEquals(badString, obj.getErrorMessage());
        Assertions.assertNull(obj.getResult());
//        Assertions.assertEquals(response.getProtocolVersion(), obj.getVersion());
    }

    @Test
    public void testMessageLengthGreaterThanMessageActualLength() throws Exception { //测试编解码
        Channel channel = getCliendSideChannel(url);
        Request request = new Request(1L);
        request.setVersion(Version.getProtocolVersion());
        Date date = new Date();
        request.setData(date);
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(1024);
        codec.encode(channel, encodeBuffer, request);
        byte[] bytes = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(bytes);
        int len = Bytes.bytes2int(bytes, 12);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        out.write(bytes, 0, 12); //将指定字节数组的指定位置、指定长度的字节写到输出流
        /*
         * The fill length can not be less than 256, because by default, hessian reads 256 bytes from the stream each time.
         * Refer Hessian2Input.readBuffer for more details
         */
        int padding = 512;
        out.write(Bytes.int2bytes(len + padding));
        out.write(bytes, 16, bytes.length - 16);
        for (int i = 0; i < padding; i++) { //依次填充数据
            out.write(1);
        }
        out.write(bytes);
        /* request|1111...|request */
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(out.toByteArray());
        Request decodedRequest = (Request) codec.decode(channel, decodeBuffer);
        Assertions.assertEquals(date, decodedRequest.getData()); //解码出来的对象与编码前的对象相等
        Assertions.assertEquals(bytes.length + padding, decodeBuffer.readerIndex());
        decodedRequest = (Request) codec.decode(channel, decodeBuffer);
        Assertions.assertEquals(date, decodedRequest.getData());
    }

    @Test
    public void testMessageLengthExceedPayloadLimitWhenEncode() throws Exception { //测试超过有效负载大小的异常情况（编码、解码都会检查）
        Request request = new Request(1L);
        request.setData("hello");
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(512);
        AbstractMockChannel channel = getCliendSideChannel(url.addParameter(Constants.PAYLOAD_KEY, 4));
        try {
            codec.encode(channel, encodeBuffer, request);
            Assertions.fail();
        } catch (IOException e) {
            Assertions.assertTrue(e.getMessage().startsWith("Data length too large: " + 6));
        }

        Response response = new Response(1L);
        response.setResult("hello");
        encodeBuffer = ChannelBuffers.dynamicBuffer(512);
        channel = getServerSideChannel(url.addParameter(Constants.PAYLOAD_KEY, 4));
        codec.encode(channel, encodeBuffer, response);
        Assertions.assertTrue(channel.getReceivedMessage() instanceof Response);
        Response receiveMessage = (Response) channel.getReceivedMessage();
        Assertions.assertEquals(Response.BAD_RESPONSE, receiveMessage.getStatus());
        Assertions.assertTrue(receiveMessage.getErrorMessage().contains("Data length too large: "));
    }
}
