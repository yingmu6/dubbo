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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.http.HttpBinder;
import org.apache.dubbo.remoting.http.HttpHandler;
import org.apache.dubbo.remoting.http.HttpServer;
import org.apache.dubbo.remoting.http.servlet.BootstrapListener;
import org.apache.dubbo.remoting.http.servlet.ServletManager;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.spi.ResteasyDeployment;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

public class DubboHttpProtocolServer extends BaseRestProtocolServer { //dubbo实现的http协议

    private final HttpServletDispatcher dispatcher = new HttpServletDispatcher(); //http服务派发器
    private final ResteasyDeployment deployment = new ResteasyDeployment(); //用于配置和初始化Rest组件
    private HttpBinder httpBinder;
    private HttpServer httpServer;
//    private boolean isExternalServer;

    public DubboHttpProtocolServer(HttpBinder httpBinder) {
        this.httpBinder = httpBinder;
    }

    @Override
    protected void doStart(URL url) { //启动服务
        // TODO jetty will by default enable keepAlive so the xml config has no effect now
        httpServer = httpBinder.bind(url, new RestHandler());

        ServletContext servletContext = ServletManager.getInstance().getServletContext(url.getPort());
        if (servletContext == null) {
            servletContext = ServletManager.getInstance().getServletContext(ServletManager.EXTERNAL_SERVER_PORT);
        }
        if (servletContext == null) {
            throw new RpcException("No servlet context found. If you are using server='servlet', " +
                    "make sure that you've configured " + BootstrapListener.class.getName() + " in web.xml");
        }

        servletContext.setAttribute(ResteasyDeployment.class.getName(), deployment);

        try {
            dispatcher.init(new SimpleServletConfig(servletContext));
        } catch (ServletException e) {
            throw new RpcException(e);
        }
    }

    @Override
    public void close() {
        httpServer.close();
    }

    @Override
    protected ResteasyDeployment getDeployment() {
        return deployment;
    }

    private class RestHandler implements HttpHandler { //Rest请求处理的内部类

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            RpcContext.getContext().setRemoteAddress(request.getRemoteAddr(), request.getRemotePort()); //设置Rpc上下文的地址信息
            dispatcher.service(request, response); //将请求交由调度器进行服务调度（request：客户端请求信息，response：服务端响应给客户端的信息）
        }
    }

    private static class SimpleServletConfig implements ServletConfig { //ServletConfig：servlet容器的配置对象，用于在初始化期间把信息传递给servlet

        private final ServletContext servletContext; //servlet的上下文信息，定义了一套servlet与servlet容器通信的方法

        public SimpleServletConfig(ServletContext servletContext) {
            this.servletContext = servletContext;
        }

        @Override
        public String getServletName() {
            return "DispatcherServlet";
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String s) {
            return null;
        }

        @Override
        public Enumeration getInitParameterNames() {
            return new Enumeration() {
                @Override
                public boolean hasMoreElements() {
                    return false;
                }

                @Override
                public Object nextElement() {
                    return null;
                }
            };
        }
    }
}
