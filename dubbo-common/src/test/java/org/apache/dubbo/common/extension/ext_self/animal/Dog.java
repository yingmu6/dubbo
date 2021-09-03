package org.apache.dubbo.common.extension.ext_self.animal;

import org.apache.dubbo.common.URL;

/**
 * @author chensy
 * @date 2021/9/3
 */
public class Dog implements Animal {

    @Override
    public String cry(URL url) {
        return "狗叫";
    }
}
