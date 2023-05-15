package org.apache.dubbo.common.extension.activate.impl;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.activate.ActivateSelfExt;

/**
 * @Author chenSy
 * @Date 2023/05/11 22:33
 * @Description
 */
@Activate(value = {"sysExt"}, group = "self_group")
public class ActivateSelfExtImpl2 implements ActivateSelfExt {

    @Override
    public String echo(String msg) {
        return "ActivateSelfExtImpl2: " + msg;
    }
}
