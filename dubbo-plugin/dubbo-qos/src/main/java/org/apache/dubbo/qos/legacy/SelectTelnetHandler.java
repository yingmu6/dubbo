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
package org.apache.dubbo.qos.legacy;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.remoting.telnet.support.Help;

import java.lang.reflect.Method;
import java.util.List;

/**
 * SelectTelnetHandler
 */
@Activate
@Help(parameter = "[index]", summary = "Select the index of the method you want to invoke.",
        detail = "Select the index of the method you want to invoke.")
public class SelectTelnetHandler implements TelnetHandler { //todo @csy-030-P2 当匹配到多个方法时，根据列表选择要调用的方法，待使用
    public static final String SELECT_METHOD_KEY = "telnet.select.method";
    public static final String SELECT_KEY = "telnet.select";

    private InvokeTelnetHandler invokeTelnetHandler = new InvokeTelnetHandler();

    @Override
    @SuppressWarnings("unchecked")
    public String telnet(Channel channel, String message) { //执行invoke GreetingService.hello("111") 时，会出现select提示，todo @csy-031-P2 待调试
        if (message == null || message.length() == 0) {
            return "Please input the index of the method you want to invoke, eg: \r\n select 1";
        }
        List<Method> methodList = (List<Method>) channel.getAttribute(InvokeTelnetHandler.INVOKE_METHOD_LIST_KEY);
        if (CollectionUtils.isEmpty(methodList)) { //需要先执行invoke指令
            return "Please use the invoke command first.";
        }
        if (!StringUtils.isInteger(message) || Integer.parseInt(message) < 1 || Integer.parseInt(message) > methodList.size()) {
            return "Illegal index ,please input select 1~" + methodList.size();
        }
        Method method = methodList.get(Integer.parseInt(message) - 1);
        channel.setAttribute(SELECT_METHOD_KEY, method);
        channel.setAttribute(SELECT_KEY, Boolean.TRUE); //往通道中设置属性
        String invokeMessage = (String) channel.getAttribute(InvokeTelnetHandler.INVOKE_MESSAGE_KEY); //获取invoke在通道中设置的调用信息，并执行invoke调用
        return invokeTelnetHandler.telnet(channel, invokeMessage);
    }
}
