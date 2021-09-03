package org.apache.dubbo.demo.provider;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.demo.DemoSpiService;

/**
 * @author chensy
 * @date 2021/9/3
 */
public class DemoSpiFirstServiceImpl implements DemoSpiService {
    @Override
    public String sayHello(URL url) {
        return "SPI第一个实现类";
    }
}
