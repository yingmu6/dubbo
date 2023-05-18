package org.apache.dubbo.common.extension.adaptive;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

// 产生的自适应类（仅做展示使用，实际方法调用时，不会进入到这里面）
public class HasAdaptiveExt$Adaptive implements org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt {
    public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1) {
        if (arg0 == null) {
            throw new IllegalArgumentException("url == null");
        }
        org.apache.dubbo.common.URL url = arg0;
        // 根据url中的配置，动态设置扩展名
        String extName = url.getParameter("has.adaptive.ext", "adaptive"); //HasAdaptiveExt中并没有指定默认扩展名和查询的url的key，是按什么规则查询到的？解答：若没有配置value值，会将SPI接口转换为带有分隔符的字符串作为value值，在AdaptiveClassCodeGenerator#getMethodAdaptiveValue处理的
        if (extName == null) {
            throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt) name from url (" + url.toString() + ") use keys([has.adaptive.ext])");
        }
        org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt extension = (org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt) ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt.class).getExtension(extName);
        return extension.echo(arg0, arg1);
    }

    @Override
    public java.lang.String echoV2(org.apache.dubbo.common.URL arg0, java.lang.String arg1)  {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("adaptive", url.getParameter("impl", "adaptive")); //@Adaptive配置多个值时，会从左到由，依次从url获取参数
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt) name from url (" + url.toString() + ") use keys([adaptive, impl])");
        org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt extension = (org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt)ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt.class).getExtension(extName);
        return extension.echoV2(arg0, arg1);
    }
}