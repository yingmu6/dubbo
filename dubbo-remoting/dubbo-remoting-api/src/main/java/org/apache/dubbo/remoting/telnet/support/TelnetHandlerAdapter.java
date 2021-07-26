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
package org.apache.dubbo.remoting.telnet.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.remoting.transport.ChannelHandlerAdapter;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.remoting.Constants.TELNET;

public class TelnetHandlerAdapter extends ChannelHandlerAdapter implements TelnetHandler { //todo @csy-025-P3 了解下telnet的原理

    private final ExtensionLoader<TelnetHandler> extensionLoader = ExtensionLoader.getExtensionLoader(TelnetHandler.class);

    @Override
    public String telnet(Channel channel, String message) throws RemotingException { //@csy-024-P2 此处是否是指令入口、分发的地方？解：是Telnet指令处理的地方
        String prompt = channel.getUrl().getParameterAndDecoded(Constants.PROMPT_KEY, Constants.DEFAULT_PROMPT); //todo @csy-025-P3 PROMPT_KEY在XML中是怎么配置的？
        boolean noprompt = message.contains("--no-prompt");
        message = message.replace("--no-prompt", ""); //@csy-024-P3 此处的参数是什么含义？ 解：telnet提示键（如果做了配置）
        StringBuilder buf = new StringBuilder();
        message = message.trim();
        String command;
        if (message.length() > 0) { //message不为空字符串时，解析指令和执行的内容（输入回车键会收到空字符串）
            int i = message.indexOf(' '); //接收指令时，收到的message不包含提示符，如dubbo> ls，收到的message为ls
            if (i > 0) { //拆分命令和参数
                command = message.substring(0, i).trim();
                message = message.substring(i + 1).trim();
            } else {
                command = message;
                message = "";
            }
        } else {
            command = "";
        }
        if (command.length() > 0) { //@csy-025-P3 telnet输入回车时，会进行怎样的操作？解：输入回车时，写到通道的内容为空字符串，不会进入此处逻辑
            if (extensionLoader.hasExtension(command)) { //将命令名作为SPI的扩展名，todo @csy-025-P3 为什么org.apache.dubbo.remoting.telnet.TelnetHandler配置文件会有多个？SPI扩展时，到底加载哪些？
                if (commandEnabled(channel.getUrl(), command)) {
                    try {
                        String result = extensionLoader.getExtension(command).telnet(channel, message); //获取指定命令的实例，并将结果写到channel
                        if (result == null) {
                            return null;
                        }
                        buf.append(result);
                    } catch (Throwable t) {
                        buf.append(t.getMessage());
                    }
                } else {
                    buf.append("Command: ");
                    buf.append(command);
                    buf.append(" disabled");
                }
            } else {
                buf.append("Unsupported command: ");
                buf.append(command);
            }
        }
        if (buf.length() > 0) {
            buf.append("\r\n"); //todo @csy-025-P3 "\r\n" 分别代表什么含义？
        }
        if (StringUtils.isNotEmpty(prompt) && !noprompt) { //提示键处理：若提示键内容不为空且没有禁用，则作对应展示
            buf.append(prompt); //todo @csy-025-P3 这里为啥将提示语放在最后？拼接书序倒是怎样？
        }
        return buf.toString(); //响应给telnet客户端的内容，todo @csy-025-P2 是怎么响应的？
    }

    private boolean commandEnabled(URL url, String command) { //判断指令是否能启用
        String supportCommands = url.getParameter(TELNET); //todo @csy-025-P3 TELNET这个URL参数XML是哪里设置的？
        if (StringUtils.isEmpty(supportCommands)) {
            return true;
        }
        String[] commands = COMMA_SPLIT_PATTERN.split(supportCommands);
        for (String c : commands) {
            if (command.equals(c)) {
                return true;
            }
        }
        return false;
    }

}
