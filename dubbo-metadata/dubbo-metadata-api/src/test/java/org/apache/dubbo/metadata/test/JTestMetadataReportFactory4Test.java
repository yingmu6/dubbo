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
package org.apache.dubbo.metadata.test;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.support.AbstractMetadataReportFactory;

/**
 * ZookeeperRegistryFactory.
 */
public class JTestMetadataReportFactory4Test extends AbstractMetadataReportFactory { //调用getMetadataReport()方法时，会调用从AbstractMetadataReportFactory继承的方法

    @Override
    public MetadataReport createMetadataReport(URL url) {
        return new JTestMetadataReport4Test(url);
    }

    @Override
    public MetadataReport getMetadataReport(URL url) { //原本没有，自己重写（证明的内容：调用自适应类时，会在运行时根据字节码技术产生自适应类，然后在自适应类中根据url中设置的参数值，动态获取指定扩展名对应的实例来执行）
        return super.getMetadataReport(url);
    }

}
