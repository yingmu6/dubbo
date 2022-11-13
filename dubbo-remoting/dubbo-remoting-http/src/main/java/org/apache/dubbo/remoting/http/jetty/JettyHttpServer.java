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
package org.apache.dubbo.remoting.http.jetty;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.http.HttpHandler;
import org.apache.dubbo.remoting.http.servlet.DispatcherServlet;
import org.apache.dubbo.remoting.http.servlet.ServletManager;
import org.apache.dubbo.remoting.http.support.AbstractHttpServer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREADS;

public class JettyHttpServer extends AbstractHttpServer { //Jetty实现的http服务类

    private static final Logger logger = LoggerFactory.getLogger(JettyHttpServer.class);

    private Server server; //维护者jetty的服务类Server

    private URL url;

    public JettyHttpServer(URL url, final HttpHandler handler) { //构建jetty对应的服务实例
        super(url, handler);
        this.url = url;
        // TODO we should leave this setting to slf4j
        // we must disable the debug logging for production use （禁止在生产环境中打印debug日志）
        Log.setLog(new StdErrLog());
        Log.getLog().setDebugEnabled(false); //禁用debug日志

        DispatcherServlet.addHttpHandler(url.getParameter(Constants.BIND_PORT_KEY, url.getPort()), handler);

        int threads = url.getParameter(THREADS_KEY, DEFAULT_THREADS);
        QueuedThreadPool threadPool = new QueuedThreadPool(); //jetty自定义的线程池，可对线程池参数进行操作处理
        threadPool.setDaemon(true);
        threadPool.setMaxThreads(threads);
        threadPool.setMinThreads(threads);

        server = new Server(threadPool); //构建jetty的Server

        ServerConnector connector = new ServerConnector(server); //带着Server构建ServerConnector服务连接器

        String bindIp = url.getParameter(Constants.BIND_IP_KEY, url.getHost());
        if (!url.isAnyHost() && NetUtils.isValidLocalHost(bindIp)) {
            connector.setHost(bindIp);
        }
        connector.setPort(url.getParameter(Constants.BIND_PORT_KEY, url.getPort()));

        server.addConnector(connector);

        ServletHandler servletHandler = new ServletHandler(); //构建Servlet处理器
        ServletHolder servletHolder = servletHandler.addServletWithMapping(DispatcherServlet.class, "/*");
        servletHolder.setInitOrder(2);

        // dubbo's original impl can't support the use of ServletContext
        //        server.addHandler(servletHandler);
        // TODO Context.SESSIONS is the best option here? (In jetty 9.x, it becomes ServletContextHandler.SESSIONS)
        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
        context.setServletHandler(servletHandler);
        ServletManager.getInstance().addServletContext(url.getParameter(Constants.BIND_PORT_KEY, url.getPort()), context.getServletContext()); //将端口号与Servlet上下文进行缓存

        try {
            server.start(); //启动jetty服务
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start jetty server on " + url.getParameter(Constants.BIND_IP_KEY) + ":" + url.getParameter(Constants.BIND_PORT_KEY) + ", cause: "
                + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        super.close(); //更改关闭标志

        ServletManager.getInstance().removeServletContext(url.getParameter(Constants.BIND_PORT_KEY, url.getPort())); //移除缓存中对应的ServletContext

        if (server != null) {
            try {
                server.stop(); //停止服务
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }

}
