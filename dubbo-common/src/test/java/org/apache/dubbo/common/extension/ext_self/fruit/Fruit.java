package org.apache.dubbo.common.extension.ext_self.fruit;

import org.apache.dubbo.common.extension.SPI;

/**
 * @author chensy
 * @date 2021/9/3
 */
@SPI
public interface Fruit {
    String getFruitName();

    Double getFruitPrice();
}