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
package org.apache.dubbo.metadata.report;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_DIRECTORY;
import static org.apache.dubbo.metadata.report.support.Constants.METADATA_REPORT_KEY;

/**
 * 2019-08-09
 */
public class MetadataReportInstance { //该类的用途是？解：用来保存MetadataReport实例

    private static AtomicBoolean init = new AtomicBoolean(false);

    private static MetadataReport metadataReport;

    public static void init(URL metadataReportURL) {
        if (init.get()) { //若已经初始化，则不再初始化操作
            return;
        }

        // 获取或创建MetadataReportFactory自适应类的实例
        MetadataReportFactory metadataReportFactory = ExtensionLoader.getExtensionLoader(MetadataReportFactory.class).getAdaptiveExtension();
        if (METADATA_REPORT_KEY.equals(metadataReportURL.getProtocol())) { //若协议为"metadata"，则对metadataReportURL的protocol进行修改
            String protocol = metadataReportURL.getParameter(METADATA_REPORT_KEY, DEFAULT_DIRECTORY);
            metadataReportURL = URLBuilder.from(metadataReportURL)
                    .setProtocol(protocol)
                    .removeParameter(METADATA_REPORT_KEY)
                    .build();
        }

        //在具体方法调用时，会获取到扩展名，执行对应扩展实例的方法，达到多态的目的，此处MetadataReportFactory的注解为@Adaptive({"protocol"})，所以会取metadataReportURL.getProtocol()的值
        metadataReport = metadataReportFactory.getMetadataReport(metadataReportURL);
        init.set(true); //初始化以后，更改状态值
    }

    public static MetadataReport getMetadataReport() {
        return getMetadataReport(false);
    }

    public static MetadataReport getMetadataReport(boolean checked) { //获取metadataReport实例对象
        if (checked) {
            checkInit();
        }
        return metadataReport;
    }

    private static void checkInit() {
        if (!init.get()) { //未初始化
            throw new IllegalStateException("the metadata report was not inited.");
        }
    }
}
