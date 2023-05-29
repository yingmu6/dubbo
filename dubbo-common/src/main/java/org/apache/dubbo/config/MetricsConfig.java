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

import org.apache.dubbo.config.support.Parameter;

import static org.apache.dubbo.common.constants.CommonConstants.METRICS_PORT;
import static org.apache.dubbo.common.constants.CommonConstants.METRICS_PROTOCOL;

public class MetricsConfig extends AbstractConfig { //metrics：指标，度量

    /**
     * Metrics概要：
     * a）对服务进行实时监控，了解服务当前的运行指标和健康状态，是微服务体系中不可或缺的环节。Metrics 作为微服务的重要组件，为服务的监控提供了全面的数据基础。
     * b）
     *
     * 参考链接：
     * a）https://developer.aliyun.com/article/693569 Dubbo的度量统计基础设施
     * b）https://cn.dubbo.apache.org/zh-cn/overview/reference/proposals/metrics/ dubbo官网
     */

    private static final long serialVersionUID = -9089919311611546383L;

    private String port;
    private String protocol;

    public MetricsConfig() {
    }

    @Parameter(key = METRICS_PORT)
    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    @Parameter(key = METRICS_PROTOCOL)
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

}
