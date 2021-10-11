package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.demo.DemoService;

/**
 * @author chensy
 * @date 2021/10/11
 */
public class DemoServiceMock implements DemoService {
    @Override
    public String sayHello(String name) {
        return "Mock数据1";
    }

    @Override
    public String sayHello2(String name) {
        return "Mock的数据2";
    }
}
