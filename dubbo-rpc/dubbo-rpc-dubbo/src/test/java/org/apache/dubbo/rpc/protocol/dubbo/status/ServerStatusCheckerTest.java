package org.apache.dubbo.rpc.protocol.dubbo.status;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.status.Status;
import org.apache.dubbo.common.status.StatusChecker;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.protocol.dubbo.support.DemoService;
import org.apache.dubbo.rpc.protocol.dubbo.support.DemoServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * @author chensy
 * @date 2021/8/12
 */
public class ServerStatusCheckerTest {

    private Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();


    @Test
    public void testStatus() {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();

        protocol.export(
                proxy.getInvoker(
                        service,
                        DemoService.class,
                        URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange")
                )
        );

//        protocol.destroy();

        StatusChecker statusChecker = ExtensionLoader.getExtensionLoader(StatusChecker.class).getExtension("server");
        Status status = statusChecker.check();
        System.out.println(status.getLevel());
    }

    @Test
    public void testLoadClass() throws IOException {
        String dir = "META-INF/dubbo/internal/";
        String type = StatusChecker.class.getName();
        String fileName = dir + type;
        ClassLoader classLoader = ClassUtils.getClassLoader(ExtensionLoader.class);
        Enumeration<java.net.URL> urls = classLoader.getResources(fileName);
        while (urls.hasMoreElements()) {
            loadResource(urls.nextElement());
        }

    }

    private void loadResource(java.net.URL resourceURL) {
        try { //资源放在try里面创建，不使用时会自动被释放
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {//读取每一行，对每一行进行解析
                    System.out.println("读取内容：" + line);
                }
            }
        } catch (Throwable t) {

        }
    }
}
