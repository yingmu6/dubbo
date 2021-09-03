package org.apache.dubbo.common.extension.ext_self.fruit;

import org.apache.dubbo.common.extension.Activate;

/**
 * @author chensy
 * @date 2021/9/3
 */
@Activate(group = "sour", value = "orange")
public class Orange implements Fruit {

    @Override
    public String getFruitName() {
        return "橘子";
    }

    @Override
    public Double getFruitPrice() {
        return 3.5;
    }
}
