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
    public void setMsg(String msg) {

    }

    @Override
    public String getMsg() {
        return null;
    }

    @Override
    public String hello() {
        System.out.println("你好 Greeting!");
        return "hello GreetingServiceImpl";
    }

    @Override
    public String hello(String msg) throws InterruptedException {
//        try {
//            Thread.sleep(3000);
//        } catch (Exception e) {
//            System.out.println("异常：" + e.getMessage());
//        }
        return "hello222 " + msg;
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
