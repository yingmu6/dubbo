package org.apache.dubbo.demo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * @author chensy
 * @date 2021/9/3
 */
@SPI
public interface DemoSpiService {
    @Adaptive
    String sayHello(URL url);
}