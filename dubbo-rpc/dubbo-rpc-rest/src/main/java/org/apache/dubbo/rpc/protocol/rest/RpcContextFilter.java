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
package org.apache.dubbo.rpc.protocol.rest;

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.RpcContext;

import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Priority(Integer.MIN_VALUE + 1)
public class RpcContextFilter implements ContainerRequestFilter, ClientRequestFilter {

    private static final String DUBBO_ATTACHMENT_HEADER = "Dubbo-Attachments";

    // currently we use a single header to hold the attachments so that the total attachment size limit is about 8k
    private static final int MAX_HEADER_SIZE = 8 * 1024;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException { //主要逻辑：将Http请求头的参数设置到Dubbo的RpcContext的附加参数中
        HttpServletRequest request = ResteasyProviderFactory.getContextData(HttpServletRequest.class); //接收请求数据
        RpcContext.getContext().setRequest(request);//使用Dubbo的RpcContext设置请求对象

        // this only works for servlet containers
        if (request != null && RpcContext.getContext().getRemoteAddress() == null) {
            RpcContext.getContext().setRemoteAddress(request.getRemoteAddr(), request.getRemotePort());
        }

        RpcContext.getContext().setResponse(ResteasyProviderFactory.getContextData(HttpServletResponse.class)); //设置响应对象Response

        String headers = requestContext.getHeaderString(DUBBO_ATTACHMENT_HEADER); //获取请求头参数（Rest方式的附加参数）
        if (headers != null) {
            for (String header : headers.split(",")) { //拆分请求头的参数，依次设置到RpcContext的附加参数中
                int index = header.indexOf("=");
                if (index > 0) {
                    String key = header.substring(0, index);
                    String value = header.substring(index + 1);
                    if (!StringUtils.isEmpty(key)) {
                        RpcContext.getContext().setAttachment(key.trim(), value.trim()); //将Http请求中的数据，写入到Dubbo的RpcContext的附加参数中
                    }
                }
            }
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException { //主要逻辑：将Dubbo的RpcContext的附加参数写到Http请求头的参数中
        int size = 0;
        for (Map.Entry<String, Object> entry : RpcContext.getContext().getObjectAttachments().entrySet()) { //遍历Dubbo的RpcContext的附加参数
            String key = entry.getKey();
            String value = (String) entry.getValue();
            if (illegalHttpHeaderKey(key) || illegalHttpHeaderValue(value)) {
                throw new IllegalArgumentException("The attachments of " + RpcContext.class.getSimpleName() + " must not contain ',' or '=' when using rest protocol");
            }

            // TODO for now we don't consider the differences of encoding and server limit
            if (value != null) {
                size += value.getBytes(StandardCharsets.UTF_8).length;
            }
            if (size > MAX_HEADER_SIZE) { //校验附加参数的大小
                throw new IllegalArgumentException("The attachments of " + RpcContext.class.getSimpleName() + " is too big");
            }

            String attachments = key + "=" + value;
            requestContext.getHeaders().add(DUBBO_ATTACHMENT_HEADER, attachments); //依次将RpcContext的附加参数值，写入到http请求头的参数中（类型为：MultivaluedMap，一个key允许对应0个或多个value值）
        }
    }

    private boolean illegalHttpHeaderKey(String key) {
        if (StringUtils.isNotEmpty(key)) {
            return key.contains(",") || key.contains("=");
        }
        return false;
    }

    private boolean illegalHttpHeaderValue(String value) {
        if (StringUtils.isNotEmpty(value)) {
            return value.contains(",");
        }
        return false;
    }
}
