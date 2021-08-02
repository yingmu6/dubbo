package org.apache.dubbo.demo.provider;

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
        System.out.println("你好 Greeting!");
        return "hello GreetingServiceImpl";
    }

    @Override
    public String hello(String msg) {
        return "hello " + msg;
    }

    @Override
    public String hello(Integer num) {
        return "hello " + num;
    }

    @Override
    public String hello(Fruit fruit) {
        return fruit.getWeight() + ";" + fruit.getPrice();
    }

    @Override
    public String hello(FruitEnum fruitEnum) {
        return fruitEnum.getCategory() + ":" + fruitEnum.getPrice();
    }
}
