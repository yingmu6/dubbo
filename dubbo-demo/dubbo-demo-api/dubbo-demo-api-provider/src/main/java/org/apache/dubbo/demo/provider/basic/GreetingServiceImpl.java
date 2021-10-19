package org.apache.dubbo.demo.provider.basic;

import org.apache.dubbo.demo.Fruit;
import org.apache.dubbo.demo.FruitEnum;
import org.apache.dubbo.demo.GreetingService;

/**
 * @author chensy
 * @date 2021/4/19
 */
public class GreetingServiceImpl implements GreetingService {
    @Override
    public String hello() {
        System.out.println("你好 Greeting! api");
        return "hello GreetingServiceImpl API";
    }

    @Override
    public String hello(String msg) {
        return "API " + msg;
    }

    @Override
    public String hello(Integer num) {
        return "API " + num;
    }

    @Override
    public String hello(Fruit fruit) {
        return "API" + fruit.getPrice();
    }

    @Override
    public String hello(FruitEnum fruitEnum) {
        return "API" + fruitEnum.name();
    }
}
