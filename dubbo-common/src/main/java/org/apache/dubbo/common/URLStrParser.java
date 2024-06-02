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
package org.apache.dubbo.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.utils.StringUtils.EMPTY_STRING;
import static org.apache.dubbo.common.utils.StringUtils.decodeHexByte;
import static org.apache.dubbo.common.utils.Utf8Utils.decodeUtf8;

public final class URLStrParser {

    private static final char SPACE = 0x20;

    private static final ThreadLocal<TempBuf> DECODE_TEMP_BUF = ThreadLocal.withInitial(() -> new TempBuf(1024)); //对字符数组或字节数组做临时缓存

    private URLStrParser() {
        //empty
    }

    /**
     * @param decodedURLStr : after {@link URL#decode} string
     *                      decodedURLStr format: protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [protocol://][username:password@][host:port]/[path][?k1=v1&k2=v2]
     */
    public static URL parseDecodedStr(String decodedURLStr) { //解析已解码的url字符串（即url未被编码）
        Map<String, String> parameters = null;
        int pathEndIdx = decodedURLStr.indexOf('?');
        if (pathEndIdx >= 0) {
            parameters = parseDecodedParams(decodedURLStr, pathEndIdx + 1);
        } else {
            pathEndIdx = decodedURLStr.length();
        }

        String decodedBody = decodedURLStr.substring(0, pathEndIdx);
        return parseURLBody(decodedURLStr, decodedBody, parameters);
    }

    private static Map<String, String> parseDecodedParams(String str, int from) {
        int len = str.length();
        if (from >= len) {
            return Collections.emptyMap();
        }

        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        Map<String, String> params = new HashMap<>();
        int nameStart = from;
        int valueStart = -1;
        int i;
        for (i = from; i < len; i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case ';':
                case '&':
                    addParam(str, false, nameStart, valueStart, i, params, tempBuf);
                    nameStart = i + 1;
                    break;
                default:
                    // continue
            }
        }
        addParam(str, false, nameStart, valueStart, i, params, tempBuf);
        return params;
    }

    /**
     * @param fullURLStr  : fullURLString
     * @param decodedBody : format: [protocol://][username:password@][host:port]/[path]
     * @param parameters  :
     * @return URL
     */
    private static URL parseURLBody(String fullURLStr, String decodedBody, Map<String, String> parameters) {
        int starIdx = 0, endIdx = decodedBody.length();
        String protocol = null;
        int protoEndIdx = decodedBody.indexOf("://");
        if (protoEndIdx >= 0) { //解析url内容时，会判断是否设置protocol，未设置则抛出异常
            if (protoEndIdx == 0) {
                throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
            }
            protocol = decodedBody.substring(0, protoEndIdx);
            starIdx = protoEndIdx + 3;
        } else {
            // case: file:/path/to/file.txt
            protoEndIdx = decodedBody.indexOf(":/");
            if (protoEndIdx >= 0) {
                if (protoEndIdx == 0) {
                    throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
                }
                protocol = decodedBody.substring(0, protoEndIdx);
                starIdx = protoEndIdx + 1;
            }
        }

        String path = null;
        int pathStartIdx = indexOf(decodedBody, '/', starIdx, endIdx);
        if (pathStartIdx >= 0) {
            path = decodedBody.substring(pathStartIdx + 1);
            endIdx = pathStartIdx;
        }

        String username = null;
        String password = null;
        int pwdEndIdx = lastIndexOf(decodedBody, '@', starIdx, endIdx);
        if (pwdEndIdx > 0) {
            int userNameEndIdx = indexOf(decodedBody, ':', starIdx, pwdEndIdx);
            username = decodedBody.substring(starIdx, userNameEndIdx);
            password = decodedBody.substring(userNameEndIdx + 1, pwdEndIdx);
            starIdx = pwdEndIdx + 1;
        }

        String host = null;
        int port = 0;
        int hostEndIdx = lastIndexOf(decodedBody, ':', starIdx, endIdx);
        if (hostEndIdx > 0 && hostEndIdx < decodedBody.length() - 1) {
            if (lastIndexOf(decodedBody, '%', starIdx, endIdx) > hostEndIdx) {
                // ipv6 address with scope id
                // e.g. fe80:0:0:0:894:aeec:f37d:23e1%en0
                // see https://howdoesinternetwork.com/2013/ipv6-zone-id
                // ignore
            } else {
                port = Integer.parseInt(decodedBody.substring(hostEndIdx + 1, endIdx));
                endIdx = hostEndIdx;
            }
        }

        if (endIdx > starIdx) {
            host = decodedBody.substring(starIdx, endIdx);
        }
        return new URL(protocol, username, password, host, port, path, parameters);
    }

    /**
     * @param encodedURLStr : after {@link URL#encode(String)} string
     *                      encodedURLStr after decode format: protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [protocol://][username:password@][host:port]/[path][?k1=v1&k2=v2] （解码后的数据格式）
     */
    public static URL parseEncodedStr(String encodedURLStr) { //解析编码后的URL字符串，产生对应的URL对象
        Map<String, String> parameters = null; //编码前的字符串/context/path?version=1.0.0&application=morgan，编码后的字符串：%2Fcontext%2Fpath%3Fapplication%3Dmorgan%26version%3D1.0.0
        int pathEndIdx = encodedURLStr.indexOf("%3F");// '?'  查找参数分隔符（%3F对应的ASCII值为'?'）
        if (pathEndIdx >= 0) { //解析url中的参数
            parameters = parseEncodedParams(encodedURLStr, pathEndIdx + 3); //取%3F后面的字符串处理
        } else { //url中不包含参数
            pathEndIdx = encodedURLStr.length();
        }

        //decodedBody format: [protocol://][username:password@][host:port]/[path]
        String decodedBody = decodeComponent(encodedURLStr, 0, pathEndIdx, false, DECODE_TEMP_BUF.get()); //解析url中的主体
        return parseURLBody(encodedURLStr, decodedBody, parameters);
    }

    /**
     * 流程分析：解析出URL中参数集合（Done）
     * 1）查找字符串中'%'，解析出后面的字符，与'='、'&' 进行比对，来判断是否是参数
     * 2）用下标nameStart、valueStart记录参数key、value的开始下标，对应也计算key、value的结束下标
     * 3）在addParam()中，对获取到的参数key、value进行URL解码，最后存入URL的参数Map中
     *
     * 备注：用例分析
     * 1）解码前的URL：dubbo%3A%2F%2Fadmin%3Aadmin123%40192.168.1.41%3A28113%2Forg.test.api.DemoService%24Iface%3Fanyhost%3Dtrue%26application%3Ddemo-service%26dubbo%3D2.6.1%26generic%3Dfalse%26interface%3Dorg.test.api.DemoService%24Iface%26methods%3DorbCompare%2CcheckText%2CcheckPicture...
     * 2）解码后的URL：dubbo://192.168.1.41:28113/org.test.api.DemoService$Iface?anyhost=true&application=demo-service&dubbo=2.6.1&generic=false&interface=org.test.api.DemoService$Iface&methods=orbCompare,checkText,checkPicture...
     */
    private static Map<String, String> parseEncodedParams(String str, int from) { //解析url中的参数键值对
        int len = str.length();
        if (from >= len) { //对应"?"后面没有键值对的场景，如dubbo://xxx?
            return Collections.emptyMap();
        }

        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        Map<String, String> params = new HashMap<>();
        int nameStart = from; //参数key的开始下标（key结束下标可由valueStart计算，即valueStart减1或减3）
        int valueStart = -1;  //参数value的开始下标（value的结束下标可由变量i进行计算）
        int i;
        for (i = from; i < len; i++) { //遍历url字符串的字符（from为参数起始位置）
            char ch = str.charAt(i);
            if (ch == '%') { //遇到百分号分隔符，解码得到原始的字符（ASCII中的字符，经过url编码后，都是类似 %xx，即%后面带上两个16进制字符）
                if (i + 3 > len) { //分隔符不是完整的情况，抛出异常，比如%3、%等，应该是%3D，百分号后面带两个十六进制数
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + str);
                }
                ch = (char) decodeHexByte(str, i + 1); //将'%'后面的2个16进制字符转换为字符，如"3D"处理后，得到字符'='
                i += 2; //跳过已处理的2个字符
            }

            switch (ch) {
                case '=': //按键值对处理
                    if (nameStart == i) { //对应那种场景？
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) { //因为参数的key、value都是url的字串，所以要计算出字符串的开始位置、结束位置
                        valueStart = i + 1; //记录值的下标
                    }
                    break;
                case ';':
                case '&': //多个参数时，进行参数拼接
                    addParam(str, true, nameStart, valueStart, i - 2, params, tempBuf);
                    nameStart = i + 1;
                    break;
                default: //非分隔符，不做处理
                    // continue
            }
        }
        addParam(str, true, nameStart, valueStart, i, params, tempBuf);
        return params;
    }

    private static boolean addParam(String str, boolean isEncoded, int nameStart, int valueStart, int valueEnd, Map<String, String> params,
                                    TempBuf tempBuf) {
        if (nameStart >= valueEnd) {
            return false;
        }

        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }

        if (isEncoded) { //对编码过的URL中的键值对进行解码，并设置到参数Map中
            String name = decodeComponent(str, nameStart, valueStart - 3, false, tempBuf); //字符串区间：[nameStart, valueStart-3)得到的字串即为key，减3是把'='对应的'%3D'的字符去掉
            String value = decodeComponent(str, valueStart, valueEnd, false, tempBuf);
            params.put(name, value);
        } else { //URL未被编码时，直接取url字符串中的值，设置到参数Map中
            String name = str.substring(nameStart, valueStart -1); //字符串区间：[nameStart, valueStart-3)得到的字串即为key，减1是因为此时url没有编码，'='就只占用一个字符
            String value = str.substring(valueStart, valueEnd);
            params.put(name, value);
        }
        return true;
    }

    /**
     * 流程分析：解码URL组件（Doing）
     * 1）
     * 2）
     * 3）
     *
     */
    private static String decodeComponent(String s, int from, int toExcluded, boolean isPath, TempBuf tempBuf) { //解码
        int len = toExcluded - from;
        if (len <= 0) {
            return EMPTY_STRING;
        }

        int firstEscaped = -1;
        for (int i = from; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c == '%' || c == '+' && !isPath) { //判断指定区间 from ~ toExcluded，是否包含 '%'、'+'等字符
                firstEscaped = i;
                break;
            }
        }
        if (firstEscaped == -1) { //若字符串中没有'%'、'+'等特殊字符，则直接取字串
            return s.substring(from, toExcluded);
        }

        // Each encoded byte takes 3 characters (e.g. "%20")
        int decodedCapacity = (toExcluded - firstEscaped) / 3; //存在特殊字符，要先去掉特殊字符（如字符串：interface%3Dorg.test.api.DemoService%24Iface（解码后：interface=org.test.api.DemoService$Iface））
        byte[] buf = tempBuf.byteBuf(decodedCapacity);
        char[] charBuf = tempBuf.charBuf(len);
        s.getChars(from, firstEscaped, charBuf, 0); //从字符串中拷贝字符到目标数组中

        int charBufIdx = firstEscaped - from;
        return decodeUtf8Component(s, firstEscaped, toExcluded, isPath, buf, charBuf, charBufIdx);
    }

    private static String decodeUtf8Component(String str, int firstEscaped, int toExcluded, boolean isPath, byte[] buf,
                                              char[] charBuf, int charBufIdx) {
        int bufIdx;
        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = str.charAt(i);
            if (c != '%') { //没遇到特殊字符，则将字符放入数组中
                charBuf[charBufIdx++] = c != '+' || isPath ? c : SPACE;
                continue;
            }

            bufIdx = 0;
            do {
                if (i + 3 > toExcluded) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + str);
                }
                buf[bufIdx++] = decodeHexByte(str, i + 1); //解析特殊字符'%'后面的数，得到字节值，并放入字节数组
                i += 3;
            } while (i < toExcluded && str.charAt(i) == '%');
            i--;

            charBufIdx += decodeUtf8(buf, 0, bufIdx, charBuf, charBufIdx); //字节数组buf的用途：存储了特殊字符的字节值，如'/'对应的47，经过decodeUtf8解码后存入字符数组中
        }
        return new String(charBuf, 0, charBufIdx);
    }

    private static int indexOf(String str, char ch, int from, int toExclude) {
        from = Math.max(from, 0);
        toExclude = Math.min(toExclude, str.length());
        if (from > toExclude) {
            return -1;
        }

        for (int i = from; i < toExclude; i++) {
            if (str.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(String str, char ch, int from, int toExclude) {
        from = Math.max(from, 0);
        toExclude = Math.min(toExclude, str.length() - 1);
        if (from > toExclude) {
            return -1;
        }

        for (int i = toExclude; i >= from; i--) {
            if (str.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    private static final class TempBuf { //字符数组和字节数组的临时缓存

        private final char[] chars;

        private final byte[] bytes;

        TempBuf(int bufSize) {
            this.chars = new char[bufSize];
            this.bytes = new byte[bufSize];
        }

        public char[] charBuf(int size) { //构建字符数组
            char[] chars = this.chars;
            if (size <= chars.length) { //若构建的字符数，不超过当前字符数组的数量，则使用当前的字符数组
                return chars;
            }
            return new char[size];
        }

        public byte[] byteBuf(int size) { //构建字节数组
            byte[] bytes = this.bytes;
            if (size <= bytes.length) {
                return bytes;
            }
            return new byte[size];
        }
    }
}
