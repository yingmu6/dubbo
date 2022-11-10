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

import org.apache.dubbo.remoting.http.HttpBinder;

/**
 * Only the server that implements servlet container
 * could support something like @Context injection of servlet objects.
 * （只有实现servlet容器的服务器才能支持servlet对象的@Context注入）
 *
 */
public class RestServerFactory {

    private HttpBinder httpBinder;

    public void setHttpBinder(HttpBinder httpBinder) {
        this.httpBinder = httpBinder;
    }

    public RestProtocolServer createServer(String name) { ///创建Rest服务的实例对象
        // TODO move names to Constants
        if ("servlet".equalsIgnoreCase(name) || "jetty".equalsIgnoreCase(name) || "tomcat".equalsIgnoreCase(name)) {
            return new DubboHttpProtocolServer(httpBinder);
        } else if ("netty".equalsIgnoreCase(name)) {
            return new NettyRestProtocolServer();
        } else { //非指定的服务，则抛出异常
            throw new IllegalArgumentException("Unrecognized server name: " + name);
        }
    }
}
