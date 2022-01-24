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
package org.apache.dubbo.metadata.report.identifier;

import org.apache.dubbo.common.URL;

import static org.apache.dubbo.common.constants.CommonConstants.*;

/**
 * The MetadataIdentifier is used to store method descriptor（存储方法的描述）.
 * <p>
 * The name of class is reserved（保留的） because of it has been used in the previous version.
 * （类名是保留的，因为在以前的版本中使用过它）
 * <p>
 * 2018/10/25
 */
public class MetadataIdentifier extends BaseServiceMetadataIdentifier implements BaseMetadataIdentifier { //todo @csy 元数据之间的继承关系是怎样的？都有哪些元数据？

    private String application;

    public MetadataIdentifier() {
    }

    public MetadataIdentifier(String serviceInterface, String version, String group, String side, String application) {
        this.serviceInterface = serviceInterface; //服务接口，如org.apache.dubbo.rpc.service.EchoService
        this.version = version; //服务版本
        this.group = group;
        this.side = side; //服务提供方，如"provider"
        this.application = application;
    }


    public MetadataIdentifier(URL url) {
        this.serviceInterface = url.getServiceInterface();
        this.version = url.getParameter(VERSION_KEY);
        this.group = url.getParameter(GROUP_KEY);
        this.side = url.getParameter(SIDE_KEY);
        setApplication(url.getParameter(APPLICATION_KEY));
    }

    public String getUniqueKey(KeyTypeEnum keyType) {
        return super.getUniqueKey(keyType, application);
    }

    public String getIdentifierKey() {
        return super.getIdentifierKey(application);
    }

    public String getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

}
