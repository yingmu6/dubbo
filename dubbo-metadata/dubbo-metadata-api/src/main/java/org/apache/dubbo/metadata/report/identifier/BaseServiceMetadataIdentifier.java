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
import org.apache.dubbo.common.utils.StringUtils;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;
import static org.apache.dubbo.metadata.MetadataConstants.DEFAULT_PATH_TAG;
import static org.apache.dubbo.metadata.MetadataConstants.KEY_SEPARATOR;

/**
 * The Base class of MetadataIdentifier for service scope
 * <p>
 * 2019-08-09
 */
public class BaseServiceMetadataIdentifier {//基础服务元数据标识符
    /**
     * 标识符指定是啥？
     * 解：包含服务接口、版本、分组、所属方等信息
     */
    String serviceInterface;
    String version;
    String group;
    String side;

    String getUniqueKey(KeyTypeEnum keyType, String... params) {
        if (keyType == KeyTypeEnum.PATH) {
            return getFilePathKey(params);
        }
        return getIdentifierKey(params); //是怎样拼接唯一key的？解：将基础信息与参数列表，按照分隔符进行拼接，值如：org.apache.dubbo.metadata.store.InterfaceNameTestService:1.0.3::provider:vicpubp
    }

    String getIdentifierKey(String... params) { //组装元数据的标识符键

        return serviceInterface
                + KEY_SEPARATOR + (version == null ? "" : version)
                + KEY_SEPARATOR + (group == null ? "" : group)
                + KEY_SEPARATOR + (side == null ? "" : side)
                + joinParams(KEY_SEPARATOR, params); //值如：org.apache.dubbo.metadata.integration.InterfaceNameTestService:1.0.0.zk.md::provider:vic.zk.md
    }

    private String joinParams(String joinChar, String... params) { //将分隔符与参数列表进行拼接
        if (params == null || params.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String param : params) { //遍历参数，依次将分隔符与参数进行拼接
            if (param == null) {
                continue;
            }
            sb.append(joinChar);
            sb.append(param);
        }
        return sb.toString();
    }

    private String getFilePathKey(String... params) {
        return getFilePathKey(DEFAULT_PATH_TAG, params);
    }

    private String getFilePathKey(String pathTag, String... params) { //文件路径拼接
        return pathTag
                + (StringUtils.isEmpty(toServicePath()) ? "" : (PATH_SEPARATOR + toServicePath()))
                + (version == null ? "" : (PATH_SEPARATOR + version))
                + (group == null ? "" : (PATH_SEPARATOR + group))
                + (side == null ? "" : (PATH_SEPARATOR + side))
                + joinParams(PATH_SEPARATOR, params);
    }

    public String toServicePath() {
        if (ANY_VALUE.equals(serviceInterface)) {
            return "";
        }
        return URL.encode(serviceInterface);
    }
}
