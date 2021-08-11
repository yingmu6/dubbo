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
public class SelectTelnetHandler implements TelnetHandler {
    public static final String SELECT_METHOD_KEY = "telnet.select.method";
    public static final String SELECT_KEY = "telnet.select";

    private InvokeTelnetHandler invokeTelnetHandler = new InvokeTelnetHandler();

    @Override
    @SuppressWarnings("unchecked")
    public String telnet(Channel channel, String message) { //执行invoke GreetingService.hello("111") 时，会出现select提示
        if (message == null || message.length() == 0) {
            return "Please input the index of the method you want to invoke, eg: \r\n select 1";
        }
        List<Method> methodList = (List<Method>) channel.getAttribute(InvokeTelnetHandler.INVOKE_METHOD_LIST_KEY); //获取invoke指令匹配的方法列表
        if (CollectionUtils.isEmpty(methodList)) { //需要先执行invoke指令
            return "Please use the invoke command first.";
        }
        if (!StringUtils.isInteger(message) || Integer.parseInt(message) < 1 || Integer.parseInt(message) > methodList.size()) { //对选择的下标进行合法性校验
            return "Illegal index ,please input select 1~" + methodList.size();
        }
        Method method = methodList.get(Integer.parseInt(message) - 1); //根据用户选择的下标，选择到具体执行的方法Method，并写到通道属性中，接下来执行invoke指令时会用到
        channel.setAttribute(SELECT_METHOD_KEY, method);
        channel.setAttribute(SELECT_KEY, Boolean.TRUE);
        String invokeMessage = (String) channel.getAttribute(InvokeTelnetHandler.INVOKE_MESSAGE_KEY); //获取invoke在通道中设置的调用信息，并执行invoke调用
        return invokeTelnetHandler.telnet(channel, invokeMessage);
    }
}
