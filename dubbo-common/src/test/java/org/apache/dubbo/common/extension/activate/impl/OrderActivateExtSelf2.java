package org.apache.dubbo.common.extension.activate.impl;

import org.apache.dubbo.common.extension.activate.ActivateExt1;

/**
 * 自定义自动激活类（带有@Activate注解）
 */
public class OrderActivateExtSelf2 implements ActivateExt1 {
    @Override
    public String echo(String msg) {
        return "echo self 2";
    }
}
