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

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import static org.apache.dubbo.common.constants.CommonConstants.*;

/**
 * AbstractConsumerConfig
 *
 * @export
 * @see ReferenceConfigBase
 */
public abstract class AbstractReferenceConfig extends AbstractInterfaceConfig {

    private static final long serialVersionUID = -2786526984373031126L;

    // ======== Reference config default values, will take effect if reference's attribute is not set  ========

    /**
     * Check if service provider exists, if not exists, it will be fast fail（服务不存在，会快速失败）
     * 相关问题点
     * 1）服务检查会在哪里使用到的，是怎么做失败处理的？
     * 解答：
     * <p>
     * 2）check的默认值为什么是true？java哪种情况下，会对变量设置默认值
     * 解：ReferenceConfigBase#shouldCheck()此方法中会对check设置默认值；java会对静态变量设置默认值
     * <p>
     * 3）怎么判断服务是否可用的？为啥直连方式时，check设置没有生效？
     * 4）目前观察到的情况是，服务引用是check=true时，只要不进行服务调用，就不会报错，启动是不报错，那为啥叫启动时做检查，
     * 从现象来看应该是调用时检查吧？
     */
    protected Boolean check;

    /**
     * Whether to eagle-init
     */
    protected Boolean init; //是否为惰性初始化

     /**
     * Whether to use generic interface
     */
    protected String generic; //是否使用泛化接口

    /**
     * Whether to find reference's instance from the current JVM （当前JVM）
     */
    protected Boolean injvm;

     /**
     * Lazy create connection
     */
    protected Boolean lazy; //是否延迟创建连接

    protected String reconnect; //重连事件

    protected Boolean sticky = false; //是否粘连

    /**
     * Whether to support event in stub.
     */
    //TODO solve merge problem
    protected Boolean stubevent;//= Constants.DEFAULT_STUB_EVENT;

    /**
     * The remote service version the customer side will reference
     */
    protected String version; //服务的版本号

     /**
     * The remote service group the customer side will reference
     */
    protected String group; //服务的分组

    /**
     * declares which app or service this interface belongs to
     */
    protected String providedBy;

    protected String router; //服务路由

    public Boolean isCheck() {
        return check;
    }

    public void setCheck(Boolean check) {
        this.check = check;
    }

    public Boolean isInit() {
        return init;
    }

    public void setInit(Boolean init) {
        this.init = init;
    }

    @Deprecated
    @Parameter(excluded = true)
    public Boolean isGeneric() {
        return this.generic != null ? ProtocolUtils.isGeneric(generic) : null;
    }

    @Deprecated
    public void setGeneric(Boolean generic) {
        if (generic != null) {
            this.generic = generic.toString();
        }
    }

    public String getGeneric() {
        return generic;
    }

    public void setGeneric(String generic) {
        if (StringUtils.isEmpty(generic)) {
            return;
        }
        if (ProtocolUtils.isValidGenericValue(generic)) {
            this.generic = generic;
        } else {
            throw new IllegalArgumentException("Unsupported generic type " + generic);
        }
    }

    /**
     * @return
     * @deprecated instead, use the parameter <b>scope</> to judge if it's in jvm, scope=local
     */
    @Deprecated
    public Boolean isInjvm() { //用范围scope代替
        return injvm;
    }

    /**
     * @param injvm
     * @deprecated instead, use the parameter <b>scope</b> to judge if it's in jvm, scope=local
     */
    @Deprecated
    public void setInjvm(Boolean injvm) {
        this.injvm = injvm;
    }

    @Override
    @Parameter(key = REFERENCE_FILTER_KEY, append = true)
    public String getFilter() {
        return super.getFilter();
    }

    @Override
    @Parameter(key = INVOKER_LISTENER_KEY, append = true)
    public String getListener() {
        return super.getListener();
    }

    @Override
    public void setListener(String listener) {
        super.setListener(listener);
    }

    public Boolean getLazy() {
        return lazy;
    }

    public void setLazy(Boolean lazy) {
        this.lazy = lazy;
    }

    @Override
    public void setOnconnect(String onconnect) { // 设置连接事件
        if (onconnect != null && onconnect.length() > 0) {
            this.stubevent = true;
        }
        super.setOnconnect(onconnect);
    }

    @Override
    public void setOndisconnect(String ondisconnect) {
        if (ondisconnect != null && ondisconnect.length() > 0) {
            this.stubevent = true;
        }
        super.setOndisconnect(ondisconnect);
    }

    @Parameter(key = STUB_EVENT_KEY)
    public Boolean getStubevent() {
        return stubevent;
    }

    public String getReconnect() {
        return reconnect;
    }

    public void setReconnect(String reconnect) {
        this.reconnect = reconnect;
    }

    public Boolean getSticky() {
        return sticky;
    }

    public void setSticky(Boolean sticky) {
        this.sticky = sticky;
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

    @Parameter(key = "provided-by")
    public String getProvidedBy() {
        return providedBy;
    }

    public void setProvidedBy(String providedBy) {
        this.providedBy = providedBy;
    }

    @Parameter(key = "router", append = true) //添加参数时，将config中的router值与参数"router"值进行拼接
    public String getRouter() {
        return router;
    }

    public void setRouter(String router) {
        this.router = router;
    }
}
