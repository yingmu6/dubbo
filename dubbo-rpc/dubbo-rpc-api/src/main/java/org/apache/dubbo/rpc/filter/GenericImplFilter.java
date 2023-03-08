/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.beanutil.JavaBeanAccessor;
import org.apache.dubbo.common.beanutil.JavaBeanDescriptor;
import org.apache.dubbo.common.beanutil.JavaBeanSerializeUtil;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.PojoUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;

/**
 * GenericImplInvokerFilter
 */
@Activate(group = CommonConstants.CONSUMER, value = GENERIC_KEY, order = 20000)
public class GenericImplFilter implements Filter, Filter.Listener { //实现消费端的泛化功能

    private static final Logger logger = LoggerFactory.getLogger(GenericImplFilter.class);

    private static final Class<?>[] GENERIC_PARAMETER_TYPES = new Class<?>[] {String.class, String[].class, Object[].class}; // 泛化方法$invoke 对应的参数Class列表

    private static final String GENERIC_IMPL_MARKER = "GENERIC_IMPL";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String generic = invoker.getUrl().getParameter(GENERIC_KEY); //获取泛化方式
        // calling a generic impl service
        if (isCallingGenericImpl(generic, invocation)) { //泛化接口GenericService的实现类处理（非$invoke或$invokeAsync方法调用，即泛化实现类新增的方法）
            RpcInvocation invocation2 = new RpcInvocation(invocation);

            /**
             * Mark this invocation as a generic impl call, this value will be removed automatically before passing on the wire.
             * See {@link RpcUtils#sieveUnnecessaryAttachments(Invocation)}
             */
            invocation2.put(GENERIC_IMPL_MARKER, true); //设置泛化实现的标志

            String methodName = invocation2.getMethodName();
            Class<?>[] parameterTypes = invocation2.getParameterTypes();
            Object[] arguments = invocation2.getArguments();

            String[] types = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                types[i] = ReflectUtils.getName(parameterTypes[i]); //获取class对象对应的描述信息
            }

            Object[] args;
            if (ProtocolUtils.isBeanGenericSerialization(generic)) { //泛化类型为：bean，使用JavaBeanSerializeUtil进行序列化
                args = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    args[i] = JavaBeanSerializeUtil.serialize(arguments[i], JavaBeanAccessor.METHOD);
                }
            } else { //其它类型用PojoUtils对参数列表直接进行序列化
                args = PojoUtils.generalize(arguments);
            }

            if (RpcUtils.isReturnTypeFuture(invocation)) {
                invocation2.setMethodName($INVOKE_ASYNC); //GenericService中的异步调用$invokeAsync
            } else {
                invocation2.setMethodName($INVOKE); //GenericService中的同步调用$invoke
            }
            invocation2.setParameterTypes(GENERIC_PARAMETER_TYPES); //调用参数类型（即GenericService的$invoke方法的参数类型列表）
            invocation2.setParameterTypesDesc(GENERIC_PARAMETER_DESC); //调用参数描述
            invocation2.setArguments(new Object[] {methodName, types, args}); //设置泛化接口方法中的参数列表
            return invoker.invoke(invocation2);
        }
        // making a generic call to a normal service
        else if (isMakingGenericCall(generic, invocation)) { //泛化调用：用于服务消费端泛化（$invoke或$invokeAsync方法调用）

            Object[] args = (Object[]) invocation.getArguments()[2];
            if (ProtocolUtils.isJavaGenericSerialization(generic)) { //序列化方式：nativejava

                for (Object arg : args) {
                    if (!(byte[].class == arg.getClass())) { // 每一个参数类型需要字节数组
                        error(generic, byte[].class.getName(), arg.getClass().getName());
                    }
                }
            } else if (ProtocolUtils.isBeanGenericSerialization(generic)) {//序列化方式：bean
                for (Object arg : args) {
                    if (!(arg instanceof JavaBeanDescriptor)) { //对每一个参数类型进行判断，需要JavaBeanDescriptor类型
                        error(generic, JavaBeanDescriptor.class.getName(), arg.getClass().getName());
                    }
                }
            }

            invocation.setAttachment(
                    GENERIC_KEY, invoker.getUrl().getParameter(GENERIC_KEY)); //设置泛化类型
        }
        return invoker.invoke(invocation); //过滤逻辑处理完后，执行方法调用
    }

    private void error(String generic, String expected, String actual) throws RpcException {
        throw new RpcException("Generic serialization [" + generic + "] only support message type " + expected + " and your message type is " + actual);
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        String generic = invoker.getUrl().getParameter(GENERIC_KEY); //获取泛化方式
        String methodName = invocation.getMethodName();
        Class<?>[] parameterTypes = invocation.getParameterTypes();
        Object genericImplMarker = invocation.get(GENERIC_IMPL_MARKER); //获取泛化标识
        if (genericImplMarker != null && (boolean) invocation.get(GENERIC_IMPL_MARKER)) { //非$invoke或非$invokeAsync方法的调用
            if (!appResponse.hasException()) { //响应没有异常信息
                Object value = appResponse.getValue(); //获取响应结果
                try {
                    Class<?> invokerInterface = invoker.getInterface();
                    if (!$INVOKE.equals(methodName) && !$INVOKE_ASYNC.equals(methodName)
                            && invokerInterface.isAssignableFrom(GenericService.class)) {
                        try {
                            // find the real interface from url
                            String realInterface = invoker.getUrl().getParameter(Constants.INTERFACE); //查找真实的调用接口
                            invokerInterface = ReflectUtils.forName(realInterface);
                        } catch (Throwable e) {
                            // ignore
                        }
                    }

                    Method method = invokerInterface.getMethod(methodName, parameterTypes);
                    if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                        if (value == null) {
                            appResponse.setValue(value);
                        } else if (value instanceof JavaBeanDescriptor) { //反序列化，并将值设置到响应结果appResponse中
                            appResponse.setValue(JavaBeanSerializeUtil.deserialize((JavaBeanDescriptor) value));
                        } else {
                            throw new RpcException("The type of result value is " + value.getClass().getName() + " other than " + JavaBeanDescriptor.class.getName() + ", and the result is " + value);
                        }
                    } else {
                        Type[] types = ReflectUtils.getReturnTypes(method);
                        appResponse.setValue(PojoUtils.realize(value, (Class<?>) types[0], types[1])); //将响应结果值反序列化后，设置到Result中
                    }
                } catch (NoSuchMethodException e) {
                    throw new RpcException(e.getMessage(), e);
                }
            } else if (appResponse.getException() instanceof com.alibaba.dubbo.rpc.service.GenericException) { //响应包含异常信息
                com.alibaba.dubbo.rpc.service.GenericException exception = (com.alibaba.dubbo.rpc.service.GenericException) appResponse.getException();
                try {
                    String className = exception.getExceptionClass();
                    Class<?> clazz = ReflectUtils.forName(className);
                    Throwable targetException = null;
                    Throwable lastException = null;
                    try {
                        targetException = (Throwable) clazz.newInstance(); //构建异常实例
                    } catch (Throwable e) {
                        lastException = e;
                        for (Constructor<?> constructor : clazz.getConstructors()) {
                            try {
                                targetException = (Throwable) constructor.newInstance(new Object[constructor.getParameterTypes().length]);
                                break;
                            } catch (Throwable e1) {
                                lastException = e1;
                            }
                        }
                    }
                    if (targetException != null) {
                        try {
                            Field field = Throwable.class.getDeclaredField("detailMessage");
                            if (!field.isAccessible()) {
                                field.setAccessible(true);
                            }
                            field.set(targetException, exception.getExceptionMessage());
                        } catch (Throwable e) {
                            logger.warn(e.getMessage(), e);
                        }
                        appResponse.setException(targetException); //在响应结果中appResponse设置异常信息
                    } else if (lastException != null) {
                        throw lastException;
                    }
                } catch (Throwable e) {
                    throw new RpcException("Can not deserialize exception " + exception.getExceptionClass() + ", message: " + exception.getExceptionMessage(), e);
                }
            }
        }
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {

    }

    private boolean isCallingGenericImpl(String generic, Invocation invocation) { //泛化调用中 非$invoke或非$invokeAsync方法
        return ProtocolUtils.isGeneric(generic) //是泛化类型
                && (!$INVOKE.equals(invocation.getMethodName()) && !$INVOKE_ASYNC.equals(invocation.getMethodName())) //方法名不为$invoke且不为$invokeAsync
                && invocation instanceof RpcInvocation; //invocation类型为RpcInvocation
    }

    private boolean isMakingGenericCall(String generic, Invocation invocation) { //泛化调用中 $invoke或$invokeAsync方法
        return (invocation.getMethodName().equals($INVOKE) || invocation.getMethodName().equals($INVOKE_ASYNC)) //方法名为$invoke或为$invokeAsync
                && invocation.getArguments() != null
                && invocation.getArguments().length == 3
                && ProtocolUtils.isGeneric(generic);
    }

}
