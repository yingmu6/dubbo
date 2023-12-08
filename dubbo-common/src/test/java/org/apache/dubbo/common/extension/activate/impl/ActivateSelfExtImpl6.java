package org.apache.dubbo.common.extension.activate.impl;

import org.apache.dubbo.common.extension.activate.ActivateSelfExt;

/**
 * @Author chenSy
 * @Date 2023/05/11 22:33
 * @Description
 */
public class ActivateSelfExtImpl6 implements ActivateSelfExt {

    @Override
    public String echo(String msg) {
        return "ActivateSelfExtImpl6: " + msg;
    }
}
