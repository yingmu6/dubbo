package org.apache.dubbo.metadata.report;

/**
 * MetadataReportFactory对应的自适应代码
 * （可以进行debug）
 *
 * @author chensy
 * @date 2022/6/1
 */

import org.apache.dubbo.common.extension.ExtensionLoader;

public class MetadataReportFactory$Adaptive implements org.apache.dubbo.metadata.report.MetadataReportFactory {
    public org.apache.dubbo.metadata.report.MetadataReport getMetadataReport(org.apache.dubbo.common.URL arg0) {
        if (arg0 == null) {
            throw new IllegalArgumentException("url == null");
        }
        org.apache.dubbo.common.URL url = arg0;
        // 在方法调用时，找到扩展名，找到对应的实例，然后再执行对应的方法，实现类的多态
        String extName = (url.getProtocol() == null ? "redis" : url.getProtocol());
        if (extName == null) {
            throw new IllegalStateException("Failed to get extension (org.apache.dubbo.metadata.report.MetadataReportFactory) name from url (" + url.toString() + ") use keys([protocol])");
        }
        org.apache.dubbo.metadata.report.MetadataReportFactory extension = (org.apache.dubbo.metadata.report.MetadataReportFactory) ExtensionLoader.getExtensionLoader(org.apache.dubbo.metadata.report.MetadataReportFactory.class).getExtension(extName);
        return extension.getMetadataReport(arg0);
    }
}
