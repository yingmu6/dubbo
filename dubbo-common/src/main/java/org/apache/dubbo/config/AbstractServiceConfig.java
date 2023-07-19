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
package org.apache.dubbo.config;

import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.support.Parameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.EXPORTER_LISTENER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SERVICE_FILTER_KEY;

/**
 * AbstractServiceConfig
 *
 * @export
 */
public abstract class AbstractServiceConfig extends AbstractInterfaceConfig {

    private static final long serialVersionUID = 1L;

    /**
     * The service version
     */
    protected String version;

    /**
     * The service group
     */
    protected String group;

    /**
     * whether the service is deprecated
     */
    protected Boolean deprecated = false; //是否启用服务

    /**
     * The time delay register service (milliseconds)
     */
    protected Integer delay; //服务延迟暴露的时间（ServiceConfig#export服务暴露时，会判断是否需要延迟暴露服务）

    /**
     * Whether to export the service
     */
    protected Boolean export; //是否暴露服务（ServiceConfig#export服务暴露时，会判断是否需要暴露服务）

    /**
     * The service weight
     */
    protected Integer weight; //服务的权重

     /**
     * Document center
     */
    protected String document; //文档中心链接

     /**
     * Whether to register as a dynamic service or not on register center, the value is true, the status will be enabled
     * after the service registered,and it needs to be disabled manually; if you want to disable the service, you also need
     * manual processing
     */
    protected Boolean dynamic = true; //服务是否动态注册（若为false，即不是动态注册，则需要人工进行启停）

    /**
     * Whether to use token
     */
     /**
     * 令牌token是怎么使用的？以及具体原理是怎样的？消费端是怎么获取token值的
     * 解：令牌验证主要是避免消费者绕过注册中心去直连提供者（如<dubbo:reference url="dubbo://host:ip">）
     * 1）提供端，可在<dubbo:provider/>或<dubbo:service/> 配置token属性，token可以自定义，如token="123"，也可以系统按uuid生成：如token="true"
     * 2）消费端，若提供端配置token值，就需要配置注册中心<dubbo:registry/>，不能使用直连方式
     * 3）具体原理是：使用TokenFilter进行拦截校验，将远端配置配置的token信息，与本地进行比较
     */
    protected String token;

     /**
     * Whether to export access logs to logs
     */
    protected String accesslog; //将向logger中输出访问日志，也可填写访问日志文件路径，直接把访问日志输出到指定文件

     /**
     * The protocol list the service will export with
     * Also see {@link #protocolIds}, only one of them will work.
     */
    protected List<ProtocolConfig> protocols; //服务将暴露的协议列表

    /**
     * The id list of protocols the service will export with
     * Also see {@link #protocols}, only one of them will work.
     */
    protected String protocolIds;

    // max allowed execute times
    private Integer executes; //最大请求并行数（服务提供者每服务每方法最大可并行执行请求数）

    /**
     * Whether to register
     */
    private Boolean register; //是否向注册中心注册服务

    /**
     * Warm up period
     */
    private Integer warmup;

    /**
     * The serialization type
     */
    private String serialization; //序列化方式

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

    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
    }

    public Boolean getExport() {
        return export;
    }

    public void setExport(Boolean export) {
        this.export = export;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    @Parameter(escaped = true) //文档链接，需要进行URL编码
    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getToken() {
        return token;
    }

    public void setToken(Boolean token) {
        if (token == null) {
            setToken((String) null);
        } else {
            setToken(String.valueOf(token));
        }
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    public Boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(Boolean dynamic) {
        this.dynamic = dynamic;
    }

    public List<ProtocolConfig> getProtocols() {
        return protocols;
    }

    @SuppressWarnings({"unchecked"})
    public void setProtocols(List<? extends ProtocolConfig> protocols) {
        this.protocols = (List<ProtocolConfig>) protocols;
    }

    public ProtocolConfig getProtocol() {
        return CollectionUtils.isEmpty(protocols) ? null : protocols.get(0);
    }

    public void setProtocol(ProtocolConfig protocol) {
        setProtocols(new ArrayList<>(Arrays.asList(protocol)));
    }

    @Parameter(excluded = true)
    public String getProtocolIds() {
        return protocolIds;
    }

    public void setProtocolIds(String protocolIds) {
        this.protocolIds = protocolIds;
    }

    public String getAccesslog() {
        return accesslog;
    }

    public void setAccesslog(Boolean accesslog) {
        if (accesslog == null) {
            setAccesslog((String) null);
        } else {
            setAccesslog(String.valueOf(accesslog));
        }
    }

    public void setAccesslog(String accesslog) {
        this.accesslog = accesslog;
    }

    public Integer getExecutes() {
        return executes;
    }

    public void setExecutes(Integer executes) {
        this.executes = executes;
    }

    @Override
    @Parameter(key = SERVICE_FILTER_KEY, append = true)
    public String getFilter() {
        return super.getFilter();
    }

    @Override
    @Parameter(key = EXPORTER_LISTENER_KEY, append = true)
    public String getListener() {
        return listener;
    }

    @Override
    public void setListener(String listener) {
        this.listener = listener;
    }

    public Boolean isRegister() {
        return register;
    }

    public void setRegister(Boolean register) {
        this.register = register;
    }

    public Integer getWarmup() {
        return warmup;
    }

    public void setWarmup(Integer warmup) {
        this.warmup = warmup;
    }

    public String getSerialization() {
        return serialization;
    }

    public void setSerialization(String serialization) {
        this.serialization = serialization;
    }
}
