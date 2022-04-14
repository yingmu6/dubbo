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
package org.apache.dubbo.common.config.configcenter;

import java.util.EventObject;
import java.util.Objects;

/**
 * An event raised（提高） when the config changed, immutable. (当配置改变时引发的事件，不可变)
 *
 * @see ConfigChangeType
 */
public class ConfigChangedEvent extends EventObject { //配置变更事件对象，EventObject：所有事件对象的基类

    private final String key;

    private final String group;

    private final String content;

    private final ConfigChangeType changeType;

    public ConfigChangedEvent(String key, String group, String content) {
        this(key, group, content, ConfigChangeType.MODIFIED);
    }

    public ConfigChangedEvent(String key, String group, String content, ConfigChangeType changeType) {
        super(key + "," + group); //将key、group拼接，设置发生事件的对象
        this.key = key;
        this.group = group; //分组：用来隔离配置，比如使用Apollo时，设置的为namespace
        this.content = content;
        this.changeType = changeType;
    }

    public String getKey() {
        return key;
    }

    public String getGroup() {
        return group;
    }

    public String getContent() {
        return content;
    }

    public ConfigChangeType getChangeType() {
        return changeType;
    }

    @Override
    public String toString() { //重写了字符串打印
        return "ConfigChangedEvent{" +
                "key='" + key + '\'' +
                ", group='" + group + '\'' +
                ", content='" + content + '\'' +
                ", changeType=" + changeType +
                "} " + super.toString();
    }

    @Override
    public boolean equals(Object o) { //重写了比较逻辑
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConfigChangedEvent)) { //若比较的对象不是ConfigChangedEvent类型，则不进行处理
            return false;
        }
        ConfigChangedEvent that = (ConfigChangedEvent) o;
        return Objects.equals(getKey(), that.getKey()) &&
                Objects.equals(getGroup(), that.getGroup()) &&
                Objects.equals(getContent(), that.getContent()) &&
                getChangeType() == that.getChangeType(); //重写比较方法，将key、group、content、changeType作为比较的条件
    }

    @Override
    public int hashCode() { //重写hashCode()方法，按照指定的属性进行计算
        return Objects.hash(getKey(), getGroup(), getContent(), getChangeType());
    }
}
