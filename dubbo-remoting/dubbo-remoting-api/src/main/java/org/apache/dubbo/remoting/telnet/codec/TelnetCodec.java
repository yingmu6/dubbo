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
package org.apache.dubbo.remoting.telnet.codec;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.transport.codec.TransportCodec;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.apache.dubbo.remoting.Constants.CHARSET_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_CHARSET;

/**
 * TelnetCodec
 */
public class TelnetCodec extends TransportCodec { //在终端执行telnet指定的编解码

    private static final Logger logger = LoggerFactory.getLogger(TelnetCodec.class);

    private static final String HISTORY_LIST_KEY = "telnet.history.list";

    private static final String HISTORY_INDEX_KEY = "telnet.history.index";

    private static final byte[] UP = new byte[] {27, 91, 65};

    private static final byte[] DOWN = new byte[] {27, 91, 66};

    private static final List<?> ENTER = Arrays.asList( //换行指令 （参照ASCII码对照表）
            new byte[] {'\r', '\n'} /* Windows Enter */,
            new byte[] {'\n'} /* Linux Enter */);

    private static final List<?> EXIT = Arrays.asList( //退出对应的字节数组，是个二维数组
            new byte[] {3} /* Windows Ctrl+C */,
            new byte[] {-1, -12, -1, -3, 6} /* Linux Ctrl+C */,
            new byte[] {-1, -19, -1, -3, 6} /* Linux Pause */);

    /**
     * 获取字符集的逻辑
     * 1）从通道Channel的设置的属性值获取
     * 2）若没有，从通道的Url中获取
     * 3）若还没有，则取默认的字符集（默认字符集为UTF-8）
     */
    private static Charset getCharset(Channel channel) {
        if (channel != null) {
            Object attribute = channel.getAttribute(CHARSET_KEY); //获取配置的字符集名称
            if (attribute instanceof String) { //判断是String类型还是Charset类型
                try {
                    return Charset.forName((String) attribute); //尝试获取指定字符串的字符编码
                } catch (Throwable t) {
                    logger.warn(t.getMessage(), t);
                }
            } else if (attribute instanceof Charset) {
                return (Charset) attribute;
            }
            URL url = channel.getUrl(); //远程url
            if (url != null) {
                String parameter = url.getParameter(CHARSET_KEY);
                if (StringUtils.isNotEmpty(parameter)) {
                    try {
                        return Charset.forName(parameter);
                    } catch (Throwable t) {
                        logger.warn(t.getMessage(), t);
                    }
                }
            }
        }
        try {
            return Charset.forName(DEFAULT_CHARSET);
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
        return Charset.defaultCharset();
    }

    private static String toString(byte[] message, Charset charset) throws UnsupportedEncodingException {
        byte[] copy = new byte[message.length];
        int index = 0;
        for (int i = 0; i < message.length; i++) { //依次对每个字符处理，先判断是否是特殊字符，若不是再按普通字符处理
            byte b = message[i];
            if (b == '\b') { // backspace（退格符）
                if (index > 0) {
                    index--;
                }
                if (i > 2 && message[i - 2] < 0) { // double byte char
                    if (index > 0) {
                        index--;
                    }
                }
            } else if (b == 27) { // escape
                if (i < message.length - 4 && message[i + 4] == 126) {
                    i = i + 4;
                } else if (i < message.length - 3 && message[i + 3] == 126) {
                    i = i + 3;
                } else if (i < message.length - 2) {
                    i = i + 2;
                }
            } else if (b == -1 && i < message.length - 2
                    && (message[i + 1] == -3 || message[i + 1] == -5)) { // handshake
                i = i + 2;
            } else { // 按普通字符处理
                copy[index++] = message[i];
            }
        }
        if (index == 0) {
            return "";
        }
        return new String(copy, 0, index, charset.name()).trim(); //将字符数组按指定字符编码，解码为对应字符串
    }

    private static boolean isEquals(byte[] message, byte[] command) throws IOException { //判断第一个数组是否和第二个数组相等
        return message.length == command.length && endsWith(message, command);
    }

    private static boolean endsWith(byte[] message, byte[] command) throws IOException { //判断第一个数组是否是以第二个数组结尾
        if (message.length < command.length) {
            return false;
        }
        int offset = message.length - command.length;
        for (int i = command.length - 1; i >= 0; i--) {
            if (message[offset + i] != command[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object message) throws IOException { //响应请求内容时编码
        if (message instanceof String) { //字符串类型处理
            if (isClientSide(channel)) {
                message = message + "\r\n";
            }
            byte[] msgData = ((String) message).getBytes(getCharset(channel).name()); //若是字符串，直接根据字符集获取字节数组
            buffer.writeBytes(msgData);
        } else { //对象类型处理，交由父类来处理
            super.encode(channel, buffer, message);
        }
    }

    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException { //收到请求内容时解码
        int readable = buffer.readableBytes();
        byte[] message = new byte[readable];
        buffer.readBytes(message);
        return decode(channel, buffer, readable, message);
    }
    
    @SuppressWarnings("unchecked")
    protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] message) throws IOException {
        if (isClientSide(channel)) { //若是客户端，直接将字节数组转换为字符串
            return toString(message, getCharset(channel)); //获取字符集，并将字符数组转换为字符串
        }
        checkPayload(channel, readable);
        if (message == null || message.length == 0) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        if (message[message.length - 1] == '\b') { // Windows backspace echo
            try {
                boolean doublechar = message.length >= 3 && message[message.length - 3] < 0; // double byte char
                channel.send(new String(doublechar ? new byte[] {32, 32, 8, 8} : new byte[] {32, 8}, getCharset(channel).name()));
            } catch (RemotingException e) {
                throw new IOException(StringUtils.toString(e));
            }
            return DecodeResult.NEED_MORE_INPUT;
        }

        for (Object command : EXIT) {
            if (isEquals(message, (byte[]) command)) { //判断是否包含"退出指令"，若包含则关闭channel
                if (logger.isInfoEnabled()) {
                    logger.info(new Exception("Close channel " + channel + " on exit command: " + Arrays.toString((byte[]) command)));
                }
                channel.close();
                return null;
            }
        }

        boolean up = endsWith(message, UP);
        boolean down = endsWith(message, DOWN);
        if (up || down) {
            LinkedList<String> history = (LinkedList<String>) channel.getAttribute(HISTORY_LIST_KEY);
            if (CollectionUtils.isEmpty(history)) {
                return DecodeResult.NEED_MORE_INPUT;
            }
            Integer index = (Integer) channel.getAttribute(HISTORY_INDEX_KEY);
            Integer old = index;
            if (index == null) {
                index = history.size() - 1;
            } else {
                if (up) {
                    index = index - 1;
                    if (index < 0) {
                        index = history.size() - 1;
                    }
                } else {
                    index = index + 1;
                    if (index > history.size() - 1) {
                        index = 0;
                    }
                }
            }
            if (old == null || !old.equals(index)) {
                channel.setAttribute(HISTORY_INDEX_KEY, index);
                String value = history.get(index);
                if (old != null && old >= 0 && old < history.size()) {
                    String ov = history.get(old);
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < ov.length(); i++) {
                        buf.append("\b");
                    }
                    for (int i = 0; i < ov.length(); i++) {
                        buf.append(" ");
                    }
                    for (int i = 0; i < ov.length(); i++) {
                        buf.append("\b");
                    }
                    value = buf.toString() + value;
                }
                try {
                    channel.send(value);
                } catch (RemotingException e) {
                    throw new IOException(StringUtils.toString(e));
                }
            }
            return DecodeResult.NEED_MORE_INPUT;
        }
        for (Object command : EXIT) {
            if (isEquals(message, (byte[]) command)) {
                if (logger.isInfoEnabled()) {
                    logger.info(new Exception("Close channel " + channel + " on exit command " + command));
                }
                channel.close();
                return null;
            }
        }
        byte[] enter = null;
        for (Object command : ENTER) {
            if (endsWith(message, (byte[]) command)) {
                enter = (byte[]) command;
                break;
            }
        }
        if (enter == null) { //如果都不是上述的Telnet指令，则认为数据不完整，还需要输入更多的数据
            return DecodeResult.NEED_MORE_INPUT;
        }
        LinkedList<String> history = (LinkedList<String>) channel.getAttribute(HISTORY_LIST_KEY);
        Integer index = (Integer) channel.getAttribute(HISTORY_INDEX_KEY);
        channel.removeAttribute(HISTORY_INDEX_KEY);
        if (CollectionUtils.isNotEmpty(history) && index != null && index >= 0 && index < history.size()) {
            String value = history.get(index);
            if (value != null) {
                byte[] b1 = value.getBytes();
                byte[] b2 = new byte[b1.length + message.length];
                System.arraycopy(b1, 0, b2, 0, b1.length);
                System.arraycopy(message, 0, b2, b1.length, message.length);
                message = b2;
            }
        }
        String result = toString(message, getCharset(channel));
        if (result.trim().length() > 0) {
            if (history == null) {
                history = new LinkedList<String>();
                channel.setAttribute(HISTORY_LIST_KEY, history);
            }
            if (history.isEmpty()) {
                history.addLast(result);
            } else if (!result.equals(history.getLast())) {
                history.remove(result);
                history.addLast(result);
                if (history.size() > 10) {
                    history.removeFirst();
                }
            }
        }
        return result;
    }

}
