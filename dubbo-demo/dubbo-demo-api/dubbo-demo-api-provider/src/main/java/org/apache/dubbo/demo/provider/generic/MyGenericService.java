package org.apache.dubbo.demo.provider.generic;

import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;

import java.util.concurrent.CompletableFuture;

/**
 * @author chensy
 * @date 2021/10/19
 */
public class MyGenericService implements GenericService {

    @Override
    public Object $invoke(String method, String[] parameterTypes, Object[] args) throws GenericException {
        return null;
    }

    @Override
    public CompletableFuture<Object> $invokeAsync(String method, String[] parameterTypes, Object[] args) throws GenericException {
        return GenericService.super.$invokeAsync(method, parameterTypes, args);
    }
}
