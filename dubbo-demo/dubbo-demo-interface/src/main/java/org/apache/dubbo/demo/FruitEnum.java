package org.apache.dubbo.demo;

/**
 * @author chensy
 * @date 2021/7/27
 */
public enum FruitEnum {
    APPLE("apple", 10.0),
    PEER("peer", 12.0);

    private String category;
    private Double price;

    private FruitEnum(String category, Double price) {
        this.category = category;
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public Double getPrice() {
        return price;
    }
}
