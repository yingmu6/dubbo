package org.apache.dubbo.common.extension.ext8_add;
import org.apache.dubbo.common.extension.ExtensionLoader;
public class AddExt1$Adaptive implements org.apache.dubbo.common.extension.ext8_add.AddExt1 {
    public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1)  {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("add.ext1", "impl1");
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext8_add.AddExt1) name from url (" + url.toString() + ") use keys([add.ext1])");
        org.apache.dubbo.common.extension.ext8_add.AddExt1 extension = (org.apache.dubbo.common.extension.ext8_add.AddExt1)ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext8_add.AddExt1.class).getExtension(extName);
        return extension.echo(arg0, arg1);
    }
}