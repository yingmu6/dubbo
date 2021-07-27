package org.apache.dubbo.demo;

/**
 * @author chensy
 * @date 2021/7/27
 */
public enum FruitEnum {
    APPLE("苹果", 10.0),
    PEER("梨", 12.0);

    private String name;
    private Double price;

    private FruitEnum(String name, Double price) {
        this.name = name;
        this.price = price;
    }


}
