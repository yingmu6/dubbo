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
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.remoting.telnet.support.Help;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;

/**
 * ChangeServiceTelnetHandler
 */
@Activate
@Help(parameter = "[service]", summary = "Change default service.", detail = "Change default service.")
public class ChangeTelnetHandler implements TelnetHandler { //改变缺省服务：设置缺省服务，需要服务名作为参数的地方，都可以省略服务参数， @csy-029-P2 其它指令时怎么使用确认服务名的 解：从通道中尝试获取缺省服务值，若能取到则使用channel.getAttribute(ChangeTelnetHandler.SERVICE_KEY);

    public static final String SERVICE_KEY = "telnet.service";

    @Override
    public String telnet(Channel channel, String message) { //todo @csy-029-P2 在进入telnet具体实现之前，都经历了哪些调用？Channel的数据是怎么组装的？
        if (message == null || message.length() == 0) {
            return "Please input service name, eg: \r\ncd XxxService\r\ncd com.xxx.XxxService";
        }
        StringBuilder buf = new StringBuilder();
        if ("/".equals(message) || "..".equals(message)) { //取消缺省服务
            String service = (String) channel.getAttribute(SERVICE_KEY); //缺省服务：存储在通道指定属性中的
            channel.removeAttribute(SERVICE_KEY);
            buf.append("Cancelled default service ").append(service).append(".");
        } else {
            boolean found = false;

            // 从暴露的服务列表中，查找是否存在指定的服务
            for (Exporter<?> exporter : DubboProtocol.getDubboProtocol().getExporters()) { //将输入的服务名依次与暴露的服务名进行比较，todo @csy-029-P3 此处只从dubbo暴露的服务中查找，是不是不通过dubbo协议暴露的，就存在找不到服务问题？
                if (message.equals(exporter.getInvoker().getInterface().getSimpleName()) //getSimpleName() 简写类名，不包含包名DemoService
                        || message.equals(exporter.getInvoker().getInterface().getName()) //getName() 完整类名：org.apache.dubbo.demo.DemoService
                        || message.equals(exporter.getInvoker().getUrl().getPath())) { //getPath() url中配置的路径，如：org.apache.dubbo.demo.DemoService
                    found = true;
                    break;
                }
            }
            if (found) { //若发现存在指定服务，则将服务名存入通道的属性中
                channel.setAttribute(SERVICE_KEY, message); //若设置其它的确认服务，直接覆盖更新
                buf.append("Used the ").append(message).append(" as default.\r\nYou can cancel default service by command: cd /");
            } else {
                buf.append("No such service ").append(message);
            }
        }
        return buf.toString(); //可以在不同的通道（不同的连接），设置各自的缺省服务
    }

}
