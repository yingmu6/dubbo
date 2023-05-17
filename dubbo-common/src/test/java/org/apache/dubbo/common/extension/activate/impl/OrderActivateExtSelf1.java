package org.apache.dubbo.common.extension.activate.impl;

import org.apache.dubbo.common.extension.activate.ActivateExt1;

/**
 * 自定义自动激活类（没有带@Activate注解）
 */
public class OrderActivateExtSelf1 implements ActivateExt1 {
    @Override
    public String echo(String msg) {
        return "echo self 1";
    }
}
