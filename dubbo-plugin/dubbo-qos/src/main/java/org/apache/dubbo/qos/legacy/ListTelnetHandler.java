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
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.qos.command.util.ServiceCheckUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.remoting.telnet.support.Help;
import org.apache.dubbo.rpc.model.*;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ListTelnetHandler handler list services and its methods details.
 */
@Activate
@Help(parameter = "[-l] [service]", summary = "List services and methods.", detail = "List services and methods.")
public class ListTelnetHandler implements TelnetHandler { //todo @csy-029-P3 是否做了缓存，感觉第一次慢些，后面都比较快的

    private ServiceRepository serviceRepository = ApplicationModel.getServiceRepository();

    @Override
    public String telnet(Channel channel, String message) { //todo @csy-027-P3 为什么ls org.apache.dubbo.demo.GreetingService与ls GreetingService中类全路径名和类的简称效果是一样的？
        StringBuilder buf = new StringBuilder();
        String service = null;
        boolean detail = false;
        if (message.length() > 0) {
            String[] parts = message.split("\\s+");
            for (String part : parts) {
                if ("-l".equals(part)) {
                    detail = true;
                } else {
                    if (!StringUtils.isEmpty(service)) {
                        return "Invalid parameter " + part;
                    }
                    service = part;
                }
            }
        } else {
            service = (String) channel.getAttribute(ChangeTelnetHandler.SERVICE_KEY);
            if (StringUtils.isNotEmpty(service)) {
                buf.append("Use default service ").append(service).append(".\r\n");
            }
        }

        if (StringUtils.isEmpty(service)) { //若没指定服务，则打印所有服务
            printAllServices(buf, detail);
        } else {
            printSpecifiedService(service, buf, detail);

            if (buf.length() == 0) {
                buf.append("No such service: ").append(service);
            }
        }
        return buf.toString();
    }

    private void printAllServices(StringBuilder buf, boolean detail) {
        printAllProvidedServices(buf, detail);
        printAllReferredServices(buf, detail);
    }

    private void printAllProvidedServices(StringBuilder buf, boolean detail) { //输出所有提供者暴露的服务信息
        List<ProviderModel> providerModels = serviceRepository.getExportedServices(); //从缓存的服务仓库里获取提供者模型列表
        if (!providerModels.isEmpty()) {
            buf.append("PROVIDER:\r\n");
        }

        for (ProviderModel provider : providerModels) {
            buf.append(provider.getServiceKey());
            if (detail) {
                buf.append(" -> ");
                buf.append(" published: ");
                buf.append(ServiceCheckUtils.isRegistered(provider) ? "Y" : "N");
            }
            buf.append("\r\n");
        }
    }

    private void printAllReferredServices(StringBuilder buf, boolean detail) { //输出所有消费者引用的服务信息
        List<ConsumerModel> consumerModels = serviceRepository.getReferredServices();
        if (!consumerModels.isEmpty()) {
            buf.append("CONSUMER:\r\n");
        }

        for (ConsumerModel consumer : consumerModels) {
            buf.append(consumer.getServiceKey());
            if (detail) {
                buf.append(" -> ");
                buf.append(" addresses: ");
                buf.append(ServiceCheckUtils.getConsumerAddressNum(consumer));
            }
        }
    }

    private void printSpecifiedService(String service, StringBuilder buf, boolean detail) {
        printSpecifiedProvidedService(service, buf, detail);
        printSpecifiedReferredService(service, buf, detail);
    }

    private void printSpecifiedProvidedService(String service, StringBuilder buf, boolean detail) {
        for (ProviderModel provider : ApplicationModel.allProviderModels()) {
            if (isProviderMatched(service,provider)) {
                buf.append(provider.getServiceKey()).append(" (as provider):\r\n");
                for (MethodDescriptor method : provider.getAllMethods()) {
                    printMethod(method.getMethod(), buf, detail);
                }
            }
        }
    }

    private void printSpecifiedReferredService(String service, StringBuilder buf, boolean detail) { //todo @csy-027-P3 一般情况下，提供者与消费者的xml都是分开的，也可以混合使用吗？此处的as consumer在什么条件下能打印出来
        for (ConsumerModel consumer : ApplicationModel.allConsumerModels()) {
            if (isConsumerMatcher(service, consumer)) {
                buf.append(consumer.getServiceKey()).append(" (as consumer):\r\n");
                for (MethodDescriptor method : consumer.getAllMethods()) {
                    printMethod(method.getMethod(), buf, detail);
                }
            }
        }
    }

    private void printMethod(Method method, StringBuilder buf, boolean detail) {
        if (detail) {
            buf.append('\t').append(ReflectUtils.getName(method));
        } else {
            buf.append('\t').append(method.getName());
        }
        buf.append("\r\n");
    }

    private boolean isProviderMatched(String service, ProviderModel provider) {
        return service.equalsIgnoreCase(provider.getServiceKey())
                || service.equalsIgnoreCase(provider.getServiceInterfaceClass().getName())
                || service.equalsIgnoreCase(provider.getServiceInterfaceClass().getSimpleName());
    }

    private boolean isConsumerMatcher(String service,ConsumerModel consumer) {
        return service.equalsIgnoreCase(consumer.getServiceKey())
                || service.equalsIgnoreCase(consumer.getServiceInterfaceClass().getName())
                || service.equalsIgnoreCase(consumer.getServiceInterfaceClass().getSimpleName());
    }
}
