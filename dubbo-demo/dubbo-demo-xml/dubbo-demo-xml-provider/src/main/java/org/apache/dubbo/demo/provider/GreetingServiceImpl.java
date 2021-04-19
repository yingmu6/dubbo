package org.apache.dubbo.demo.provider;

import org.apache.dubbo.demo.GreetingService;

/**
 * @author chensy
 * @date 2021/4/19
 */
public class GreetingServiceImpl implements GreetingService {
    @Override
    public String hello() {
        System.out.println("你好 Greeting!");
        return "hello GreetingServiceImpl";
    }
}
