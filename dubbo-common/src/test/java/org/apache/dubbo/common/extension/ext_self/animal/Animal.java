package org.apache.dubbo.common.extension.ext_self.animal;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * @author chensy
 * @date 2021/9/3
 */
@SPI
public interface Animal {
    @Adaptive
    String cry(URL url);
}