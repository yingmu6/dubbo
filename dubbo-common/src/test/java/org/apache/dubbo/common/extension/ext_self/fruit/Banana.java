package org.apache.dubbo.common.extension.ext_self.fruit;

import org.apache.dubbo.common.extension.Activate;

/**
 * @author chensy
 * @date 2021/9/3
 */
@Activate(group = "sweet")
public class Banana implements Fruit {

    @Override
    public String getFruitName() {
        return "香蕉";
    }

    @Override
    public Double getFruitPrice() {
        return 1.5;
    }
}
