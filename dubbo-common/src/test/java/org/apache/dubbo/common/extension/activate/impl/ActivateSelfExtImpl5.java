package org.apache.dubbo.common.extension.activate.impl;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.activate.ActivateSelfExt;

/**
 * @Author chenSy
 * @Date 2023/05/11 22:33
 * @Description
 */
@Activate(value = {"name:zhang","age:13"}, group = "self_group")
public class ActivateSelfExtImpl5 implements ActivateSelfExt {

    @Override
    public String echo(String msg) {
        return "ActivateSelfExtImpl5: " + msg;
    }
}
