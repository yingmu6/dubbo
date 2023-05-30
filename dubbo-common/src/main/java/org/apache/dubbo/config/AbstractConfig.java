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
package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.CompositeConfiguration;
import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.*;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.AsyncMethodInfo;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.utils.ReflectUtils.findMethodByMethodSignature;

/**
 * Utility methods and public methods for parsing configuration
 *
 * @export
 */
public abstract class AbstractConfig implements Serializable {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractConfig.class);
    private static final long serialVersionUID = 4267533505537413570L;

    /**
     * The legacy properties container
     */
    private static final Map<String, String> LEGACY_PROPERTIES = new HashMap<String, String>();

    /**
     * The suffix（后缀） container
     */
    private static final String[] SUFFIXES = new String[]{"Config", "Bean", "ConfigBase"};

    static { //预置的属性键值对
        LEGACY_PROPERTIES.put("dubbo.protocol.name", "dubbo.service.protocol");
        LEGACY_PROPERTIES.put("dubbo.protocol.host", "dubbo.service.server.host");
        LEGACY_PROPERTIES.put("dubbo.protocol.port", "dubbo.service.server.port");
        LEGACY_PROPERTIES.put("dubbo.protocol.threads", "dubbo.service.max.thread.pool.size");
        LEGACY_PROPERTIES.put("dubbo.consumer.timeout", "dubbo.service.invoke.timeout");
        LEGACY_PROPERTIES.put("dubbo.consumer.retries", "dubbo.service.max.retry.providers");
        LEGACY_PROPERTIES.put("dubbo.consumer.check", "dubbo.service.allow.no.provider");
        LEGACY_PROPERTIES.put("dubbo.service.url", "dubbo.service.address");
    }

    /**
     * The config id
     */
    protected String id; //配置id（所有config子类都有）
    protected String prefix;

    protected final AtomicBoolean refreshed = new AtomicBoolean(false);

    private static String convertLegacyValue(String key, String value) {
        if (value != null && value.length() > 0) {
            if ("dubbo.service.max.retry.providers".equals(key)) {
                return String.valueOf(Integer.parseInt(value) - 1);
            } else if ("dubbo.service.allow.no.provider".equals(key)) {
                return String.valueOf(!Boolean.parseBoolean(value));
            }
        }
        return value;
    }

    public static String getTagName(Class<?> cls) { //获取Config类对应的标签名，比如ConfigCenter为config-center
        String tag = cls.getSimpleName(); //如ConfigCenterConfig，tag为ConfigCenterConfig
        for (String suffix : SUFFIXES) { //若类名包含指定后缀名，则先去除掉后缀
            if (tag.endsWith(suffix)) { //把包含的后缀去掉，比如ConfigCenterConfig改为ConfigCenter
                tag = tag.substring(0, tag.length() - suffix.length());
                break;
            }
        }
        return StringUtils.camelToSplitName(tag, "-"); //将驼峰字符串转换为按分隔符处理
    }

    public static void appendParameters(Map<String, String> parameters, Object config) {
        appendParameters(parameters, config, null);
    }

    @SuppressWarnings("unchecked")
    public static void appendParameters(Map<String, String> parameters, Object config, String prefix) { //将Config对象中的属性值进行筛选处理，并添加的参数Map中
        /**
         * @csy-004 待调试，parameters都用途是啥？是指把config中的属性值写到参数map中吗？
         * 解：该方法的作用就是，将Config配置对象中的属性值进行筛选，按键值对写到参数map中，而参数map用于后续通讯的数据传输
         */
        if (config == null) {
            return;
        }
        Method[] methods = config.getClass().getMethods();
        for (Method method : methods) {
            try {
                String name = method.getName();
                if (MethodUtils.isGetter(method)) {
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) { //方法返回值为对象或在声明@Parameter且参数被排除时，跳过不处理
                        continue;
                    }
                    String key;
                    if (parameter != null && parameter.key().length() > 0) { //若方法使用了@Parameter注解声明，且设置了key的值，则将该值作为参数key
                        key = parameter.key();
                    } else { //没有带@Parameter注解
                        key = calculatePropertyFromGetter(name); //提取属性名，如getProtocol()方法的属性名为protocol(若属性名是驼峰的，则按分隔符处理，如getProtocolName(),若分隔符为"."，则最终的属性名为protocol.name)
                    }
                    Object value = method.invoke(config); //使用反射机制，获取config中get方法的返回值
                    String str = String.valueOf(value).trim();
                    if (value != null && str.length() > 0) {
                        if (parameter != null && parameter.escaped()) {
                            str = URL.encode(str); //若escaped指定是需要编码的，则编码字符串
                        }
                        if (parameter != null && parameter.append()) { //若属性key对应的值有多个，是否要在原来的值上附加
                            String pre = parameters.get(key);
                            if (pre != null && pre.length() > 0) {
                                str = pre + "," + str; //带上分隔符，附加到原有的值value上
                            }
                        }
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key; //若有指定分隔符，附加到属性key上
                        }
                        parameters.put(key, str); //处理好属性key、value后，写入参数Map中
                    } else if (parameter != null && parameter.required()) { //在值value为空，且@parameter注解required声明为必须时，报出异常信息
                        throw new IllegalStateException(config.getClass().getSimpleName() + "." + key + " == null");
                    }
                } else if (isParametersGetter(method)) { //若是getParameters()方法，则可以将该方法的返回值Map<String, String>直接设置到处理的参数map中
                    Map<String, String> map = (Map<String, String>) method.invoke(config, new Object[0]);
                    parameters.putAll(convert(map, prefix));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    @Deprecated
    protected static void appendAttributes(Map<String, Object> parameters, Object config) {
        appendAttributes(parameters, config, null);
    }

    @Deprecated
    protected static void appendAttributes(Map<String, Object> parameters, Object config, String prefix) {
        if (config == null) {
            return;
        }
        Method[] methods = config.getClass().getMethods();
        for (Method method : methods) {
            try {
                Parameter parameter = method.getAnnotation(Parameter.class);
                if (parameter == null || !parameter.attribute()) {
                    continue;
                }
                String name = method.getName();
                if (MethodUtils.isGetter(method)) {
                    String key;
                    if (parameter.key().length() > 0) {
                        key = parameter.key();
                    } else {
                        key = calculateAttributeFromGetter(name);
                    }
                    Object value = method.invoke(config);
                    if (value != null) {
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key;
                        }
                        parameters.put(key, value);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    protected static AsyncMethodInfo convertMethodConfig2AsyncInfo(MethodConfig methodConfig) {
        if (methodConfig == null || (methodConfig.getOninvoke() == null && methodConfig.getOnreturn() == null && methodConfig.getOnthrow() == null)) {
            return null;
        }

        //check config conflict
        if (Boolean.FALSE.equals(methodConfig.isReturn()) && (methodConfig.getOnreturn() != null || methodConfig.getOnthrow() != null)) {
            throw new IllegalStateException("method config error : return attribute must be set true when onreturn or onthrow has been set.");
        }

        AsyncMethodInfo asyncMethodInfo = new AsyncMethodInfo();

        asyncMethodInfo.setOninvokeInstance(methodConfig.getOninvoke());
        asyncMethodInfo.setOnreturnInstance(methodConfig.getOnreturn());
        asyncMethodInfo.setOnthrowInstance(methodConfig.getOnthrow());

        try {
            String oninvokeMethod = methodConfig.getOninvokeMethod();
            if (StringUtils.isNotEmpty(oninvokeMethod)) {
                asyncMethodInfo.setOninvokeMethod(getMethodByName(methodConfig.getOninvoke().getClass(), oninvokeMethod));
            }

            String onreturnMethod = methodConfig.getOnreturnMethod();
            if (StringUtils.isNotEmpty(onreturnMethod)) {
                asyncMethodInfo.setOnreturnMethod(getMethodByName(methodConfig.getOnreturn().getClass(), onreturnMethod));
            }

            String onthrowMethod = methodConfig.getOnthrowMethod();
            if (StringUtils.isNotEmpty(onthrowMethod)) {
                asyncMethodInfo.setOnthrowMethod(getMethodByName(methodConfig.getOnthrow().getClass(), onthrowMethod));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        return asyncMethodInfo;
    }

    private static Method getMethodByName(Class<?> clazz, String methodName) {
        try {
            return ReflectUtils.findMethodByMethodName(clazz, methodName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Set<String> getSubProperties(Map<String, String> properties, String prefix) {
        return properties.keySet().stream().filter(k -> k.contains(prefix)).map(k -> {
            k = k.substring(prefix.length());
            return k.substring(0, k.indexOf("."));
        }).collect(Collectors.toSet());
    }

    // 提取属性名称
    private static String extractPropertyName(Class<?> clazz, Method setter) throws Exception { //extract [ˈekstrækt] v. 提取 n. 选段，引文
        String propertyName = setter.getName().substring("set".length()); //取除set的子串，作为属性名
        Method getter = null;
        try {
            getter = clazz.getMethod("get" + propertyName);
        } catch (NoSuchMethodException e) {
            getter = clazz.getMethod("is" + propertyName);
        }
        Parameter parameter = getter.getAnnotation(Parameter.class);
        if (parameter != null && StringUtils.isNotEmpty(parameter.key()) && parameter.useKeyAsProperty()) { //若方法上带有@Parameter注解，则使用注解上声明的名称作为属性名称
            propertyName = parameter.key();
        } else {
            propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1); //若方法上没有带有@Parameter注解或注解中未设置key值，从方法名中提出属性名（首字母转换为小写）
        }
        return propertyName;
    }

    private static String calculatePropertyFromGetter(String name) { //从get方法中获取属性名
        int i = name.startsWith("get") ? 3 : 2;
        return StringUtils.camelToSplitName(name.substring(i, i + 1).toLowerCase() + name.substring(i + 1), ".");
    }

    private static String calculateAttributeFromGetter(String getter) { //从get方法中计算属性名
        int i = getter.startsWith("get") ? 3 : 2; //判断是否以"get"开头，可以判断是get方法还是is方法
        return getter.substring(i, i + 1).toLowerCase() + getter.substring(i + 1); //去除get或is后，将第一个字母小写后，再拼接后续的字符串
    }

    private static void invokeSetParameters(Class c, Object o, Map map) {
        try {
            Method method = findMethodByMethodSignature(c, "setParameters", new String[] {Map.class.getName()});
            if (method != null && isParametersSetter(method)) {
                method.invoke(o, map);
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    private static Map<String, String> invokeGetParameters(Class c, Object o) {
        try {
            Method method = findMethodByMethodSignature(c, "getParameters", null); //根据方法签名，找到对应的Method对象
            if (method != null && isParametersGetter(method)) { //若Method对象不为空，且符合条件，则对应执行getParameters()
                return (Map<String, String>) method.invoke(o);
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static boolean isParametersGetter(Method method) { //判断是否是getParameters()方法
        String name = method.getName();
        return ("getParameters".equals(name)
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterTypes().length == 0
                && method.getReturnType() == Map.class); //返回值是Map类型
    }

    private static boolean isParametersSetter(Method method) { //判断是否是有效的setParameters()方法
        return ("setParameters".equals(method.getName())
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 1
                && Map.class == method.getParameterTypes()[0]
                && method.getReturnType() == void.class);
    }

     /**
     * @param parameters the raw（原始的） parameters
     * @param prefix     the prefix
     * @return the parameters whose raw key will replace "-" to "."
     * @revised 2.7.8 "private" to be "protected"（revised： [rɪ'vaɪzd] adj. 改进的，v. 修改；校订）
     */
    protected static Map<String, String> convert(Map<String, String> parameters, String prefix) { //将参数Map的key拼接上前缀
        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        String pre = (prefix != null && prefix.length() > 0 ? prefix + "." : "");
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            result.put(pre + key, value); //带上前缀处理
            // For compatibility [kəmˌpætəˈbɪləti] n.兼容性 , key like "registry-type" will has a duplicate key "registry.type"
            // (出于兼容性，若带有中划线的key，会产生带上点号的key，即会有两个key)
            if (key.contains("-")) {
                result.put(pre + key.replace('-', '.'), value);
            }
        }
        return result;
    }

    @Parameter(excluded = true)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void updateIdIfAbsent(String value) {
        if (StringUtils.isNotEmpty(value) && StringUtils.isEmpty(id)) {
            this.id = value;
        }
    }

    protected void appendAnnotation(Class<?> annotationClass, Object annotation) {
        Method[] methods = annotationClass.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() != Object.class
                    && method.getReturnType() != void.class
                    && method.getParameterTypes().length == 0
                    && Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers())) {
                try {
                    String property = method.getName();
                    if ("interfaceClass".equals(property) || "interfaceName".equals(property)) {
                        property = "interface";
                    }
                    String setter = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
                    Object value = method.invoke(annotation);
                    if (value != null && !value.equals(method.getDefaultValue())) {
                        Class<?> parameterType = ReflectUtils.getBoxedClass(method.getReturnType());
                        if ("filter".equals(property) || "listener".equals(property)) {
                            parameterType = String.class;
                            value = StringUtils.join((String[]) value, ",");
                        } else if ("parameters".equals(property)) {
                            parameterType = Map.class;
                            value = CollectionUtils.toStringMap((String[]) value);
                        }
                        try {
                            Method setterMethod = getClass().getMethod(setter, parameterType);
                            setterMethod.invoke(this, value);
                        } catch (NoSuchMethodException e) {
                            // ignore
                        }
                    }
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Should be called after Config was fully initialized.
     * // FIXME: this method should be completely replaced by appendParameters
     *
     * @return
     * @see AbstractConfig#appendParameters(Map, Object, String)
     * <p>
     * Notice! This method should include all properties in the returning map, treat @Parameter differently compared to appendParameters.（区别对待@Parameter和appendParameters）
     */
    public Map<String, String> getMetaData() { //获取Config对象中的元数据（找到元数据方法或getParameters方法，进行方法调用并获取到返回值，最后设置到元数据Map中）
        Map<String, String> metaData = new HashMap<>();
        Method[] methods = this.getClass().getMethods(); //this.getClass() 指的是AbstractConfig的实例对象，比如ConfigCenterConfig
        for (Method method : methods) {
            try {
                String name = method.getName();
                if (MethodUtils.isMetaMethod(method)) { //判断是否是获取元数据方法（public的get/is方法，且返回值是基本类型）
                    String key;
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    if (parameter != null && parameter.key().length() > 0 && parameter.useKeyAsProperty()) {
                        key = parameter.key(); //若方法上带有@Parameter注解，则直接取注解中key的值
                    } else {
                        key = calculateAttributeFromGetter(name); //若没有带有@Parameter注解或配置key，则从方法名中取出属性名
                    }
                    // treat url and configuration differently, the value should always present in configuration though it may not need to present in url.
                    //（区别对待url和配置，值应该总是在配置中呈现，尽管它可能不需要在url中呈现）
                    //if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) {
                    if (method.getReturnType() == Object.class) {
                        metaData.put(key, null);
                        continue;
                    }

                    /**
                     * Attributes annotated as deprecated should not override newly added replacement.
                     */
                    if (MethodUtils.isDeprecated(method) && metaData.get(key) != null) { //若方法已经弃用了，则不再处理
                        continue;
                    }

                    Object value = method.invoke(this); //使用反射调用对应方法，并接收返回值
                    String str = String.valueOf(value).trim();
                    if (value != null && str.length() > 0) {
                        metaData.put(key, str); //将配置对象中的key、value设置到元数据中
                    } else {
                        metaData.put(key, null);
                    }
                } else if (isParametersGetter(method)) { //判断是否是getParameters方法
                    Map<String, String> map = (Map<String, String>) method.invoke(this, new Object[0]); //调用getParameters()方法，new Object[0]表明没有参数
                    metaData.putAll(convert(map, ""));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        return metaData;
    }

    @Parameter(excluded = true)
    public String getPrefix() { //获取Config的前缀名（若没有设置，则使用默认的前缀名，如ApplicationConfig对应的前缀为"dubbo.application"）
        return StringUtils.isNotEmpty(prefix) ? prefix : (CommonConstants.DUBBO + "." + getTagName(this.getClass()));
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void refresh() { //刷新Config对象的属性值（获取最新的配置值，然后通过set方法或setParameters方法设置到Config对象中）
        Environment env = ApplicationModel.getEnvironment(); //获取环境信息
        try {
            CompositeConfiguration compositeConfiguration = env.getPrefixedConfiguration(this); //获取带有前缀的合成配置实例（包含多种配置源的实例对象）
            // loop methods, get override value and set the new value back to method
            Method[] methods = getClass().getMethods();
            for (Method method : methods) { //遍历当前配置对象的方法，从合成的配置实例中获取值，通过set()方法或setParameters()方法设置到XxxConfig对象中
                if (MethodUtils.isSetter(method)) { //是否是setXXX()方法
                    try {
                        String value = StringUtils.trim(compositeConfiguration.getString(extractPropertyName(getClass(), method))); //从配置中心获取属性对应的值（若为远程的配置中心，则发起了远程调用）
                        // isTypeMatch() is called to avoid duplicate and incorrect update, for example, we have two 'setGeneric' methods in ReferenceConfig.
                        if (StringUtils.isNotEmpty(value) && ClassUtils.isTypeMatch(method.getParameterTypes()[0], value)) { //若值不为空，且参数类型与参数值能够匹配，则执行invoke调用
                            method.invoke(this, ClassUtils.convertPrimitive(method.getParameterTypes()[0], value)); //调用set方法对Config对象的属性设置
                        }
                    } catch (NoSuchMethodException e) {
                        logger.info("Failed to override the property " + method.getName() + " in " +
                                this.getClass().getSimpleName() +
                                ", please make sure every property has getter/setter method provided.");
                    }
                } else if (isParametersSetter(method)) { //是否是setParameters()方法
                    String value = StringUtils.trim(compositeConfiguration.getString(extractPropertyName(getClass(), method)));
                    if (StringUtils.isNotEmpty(value)) {
                        Map<String, String> map = invokeGetParameters(getClass(), this); //获取getParameters()方法的返回值
                        map = map == null ? new HashMap<>() : map;
                        map.putAll(convert(StringUtils.parseParameters(value), "")); //将属性值解析为Map形式，并设置到配置对象Config的Map
                        invokeSetParameters(getClass(), this, map); //调用setParameters()方法，对Config对象的参数设值
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to override ", e);
        }
    }

    @Override
    public String toString() {
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("<dubbo:");
            buf.append(getTagName(getClass()));
            Method[] methods = getClass().getMethods();
            for (Method method : methods) {
                try {
                    if (MethodUtils.isGetter(method)) {
                        String name = method.getName();
                        String key = calculateAttributeFromGetter(name);

                        try {
                            getClass().getDeclaredField(key);
                        } catch (NoSuchFieldException e) {
                            // ignore
                            continue;
                        }

                        Object value = method.invoke(this);
                        if (value != null) {
                            buf.append(" ");
                            buf.append(key);
                            buf.append("=\"");
                            buf.append(value);
                            buf.append("\"");
                        }
                    }
                } catch (Exception e) {
                    logger.warn(e.getMessage(), e);
                }
            }
            buf.append(" />");
            return buf.toString();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
            return super.toString();
        }
    }

    /**
     * FIXME check @Parameter(required=true) and any conditions that need to match.
     */
    @Parameter(excluded = true)
    public boolean isValid() {
        return true;
    }


    @Override
    public boolean equals(Object obj) { //config对象比较
        if (obj == null || !(obj.getClass().getName().equals(this.getClass().getName()))) { //判断对象是否为空或待比较的config对象与当前config对象的类名是否相等
            return false;
        }

        // 依次遍历当前this.config对象的get、is方法，然后根据方法名找到待比较config对象中的方法，
        // 依次获取返回值进行比较，只要有一个返回值不匹配，则认为不想等。只有所有get、is方法的返回值相等时，两个config对象相等
        Method[] methods = this.getClass().getMethods();
        for (Method method1 : methods) {
            if (MethodUtils.isGetter(method1)) {
                Parameter parameter = method1.getAnnotation(Parameter.class);
                if (parameter != null && parameter.excluded()) { //带上@Parameter注解，且属性设置excluded=true，则跳过当前方法的处理
                    continue;
                }
                try {
                    Method method2 = obj.getClass().getMethod(method1.getName(), method1.getParameterTypes());
                    Object value1 = method1.invoke(this, new Object[] {});
                    Object value2 = method2.invoke(obj, new Object[] {});
                    if (!Objects.equals(value1, value2)) { //比较方法的返回值
                        return false;
                    }
                } catch (Exception e) {
                    return true;
                }
            }
        }
        return true;
    }

    /**
     * Add {@link AbstractConfig instance} into {@link ConfigManager}
     * <p>
     * Current method will invoked by Spring or Java EE container automatically, or should be triggered manually.
     *
     * @see ConfigManager#addConfig(AbstractConfig)
     * @since 2.7.5
     */
    @PostConstruct
    public void addIntoConfigManager() {
        ApplicationModel.getConfigManager().addConfig(this);
    }

    @Override
    public int hashCode() {
        int hashCode = 1;

        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            if (MethodUtils.isGetter(method)) {
                Parameter parameter = method.getAnnotation(Parameter.class);
                if (parameter != null && parameter.excluded()) {
                    continue;
                }
                try {
                    Object value = method.invoke(this, new Object[]{});
                    hashCode = 31 * hashCode + value.hashCode();
                } catch (Exception ignored) {
                    //ignored
                }
            }
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }
}
