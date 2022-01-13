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
package org.apache.dubbo.qos.command;

import org.apache.dubbo.common.extension.SPI;

@SPI
public interface BaseCommand { //该命令的用途是啥？都有哪些的？什么是QOS？ 解答：QOS命令用于在线运维
    /**
     * QoS的英文全称为"Quality [ˈkwɒləti] of Service",中文名为"服务质量"。在dubbo2.5.8新版本增加了QOS模块，提供了新的telnet命令支持。
     * Dubbo管它叫在线运维命令，我们可以通过它能够看到服务提供者状态，服务调用者状态，现在dubbo提供了ls， online，offline，help ，quit命令
     * <p>
     * Telnet命令也可以对服务治理的，https://dubbo.apache.org/zh/docs/references/telnet/ telnet官网使用
     * <p>
     * https://blog.csdn.net/yuanshangshenghuo/article/details/107563319  解析dubbo在线运维Qos
     * https://dubbo.apache.org/zh/docs/references/qos/ 官网使用手册
     */
    String execute(CommandContext commandContext, String[] args);

    /**
     * todo @csy QOS问题点
     * 1）当提供者关闭时，控制台会抛出"Connection closed by foreign host." ，这个信息是哪里打出来的？通道关闭事件，是哪里监听的？
     */
}
