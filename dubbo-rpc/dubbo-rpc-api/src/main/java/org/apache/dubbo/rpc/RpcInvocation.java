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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.MethodDescriptor;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceRepository;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;

/**
 * RPC Invocation.（RPC 调用信息）
 *
 * @serial Don't change the class name and properties.
 */

/**
 * Dubbo 的核心领域模型中：
 * 1）Protocol：语义上：是"协议"，领域模型上：是"服务域"，它是Invoker暴露和引用的主功能入口，它负责Invoker的生命周期管理。
 * 2）Invoker 语义上：是"调用者"，领域模型上：是"实体域"，它是Dubbo的核心模型，其它模型都向它靠扰，或转换成它，它代表一个可执行体，可向它发起 invoke 调用，它有可能是一个本地的实现，也可能是一个远程的实现，也可能一个集群实现。
 * 3）Invocation 语义上：是"调用者"，领域模型上：是"会话域"，它持有调用过程中的变量，比如方法名，参数等。
 */
public class RpcInvocation implements Invocation, Serializable { //RpcInvocation的功能用途是什么？解：用来存储每次调用的信息
    /**
     * @csy-02-28 待画出数据结构和类图，解：已画出
     * 包含的信息：调用的接口名、方法名、参数类型列表、参数值列表、返回类型列表、附加参数等信息
     */

    private static final long serialVersionUID = -4355285085441097045L;

    private String targetServiceUniqueName; //目标服务名称

    private String methodName; //调用的方法名
    private String serviceName; //调用的服务名

    private transient Class<?>[] parameterTypes;
    private String parameterTypesDesc;
    private String[] compatibleParamSignatures; //此处属性的值是怎样的？解：值为参数的class名称，如"java.lang.String"

    private Object[] arguments;

    /**
     * Passed to（传递给） the remote server during RPC call
     */
    private Map<String, Object> attachments; //在RPC调用期间传递到远程服务器

    /**
     * Only used on the caller side（调用端）, will not appear on the wire（导线）.
     */
    private Map<Object, Object> attributes = new HashMap<Object, Object>(); //仅仅用在调用方，不会传递到远端（类似元数据处理方式：核心数据发送到远端，其它数据不传，而是传到元数据中心）

    private transient Invoker<?> invoker; //调用的实体

    private transient Class<?> returnType;

    private transient Type[] returnTypes;

    private transient InvokeMode invokeMode; //调用模式

    public RpcInvocation() {
    }

    public RpcInvocation(Invocation invocation, Invoker<?> invoker) {
        this(invocation.getMethodName(), invocation.getServiceName(), invocation.getParameterTypes(),
                invocation.getArguments(), new HashMap<>(invocation.getObjectAttachments()),
                invocation.getInvoker(), invocation.getAttributes());
        if (invoker != null) {
            URL url = invoker.getUrl(); //将url中的数据信息，设置到调用信息RpcInvocation的参数Map中，内部是用RpcInvocation对象进行数据传递
            setAttachment(PATH_KEY, url.getPath());
            if (url.hasParameter(INTERFACE_KEY)) { //参数包含接口、分组、版本、超时时间等
                setAttachment(INTERFACE_KEY, url.getParameter(INTERFACE_KEY));
            }
            if (url.hasParameter(GROUP_KEY)) { //若url中存在指定参数的值，则取出设置到当前RpcInvocation的attachments中
                setAttachment(GROUP_KEY, url.getParameter(GROUP_KEY));
            }
            if (url.hasParameter(VERSION_KEY)) {
                setAttachment(VERSION_KEY, url.getParameter(VERSION_KEY, "0.0.0"));
            }
            if (url.hasParameter(TIMEOUT_KEY)) {
                setAttachment(TIMEOUT_KEY, url.getParameter(TIMEOUT_KEY));
            }
            if (url.hasParameter(TOKEN_KEY)) {
                setAttachment(TOKEN_KEY, url.getParameter(TOKEN_KEY));
            }
            if (url.hasParameter(APPLICATION_KEY)) {
                setAttachment(APPLICATION_KEY, url.getParameter(APPLICATION_KEY));
            }
        }
        this.targetServiceUniqueName = invocation.getTargetServiceUniqueName();
    }

    // 提供多种构造函数，可有选择的调用
    public RpcInvocation(Invocation invocation) { //提取传入的invocation值，构建RpcInvocation
        this(invocation.getMethodName(), invocation.getServiceName(), invocation.getParameterTypes(),
                invocation.getArguments(), invocation.getObjectAttachments(), invocation.getInvoker(), invocation.getAttributes());
        this.targetServiceUniqueName = invocation.getTargetServiceUniqueName();
    }

    public RpcInvocation(Method method, String serviceName, Object[] arguments) {
        this(method, serviceName, arguments, null, null);
    }

    public RpcInvocation(Method method, String serviceName, Object[] arguments, Map<String, Object> attachment, Map<Object, Object> attributes) {
        this(method.getName(), serviceName, method.getParameterTypes(), arguments, attachment, null, attributes);
        this.returnType = method.getReturnType();
    }

    public RpcInvocation(String methodName, String serviceName, Class<?>[] parameterTypes, Object[] arguments) { //使用调用信息核心参数构造，包含调用的方法名、接口名、参数类型列表、参数值列表
        this(methodName, serviceName, parameterTypes, arguments, null, null, null);
    }

    public RpcInvocation(String methodName, String serviceName, Class<?>[] parameterTypes, Object[] arguments, Map<String, Object> attachments) {
        this(methodName, serviceName, parameterTypes, arguments, attachments, null, null);
    }

    // 构建调用的基本信息（如服务接口信息、方法信息、方法参数信息等）
    public RpcInvocation(String methodName, String serviceName, Class<?>[] parameterTypes, Object[] arguments,
                         Map<String, Object> attachments, Invoker<?> invoker, Map<Object, Object> attributes) {
        this.methodName = methodName;
        this.serviceName = serviceName;
        this.parameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
        this.arguments = arguments == null ? new Object[0] : arguments;
        this.attachments = attachments == null ? new HashMap<>() : attachments;
        this.attributes = attributes == null ? new HashMap<>() : attributes;
        this.invoker = invoker;
        initParameterDesc();
    }

    private void initParameterDesc() { //初始化参数描述信息
        ServiceRepository repository = ApplicationModel.getServiceRepository();
        if (StringUtils.isNotEmpty(serviceName)) {
            // 先查询服务描述信息ServiceDescriptor，再查找方法描述信息MethodDescriptor，最后从方法信息中找到相关信息并设置到成员变量中
            ServiceDescriptor serviceDescriptor = repository.lookupService(serviceName); //从ServiceRepository中的缓存Map中查找
            if (serviceDescriptor != null) {
                MethodDescriptor methodDescriptor = serviceDescriptor.getMethod(methodName, parameterTypes);
                if (methodDescriptor != null) {
                    this.parameterTypesDesc = methodDescriptor.getParamDesc();
                    this.compatibleParamSignatures = methodDescriptor.getCompatibleParamSignatures();
                    this.returnTypes = methodDescriptor.getReturnTypes();
                }
            }
        }

        if (parameterTypesDesc == null) { //若serviceName为空或没有找到MethodDescriptor、methodDescriptor信息，则取当前成员变量进行处理
            this.parameterTypesDesc = ReflectUtils.getDesc(this.getParameterTypes()); //获取参数的类型描述信息，如Ljava/lang/String;
            this.compatibleParamSignatures = Stream.of(this.parameterTypes).map(Class::getName).toArray(String[]::new);
            this.returnTypes = RpcUtils.getReturnTypes(this);
        }
    }

    @Override
    public Invoker<?> getInvoker() {
        return invoker;
    }

    public void setInvoker(Invoker<?> invoker) {
        this.invoker = invoker;
    }

    public Object put(Object key, Object value) {
        return attributes.put(key, value);
    }

    public Object get(Object key) {
        return attributes.get(key);
    }

    @Override
    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getTargetServiceUniqueName() {
        return targetServiceUniqueName;
    }

    public void setTargetServiceUniqueName(String targetServiceUniqueName) {
        this.targetServiceUniqueName = targetServiceUniqueName;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
    }

    public String getParameterTypesDesc() {
        return parameterTypesDesc;
    }

    public void setParameterTypesDesc(String parameterTypesDesc) {
        this.parameterTypesDesc = parameterTypesDesc;
    }

    public String[] getCompatibleParamSignatures() {
        return compatibleParamSignatures;
    }

    // parameter signatures can be set independently, it is useful when the service type is not found on caller side and
    // the invocation is not generic invocation either.
    public void setCompatibleParamSignatures(String[] compatibleParamSignatures) {
        this.compatibleParamSignatures = compatibleParamSignatures;
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments == null ? new Object[0] : arguments;
    }

    @Override
    public Map<String, Object> getObjectAttachments() {
        return attachments;
    }

    @Override
    public void setAttachment(String key, String value) {
        setObjectAttachment(key, value);
    }

    @Deprecated
    @Override
    public Map<String, String> getAttachments() {
        return new AttachmentsAdapter.ObjectToStringMap(attachments);
    }

    @Deprecated
    public void setAttachments(Map<String, String> attachments) {
        this.attachments = attachments == null ? new HashMap<>() : new HashMap<>(attachments);
    }

    public void setObjectAttachments(Map<String, Object> attachments) {
        this.attachments = attachments == null ? new HashMap<>() : attachments;
    }

    public void setAttachment(String key, Object value) {
       setObjectAttachment(key, value);
    }

    @Override
    public void setObjectAttachment(String key, Object value) {
        if (attachments == null) {
            attachments = new HashMap<>();
        }
        attachments.put(key, value);
    }

    @Override
    public void setAttachmentIfAbsent(String key, String value) {
        setObjectAttachmentIfAbsent(key, value);
    }

    public void setAttachmentIfAbsent(String key, Object value) {
        setObjectAttachmentIfAbsent(key, value);
    }

    @Override
    public void setObjectAttachmentIfAbsent(String key, Object value) {
        if (attachments == null) {
            attachments = new HashMap<>();
        }
        if (!attachments.containsKey(key)) { //附加参数Map不存在key时，进行设置操作
            attachments.put(key, value);
        }
    }

    @Deprecated
    public void addAttachments(Map<String, String> attachments) {
        if (attachments == null) {
            return;
        }
        if (this.attachments == null) {
            this.attachments = new HashMap<>();
        }
        this.attachments.putAll(attachments);
    }

    public void addObjectAttachments(Map<String, Object> attachments) { //添加附加参数
        if (attachments == null) {
            return;
        }
        if (this.attachments == null) {
            this.attachments = new HashMap<>();
        }
        this.attachments.putAll(attachments);
    }

    @Deprecated
    public void addAttachmentsIfAbsent(Map<String, String> attachments) {
        if (attachments == null) {
            return;
        }
        for (Map.Entry<String, String> entry : attachments.entrySet()) {
            setAttachmentIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    public void addObjectAttachmentsIfAbsent(Map<String, Object> attachments) { //添加附加参数（在key不存在时设置，相同的key不做覆盖）
        if (attachments == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : attachments.entrySet()) {
            setAttachmentIfAbsent(entry.getKey(), entry.getValue()); //依次将输入的附加参数，设置到RpcInvocation的附加参数中
        }
    }

    @Override
    @Deprecated
    public String getAttachment(String key) {
        if (attachments == null) {
            return null;
        }
        Object value = attachments.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    @Override
    public Object getObjectAttachment(String key) {
        if (attachments == null) {
            return null;
        }
        return attachments.get(key);
    }

    @Override
    @Deprecated
    public String getAttachment(String key, String defaultValue) {
        if (attachments == null) {
            return defaultValue;
        }
        Object value = attachments.get(key);
        if (value instanceof String) {
            String strValue = (String) value;
            if (StringUtils.isEmpty(strValue)) {
                return defaultValue;
            } else {
                return strValue;
            }
        }
        return null;
    }

    @Deprecated
    public Object getObjectAttachment(String key, Object defaultValue) {
        if (attachments == null) {
            return defaultValue;
        }
        Object value = attachments.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public void setReturnType(Class<?> returnType) {
        this.returnType = returnType;
    }

    public Type[] getReturnTypes() {
        return returnTypes;
    }

    public void setReturnTypes(Type[] returnTypes) {
        this.returnTypes = returnTypes;
    }

    public InvokeMode getInvokeMode() {
        return invokeMode;
    }

    public void setInvokeMode(InvokeMode invokeMode) {
        this.invokeMode = invokeMode;
    }

    @Override
    public String toString() {
        return "RpcInvocation [methodName=" + methodName + ", parameterTypes="
                + Arrays.toString(parameterTypes) + ", arguments=" + Arrays.toString(arguments)
                + ", attachments=" + attachments + "]";
    }

}
