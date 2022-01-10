package org.apache.dubbo.common.extension.ext1;

/**
 * @author chensy
 * @date 2022/1/7
 */

import org.apache.dubbo.common.extension.ExtensionLoader;

public class SimpleExt$Adaptive implements org.apache.dubbo.common.extension.ext1.SimpleExt { // 产生的自适应代码
    public java.lang.String yell(org.apache.dubbo.common.URL arg0, java.lang.String arg1) {
        if (arg0 == null) {
            throw new IllegalArgumentException("url == null");
        }
        org.apache.dubbo.common.URL url = arg0;
        // 查找扩展名，把@Adaptive中设置的值，作为url参数key查找，如@Adaptive({"key1", "key2"})，会先查找url中key1的参数，若没找到再找key2的值，还没找到，就找SPI里声明的值，如@SPI("impl1")
        String extName = url.getParameter("key1", url.getParameter("key2", "impl1"));
        if (extName == null) { //若在url参数Map中没有找到对应key的值，就抛出异常
            throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext1.SimpleExt) name from url (" + url.toString() + ") use keys([key1, key2])");
        }
        // 找到扩展名，获取指定扩展名对应的扩展实例，然后再执行对应的方法
        org.apache.dubbo.common.extension.ext1.SimpleExt extension = (org.apache.dubbo.common.extension.ext1.SimpleExt) ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext1.SimpleExt.class).getExtension(extName);
        return extension.yell(arg0, arg1);
    }

    public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1) {
        if (arg0 == null) { //flag3
            throw new IllegalArgumentException("url == null");
        }
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("simple.ext", "impl1");
        if (extName == null) {
            throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext1.SimpleExt) name from url (" + url.toString() + ") use keys([simple.ext])");
        }
        org.apache.dubbo.common.extension.ext1.SimpleExt extension = (org.apache.dubbo.common.extension.ext1.SimpleExt) ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext1.SimpleExt.class).getExtension(extName);
        return extension.echo(arg0, arg1);
    }

    public java.lang.String bang(org.apache.dubbo.common.URL arg0, int arg1) {
        throw new UnsupportedOperationException("The method public abstract java.lang.String org.apache.dubbo.common.extension.ext1.SimpleExt.bang(org.apache.dubbo.common.URL,int) of interface org.apache.dubbo.common.extension.ext1.SimpleExt is not adaptive method!");
    }
}
