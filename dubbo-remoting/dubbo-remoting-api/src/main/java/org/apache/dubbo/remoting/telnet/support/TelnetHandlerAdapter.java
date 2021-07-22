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

public class TelnetHandlerAdapter extends ChannelHandlerAdapter implements TelnetHandler {

    private final ExtensionLoader<TelnetHandler> extensionLoader = ExtensionLoader.getExtensionLoader(TelnetHandler.class);

    @Override
    public String telnet(Channel channel, String message) throws RemotingException { //@csy-024-P2 此处是否是指令入口、分发的地方？解：是Telnet指令处理的地方
        String prompt = channel.getUrl().getParameterAndDecoded(Constants.PROMPT_KEY, Constants.DEFAULT_PROMPT);
        boolean noprompt = message.contains("--no-prompt");
        message = message.replace("--no-prompt", ""); //@csy-024-P3 此处的参数是什么含义？ 解：telnet提示键
        StringBuilder buf = new StringBuilder();
        message = message.trim();
        String command;
        if (message.length() > 0) {
            int i = message.indexOf(' ');
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
        if (command.length() > 0) {
            if (extensionLoader.hasExtension(command)) { //将命令名作为SPI的扩展名
                if (commandEnabled(channel.getUrl(), command)) {
                    try {
                        String result = extensionLoader.getExtension(command).telnet(channel, message); //从接收的字符串解析出命令，根据dubbo的spi扩展机制获取对应的TelnetHandler实现
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
            buf.append("\r\n");
        }
        if (StringUtils.isNotEmpty(prompt) && !noprompt) {
            buf.append(prompt);
        }
        return buf.toString();
    }

    private boolean commandEnabled(URL url, String command) {
        String supportCommands = url.getParameter(TELNET);
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
