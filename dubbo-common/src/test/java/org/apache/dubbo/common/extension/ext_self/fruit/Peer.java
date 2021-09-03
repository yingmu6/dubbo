package org.apache.dubbo.common.extension.ext_self.fruit;

import org.apache.dubbo.common.extension.Activate;

/**
 * @author chensy
 * @date 2021/9/3
 */
@Activate(group = "sweet", value = "peer")
public class Peer implements Fruit {
    @Override
    public String getFruitName() {
        return "梨";
    }

    @Override
    public Double getFruitPrice() {
        return 4.5;
    }
}
