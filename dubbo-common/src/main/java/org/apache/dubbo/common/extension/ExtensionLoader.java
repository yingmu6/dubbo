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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.extension.support.WrapperComparator;
import org.apache.dubbo.common.lang.Prioritized;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static java.util.ServiceLoader.load;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.constants.CommonConstants.*;

/**
 * {@link org.apache.dubbo.rpc.model.ApplicationModel}, {@code DubboBootstrap} and this class（当前类） are
 * at present designed to be singleton or static (by itself totally（完全） static or uses some static fields).
 * So the instances returned from them are of process or classloader scope. If you want to support
 * multiple dubbo servers in a single process, you may need to refactor these three classes.
 *
 * （翻译内容：ApplicationModel、DubboBootstrap和当前的类ExtensionLoader目前被设计为单例或静态(本身完全静态或使用一些静态字段)。
 * 因此，从它们返回的实例属于进程或类加载器范围。如果你想支持在一个进程中有多个dubbo服务器，你可能需要重构这三个类）
 * <p>
 * Load dubbo extensions（ExtensionLoader用途：加载dubbo的扩展信息）
 * <ul>
 * <li>auto inject dependency extension </li> 自动注入依赖的扩展
 * <li>auto wrap extension in wrapper </li>   自动封装扩展
 * <li>default extension is an adaptive instance</li> 默认扩展是一个自适应实例
 * </ul>
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI     SPI标识注解
 * @see org.apache.dubbo.common.extension.Adaptive  自适应注解
 * @see org.apache.dubbo.common.extension.Activate  自动激活注解
 */
public class ExtensionLoader<T> { //扩展加载器（将配置文件中的信息，加载到缓存中，T为SPI接口对应的泛型，即成员变量type的类型）
    /**
     * @csy-007 ExtensionLoader是单例模式吗？ 只能有一个实例吗？
     * 解：不是，每一个SPI接口对应一个ExtensionLoader实例，测试如org.apache.dubbo.common.extension.ExtensionLoaderTest#test_getDefaultExtension()
     * 从
     */

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*"); //类变量：类的所有对象共同拥有，成员变量：对象独自拥有

    /**
     * 扩展接口与ExtensionLoader扩展加载器的映射（类共享变量）
     * 1）包含了ExtensionFactory接口与其它接口的映射
     * 2）每一个SPI接口对应一个ExtensionLoader
     * 3）static 静态成员变量，对象之间共享（具体的对象时，EXTENSION_LOADERS、EXTENSION_INSTANCES等static变量是没有存值的）
     *
     */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>(64);

    /**
     * 扩展类Class与扩展实例的映射（类共享变量，同一个扩展接口对应一个扩展实例，也就是单实例的）
     */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>(64);

    private final Class<?> type; //扩展接口的类型

    private final ExtensionFactory objectFactory; //扩展实例的创建工厂（ExtensionFactory也是SPI接口，1）此处的实例为AdaptiveExtensionFactory@xxx，会用存储的多个扩展工厂查找扩展实例，2）当type=ExtensionFactory.class时，objectFactory=null）

    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>(); //扩展实例类Class与扩展名的映射（该缓存中：多个扩展类可以对应同一个扩展名）

    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>(); //当前扩展接口中的扩展名与扩展类Class的映射（普通扩展类的缓存，从loadClass()看出，也包含@Activate的扩展类）

    private final Map<String, Object> cachedActivates = new ConcurrentHashMap<>(); //扩展名与@Active注解对象的映射（自动激活扩展类的缓存）
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>(); //扩展名与扩展实例的映射
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>(); //自适应扩展类的实例
    private volatile Class<?> cachedAdaptiveClass = null; //自适应扩展类（一个扩展接口最多只有一个自适应扩展类）
    private String cachedDefaultName; //缓存默认的扩展名，即为SPI上声明的扩展名
    private volatile Throwable createAdaptiveInstanceError; //创建自适应扩展实例时发生的错误

    private Set<Class<?>> cachedWrapperClasses; //扩展接口对应的封装类集合（封装类不是扩展类，所以没有在cachedClasses缓存中）

    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>(); //加载时扩展类时的异常信息

    private static volatile LoadingStrategy[] strategies = loadLoadingStrategies(); //加载的策略（使用java的SPI处理，static方法：在类加载时就执行，即进入类的具体方法前）

    public static void setLoadingStrategies(LoadingStrategy... strategies) {
        if (ArrayUtils.isNotEmpty(strategies)) {
            ExtensionLoader.strategies = strategies;
        }
    }

    /**
     * Load all {@link Prioritized prioritized} {@link LoadingStrategy Loading Strategies} via {@link ServiceLoader}
     *
     * @return non-null
     * @since 2.7.7
     */

    /**
     * 使用java SPI处理，获取各个加载策略并进行排序（若此处LoadingStrategy为SPI接口，用dubbo spi方式，则会循环等待初始化，最终不能初始化）
     */
    private static LoadingStrategy[] loadLoadingStrategies() {
        return stream(load(LoadingStrategy.class).spliterator(), false)
                .sorted()
                .toArray(LoadingStrategy[]::new);
    }

    /**
     * Get all {@link LoadingStrategy Loading Strategies}
     *
     * @return non-null
     * @see LoadingStrategy
     * @see Prioritized
     * @since 2.7.7
     */
    public static List<LoadingStrategy> getLoadingStrategies() {
        return asList(strategies);
    }

    /**
     * @csy-002 此处递归待调试下，看下递归流程？递归的原理是什么？
     * 递归：直接或间接调用自身的一种方法，它通常把一个大型复杂的问题层层转化为一个与原问题相似的规模较小的问题来求解，
     *      递归策略只需少量的程序就可描述出解题过程所需要的多次重复计算，大大地减少了程序的代码量
     *      https://baike.baidu.com/item/%E9%80%92%E5%BD%92/1740695?fr=aladdin （分治法）
     * 递归函数的执行过程，函数代码虽然只有一份，但在执行的过程中，每调用一次，就会有一次入栈，生成一份不同的参数、局部变量和返回地址（若没递归结束条件，则会栈溢出）
     * 一般来说，递归需要有边界条件、递归前进段和递归返回段。当边界条件不满足时，递归前进；当边界条件满足时，递归返回。
     *
     * 此处objectFactory设置的逻辑
     * 1）若SPI接口是ExtensionFactory，则objectFactory设置为null，因为自身已经是ExtensionFactory类型了
     * 2）若SPI接口非ExtensionFactory，则需要objectFactory实例的值，因为ExtensionFactory本身是SPI接口，所以还需要SPI的方式
     *    先获取到ExtensionLoader，再获取自适应的扩展实例
     */
    private ExtensionLoader(Class<?> type) { //私有的构造方法，创建ExtensionLoader实例（指定扩展接口的类型和扩展工厂）
        this.type = type;
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }

    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    /**
     * @csy-006 ExtensionLoader加载器的概念是啥？与类加载器概念有何异同？
     * ExtensionLoader：加载dubbo的扩展类
     * ClassLoader：classloader顾名思义，即是类加载。虚拟机把描述类的数据从class字节码文件加载到内存，并对数据进行检验、转换解析和初始化，
     * 最终形成可以被虚拟机直接使用的Java类型，这就是虚拟机的类加载机制
     * https://juejin.cn/post/6931972267609948167
     * <p>
     * 注明：因为SPI的处理都集中在当前ExtensionLoader中，所以进行SPI操作，需要先获取ExtensionLoader实例，再进行相关操作
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) { //获取扩展接口对应的扩展加载器ExtensionLoader（从缓存中获取，若不存在则重新创建）
        /**
         * 扩展类型：不为空且是SPI接口
         */
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) { //每个SPI接口，对应一个扩展加载器ExtensionLoader，若存在直接返回，否则创建ExtensionLoader
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type); //直接从Map中获取值，少了中间变量，更简洁些
        }
        return loader;
    }

    // For testing purposes only（移除扩展加载器的缓存，仅用于测试）
    public static void resetExtensionLoader(Class type) { //重置扩展加载器（将缓存中的扩展加载器、扩展实例移除）
        ExtensionLoader loader = EXTENSION_LOADERS.get(type);
        if (loader != null) {
            // Remove all instances associated with this loader as well
            Map<String, Class<?>> classes = loader.getExtensionClasses();
            for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
                EXTENSION_INSTANCES.remove(entry.getValue());
            }
            classes.clear();
            EXTENSION_LOADERS.remove(type);
        }
    }

    public static void destroyAll() {
        EXTENSION_INSTANCES.forEach((_type, instance) -> {
            if (instance instanceof Lifecycle) { //若为Lifecycle实例，则调用其destroy()方法
                Lifecycle lifecycle = (Lifecycle) instance;
                try {
                    lifecycle.destroy();
                } catch (Exception e) {
                    logger.error("Error destroying extension " + lifecycle, e);
                }
            }
        });
    }

    private static ClassLoader findClassLoader() { //获取ExtensionLoader对应的类加载器ClassLoader
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }

    public String getExtensionName(T extensionInstance) { //获取扩展实例对应的扩展名
        return getExtensionName(extensionInstance.getClass()); //转换为按扩展Class去取对应的扩展名
    }

    public String getExtensionName(Class<?> extensionClass) { //获取扩展实例Class对应的扩展名
        getExtensionClasses();// load class
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent（等同的） to {@code getActivateExtension(url, key, null)}
     *
     * @param url url
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String, String)
     */
    public List<T> getActivateExtension(URL url, String key) { //获取自动激活的扩展实例列表
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url
     * @param values extension point names
     * @return extension list which are activated
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * This is equivalent（等同的） to {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url
     * @param key   url parameter key which used to get extension point names（用于获取扩展名列表的url参数key）
     * @param group group
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String key, String group) { //如：获取filter列表时，key：service.filter，group：provider
        String value = url.getParameter(key); //从url中获取参数key对应的值，作为用户定义的扩展名列表
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group); //按分隔符拆分参数值，如"order1,default,order4"，拆分映射为数组
    }

    /**
     * Get activate extensions.（获取自动激活的扩展类列表）
     * 注明：自动激活的扩展分为两类
     * a）系统激活的扩展类：带有@Activate注解，匹配注解中的group、value获取实例
     * b）自定义激活的扩展类：由用户指定的扩展类，不带@Activate注解，不用比较group、value值
     *
     * 在指定扩展名时，如"aa,default,bb"，其中aa、bb是自定义扩展名，而default代表系统扩展类，不指定default时，自定义扩展名在default后，如"aa,bb"，
     * 最终的扩展类为：default扩展类 -> aa扩展类 -> bb扩展类
     *
     * @param url    url
     * @param values extension point names 扩展名列表
     * @param group  group 扩展名所属分组
     * @return extension list which are activated （返回匹配的扩展类列表）
     * @see org.apache.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) { //获取自动激活的扩展列表（将URL中配置的参数与@Activate配置的内容进行比较）
        List<T> activateExtensions = new ArrayList<>();
        List<String> names = values == null ? new ArrayList<>(0) : asList(values); // 自定义激活的扩展名列表（names即为用户配置的扩展名列表，如"aa,default,bb"，若没配置，则处理@Activate类）

        /**
         * 类型一：系统激活的扩展类（即类上带有@Activate）
         */
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) { // 系统激活的扩展类处理（若包含"-default"，则不处理系统激活的扩展类）
            getExtensionClasses(); //加载扩展类，并将带有@Activate的扩展类缓存到cachedActives中
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) { // 遍历从SPI配置文件中加载的@Activate标识的扩展类列表
                String name = entry.getKey(); //扩展名
                Object activate = entry.getValue(); // @Active对象

                String[] activateGroup, activateValue;

                if (activate instanceof Activate) { //取注解@Activate上设置的group、value值
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) { //兼容老版本的功能
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }

                /**
                 * 自动激活条件匹配逻辑（先比较group、再比较value）
                 * 1）判断用于匹配的group是否在注解声明的group的列表值中
                 * 2）扩展名name不在用户自定义的扩展名列表中且没有被剔除
                 * 3）将注解中声明的value值与url中参数值进行比较
                 * 若都满足条件，则获取扩展名对应的实例，并加载到系统激活扩展的列表中
                 */
                if (isMatchGroup(group, activateGroup)
                        && !names.contains(name) //为啥要有这个判断？解答：当前处理的是系统激活的扩展类，而names是自定义激活的扩展名列表，两者分开处理，所以要进行排除
                        && !names.contains(REMOVE_VALUE_PREFIX + name) //对应场景：剔除某个系统激活的类，如"-order"，则去掉扩展名为"order"对应的@Activate类（names没配置时，可通过此处校验）
                        && isActive(activateValue, url)) {
                    activateExtensions.add(getExtension(name)); // 若匹配，则获取扩展名name对应的实例并加载到列表中
                }
            }
            activateExtensions.sort(ActivateComparator.COMPARATOR); //将可激活扩展类列表进行排序
        }
        List<T> loadedExtensions = new ArrayList<>(); //处理用户自定义扩展实例的临时列表

        /**
         * 类型二：自定义激活的扩展类（没有带@Activate的扩展类）
         */
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            // 带有剔除符号"-"的扩展名，不做处理
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                if (DEFAULT_KEY.equals(name)) { //若指定了"default"，表明是系统激活的扩展实例，需调整系统激活和自定义激活实例的位置
                    if (!loadedExtensions.isEmpty()) { //loadedExtensions不为空，表明在"default"前，有自定义的扩展类
                        activateExtensions.addAll(0, loadedExtensions); //把自定义的激活类放在系统激活的类的前面
                        loadedExtensions.clear();
                    }
                } else {
                    loadedExtensions.add(getExtension(name)); //将符合条件的自定义激活的扩展实例加载到列表
                }
            }
        }
        if (!loadedExtensions.isEmpty()) { //若没有配置"default"，则系统激活的扩展类，放在自定义激活的扩展类前面
            activateExtensions.addAll(loadedExtensions);
        }
        return activateExtensions;
    }

    /**
     * 系统激活中的group匹配逻辑：
     * 1）若用于匹配的group为空，表明不按group匹配，则判定为匹配成功
     * 2）若用于匹配的group不为空，且@Activate注解上声明的group数组也不为空，则根据group是否在group数组中来判定是否匹配成功
     * 3）若用于匹配的group不为空，且@Activate注解上声明的group数组为空，则判定为匹配失败
     */
    private boolean isMatchGroup(String group, String[] groups) {//判断group是否匹配（group是用于匹配的参数，groups是@Activate注解上声明的group数组）
        if (StringUtils.isEmpty(group)) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 系统激活中的value匹配逻辑：
     * 1）若注解@Activate中没有配置value值，表明不按value匹配，则判定为匹配成功
     * 2）遍历注解@Active中配置的value值，按key:value形式解析（也可以只有key）。遍历URL中的参数集合，与注解value解析出的键值匹配
     *    2.1）若URL参数集合不为空，匹配参数的键值对
     *         a）匹配参数键，url参数与注解中参数相等，或url参数以注解中参数结尾，则匹配成功
     *         b）匹配参数值，若注解中配置了value，则需要与url中相同key对应的值比较，根据是否相等来判定；若注解中没配置value，只要url相同key的值不为空，即匹配成功
     *    2.2）若URL参数集合为空，则判定为匹配不成功
     */
    private boolean isActive(String[] keys, URL url) { // 比较值，keys是@Activate注解上的value值列表，将注解中value值列表与url的参数键值对进行比较
        if (keys.length == 0) { //若@Activate注解上没设置value，直接匹配成功
            return true;
        }
        for (String key : keys) { //遍历注解上的所有key（只要有一个key匹配成功，即匹配成功）
            // @Active(value="key1:value1, key2:value2")    2.5.6版本时没有key1:value1这种形式，直接用key来比较的
            String keyValue = null;
            if (key.contains(":")) { //分隔key中设置的值
                String[] arr = key.split(":");
                key = arr[0];
                keyValue = arr[1];
            }

            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {  //遍历url的参数集合
                String k = entry.getKey(); //url中参数键key
                String v = entry.getValue(); //url中参数值value
                if ((k.equals(key) || k.endsWith("." + key))
                        && ((keyValue != null && keyValue.equals(v)) || (keyValue == null && ConfigUtils.isNotEmpty(v)))) {
                    return true;
                }
            } //url中不包含参数集合时，返回false
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>()); //缓存中没有值时，初始化Holder的值
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    public List<T> getLoadedExtensionInstances() {
        List<T> instances = new ArrayList<>();
        cachedInstances.values().forEach(holder -> instances.add((T) holder.get()));
        return instances;
    }

    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }

//    public T getPrioritizedExtensionInstance() {
//        Set<String> supported = getSupportedExtensions();
//
//        Set<T> instances = new HashSet<>();
//        Set<T> prioritized = new HashSet<>();
//        for (String s : supported) {
//
//        }
//
//    }

    /**
     * Find the extension with the given name. If the specified name is not found, then {@link IllegalStateException}
     * will be thrown.
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) { //获取的扩展名对应的实例，默认是对实例进行封装的wrap=true
        return getExtension(name, true);
    }

    /**
     * @csy-009 配置文件中的扩展名与扩展类Class都是一次性加载好的，那扩展类的实例是怎么做到按需加载的？
     * 解：如本方法中的getOrCreateHolder(name)，就是只创建指定扩展名的实例
     */
    public T getExtension(String name, boolean wrap) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if ("true".equals(name)) { //扩展名为"true"，返回默认扩展实例
            return getDefaultExtension();
        }
        final Holder<Object> holder = getOrCreateHolder(name); //获取扩展名对应实例持有类
        Object instance = holder.get();
        if (instance == null) { //使用双重判断+synchronized来确保单实例
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) { //若缓存中没有扩展实例，则创建对应实例对象
                    instance = createExtension(name, wrap);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Get the extension by specified name if found, or {@link #getDefaultExtension() returns the default one}
     *
     * @param name the name of extension
     * @return non-null
     */
    public T getOrDefaultExtension(String name) { //获取指定扩展或默认扩展（SPI配置文件未配置指定扩展时，取默认扩展）
        return containsExtension(name) ? getExtension(name) : getDefaultExtension();
    }

    /**
     * Return default extension, return <code>null</code> if it's not configured.
     */
    public T getDefaultExtension() { //获取默认扩展实例（即SPI注解上声明的值）
        getExtensionClasses();
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) { //若没有配置，则返回null
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) { //判断指定的扩展名是否有对应的扩展信息
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    public Set<String> getSupportedExtensions() { //获取支持的扩展名集合（只包含普通扩展类，不包含自适应和自动激活类）
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    public Set<T> getSupportedExtensionInstances() { //获取支持的扩展实例
        List<T> instances = new LinkedList<>();
        Set<String> supportedExtensions = getSupportedExtensions(); //获取扩展名列表
        if (CollectionUtils.isNotEmpty(supportedExtensions)) {
            for (String name : supportedExtensions) { //依次遍历扩展名，然后获取对应的实例
                instances.add(getExtension(name));
            }
        }
        // sort the Prioritized instances
        sort(instances, Prioritized.COMPARATOR); //使用Prioritized比较器，进行排序
        return new LinkedHashSet<>(instances);
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via（通过） API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) { //添加扩展接口（通过API方式添加，非配置文件中配置）
        getExtensionClasses(); // load classes（加载扩展类）

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement the Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) { //非自适应扩展类时
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name); //符合条件后，设置到缓存中
            cachedClasses.get().put(name, clazz);
        } else { //自适应扩展类，即类上带有@Adaptive注解（一个扩展接口，最多只有一个自适应扩展类）
            if (cachedAdaptiveClass != null) { //添加扩展时，自适应类已存在，则抛出异常
                throw new IllegalStateException("Adaptive Extension already exists (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz; //自适应扩展，是不需要处理扩展名的
        }
    }

    /**
     * Replace the existing extension via API（替换存在的扩展）
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test （不再推荐使用，仅在测试时使用）
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) { //替换普通扩展类
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (!cachedClasses.get().containsKey(name)) { //判断需要替换的扩展名是否存在
                throw new IllegalStateException("Extension name " +
                        name + " doesn't exist (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name); //替换缓存中扩展信息，并移除缓存实例
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else { //替换自适应扩展类
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension doesn't exist (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null); //自适应扩展实例替换后，调用getAdaptiveExtension时会创建实例
        }
    }

    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() { //获取自适应扩展实例，若不存在则创建（先产生自适应扩展类，然后在运行时根据url中参数选择具体的实例调用）
        /**
         * 1）自适应扩展类，是根据字节码操作，在运行期间动态创建的，而不是声明的静态类（也可以通过在类上声明@Adaptive实现）
         * 2）先创建自适应类的实例，然后调用类的方法时，再从url中获取@Adaptive配置的参数值，实现调用的多态，是方法中实现多态，而不是类上实现多态
         */
        Object instance = cachedAdaptiveInstance.get(); //一个SPI接口最多只有一个自适应类
        if (instance == null) {
            if (createAdaptiveInstanceError != null) { //实例为空，且异常信息的实例不为空，表明当时创建自适应实例时，出现异常
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }

            synchronized (cachedAdaptiveInstance) { //注明：虽然cachedAdaptiveInstance是私有变量，但由于一个SPI的接口type对应一个ExtensionLoader，也就是多线程下可能会操作同一个ExtensionLoader，所以是会存在线程安全问题的
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        instance = createAdaptiveExtension();
                        cachedAdaptiveInstance.set(instance); //创建自适应扩展实例，并设置到缓存中
                    } catch (Throwable t) {
                        createAdaptiveInstanceError = t;
                        throw new IllegalStateException("Failed to create adaptive instance: " + t.toString(), t);
                    }
                }
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) { //若存在异常信息，且扩展名与异常Map中key匹配，则直接返回异常
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) { //若加载配置文件时，出现异常则进行信息追加
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    @SuppressWarnings("unchecked")
    private T createExtension(String name, boolean wrap) { //创建普通的扩展实例
        // 先加载扩展接口对应的所有扩展类Class，然后在找出扩展名对应扩展类Class
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) { //配置文件中，若没有查找到扩展名对应的Class类，则抛出扩展发现异常
            throw findException(name);
        }
        try {
            //通过Class的newInstance()创建扩展类的实例（反射机制）
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance()); //将扩展类newInstance()创建扩展实例，并放入缓存中
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            //注入依赖的扩展实例（类似IOC功能）
            injectExtension(instance);

            if (wrap) { //使用封装类对扩展实例进行封装（类似AOP功能，默认情况都会封装）

                List<Class<?>> wrapperClassesList = new ArrayList<>();
                if (cachedWrapperClasses != null) { //当前扩展接口对应的封装类列表（如WrappedExt的封装类列表为Ext5Wrapper1、Ext5Wrapper2，在前面getExtensionClass()时就对封装类进行缓存，缓存列表的顺序为SPI文件中配置顺序）
                    wrapperClassesList.addAll(cachedWrapperClasses);
                    wrapperClassesList.sort(WrapperComparator.COMPARATOR); //将封装类列表进行排序（对象比较后，列表按自然排序，即升序排列）
                    Collections.reverse(wrapperClassesList); //将已经排好序的封装类列表进行翻转（如1、2、3序列，翻转后变为3、2、1。为什么要翻转？为了经过层层封装后，使封装类的执行顺序与反转前的顺序一致）
                }

                if (CollectionUtils.isNotEmpty(wrapperClassesList)) {
                    for (Class<?> wrapperClass : wrapperClassesList) {
                        /**
                         * @csy-010 为啥使用了@Wrapper注解，获取的值还为null？声明了注解@Wrapper和未声明的处理逻辑是怎样的？
                         * 解：此处github上有同上的问题，说是@Wrapper没有生效，从语义上看不确定是否有问题
                         * 可参考 https://github.com/apache/dubbo/issues/6946
                         */
                        Wrapper wrapper = wrapperClass.getAnnotation(Wrapper.class); //用于条件匹配

                        /**
                         * 判断是否使用封装功能
                         * a）封装类没有带 @Wrapper注解
                         * b）封装类带有 @Wrapper注解，当前扩展名在需要封装的扩展名列表中，且不在不需要封装的扩展名列表中
                         */
                        if (wrapper == null
                                || (ArrayUtils.contains(wrapper.matches(), name) && !ArrayUtils.contains(wrapper.mismatches(), name))) {
                            instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance)); //通过构造方法的newInstance创建封装类实例，然后为封装类实例进行依赖注入（可能封装类还有其他的SPI依赖）
                        } //特别留意：这里的instance是循环赋值，即实现循环封装
                    }
                }
            }

            initExtension(instance); //初始化扩展实例（若实例为Lifecycle类型，则调用Lifecycle#initialize进行初始化）
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }

    private boolean containsExtension(String name) { //判断扩展键值对Map中，是否包含指定的扩展名
        return getExtensionClasses().containsKey(name);
    }

    /**
     * @csy-009 注入扩展逻辑是怎样的？
     * 解：创建扩展类的实例后，若该实例的属性是扩展类，会使用Set方法设置的扩展实例（即IOC功能）
     */
    private T injectExtension(T instance) { //通过set方式，为扩展实例进行依赖注入（T为SPI接口类型，此处instance已经传入具体实例值）

        if (objectFactory == null) { //扩展工厂为空时，提前结束
            return instance;
        }

        try {
            for (Method method : instance.getClass().getMethods()) { //遍历扩展实例中的所有方法，通过set方法进行依赖注入（不是SPI接口的方法）
                if (!isSetter(method)) { //只处理set方法
                    continue;
                }
                /**
                 * Check {@link DisableInject} to see if we need auto injection for this property
                 */
                if (method.getAnnotation(DisableInject.class) != null) { //若方法上声明@DisableInject，则不进行注入处理
                    continue;
                }
                Class<?> pt = method.getParameterTypes()[0]; //取出set方法中的参数Class
                /**
                 * @csy-009 参数类型只要不是基本类型就可以注入吗？非SPI类型的实例可以吗？
                 * 解：非SPI类型也不可以，使用ExtensionFactory工厂创建扩展对象时，明确指出是SPI接口
                 */
                if (ReflectUtils.isPrimitives(pt)) {
                    continue;
                }

                try {
                    String property = getSetterProperty(method); //获取属性名（通过解析方法名称）
                    Object object = objectFactory.getExtension(pt, property);//@csy-009 此处是怎么获取对象的？解：此处的objectFactory类型为自适应扩展工厂AdaptiveExtensionFactory，通过遍历其中维护的扩展工厂来获取扩展对象
                    if (object != null) { //获取到的扩展实例不为空时，则为对象属性设置值，如Ext6扩展接口的实现类Ext6Impl1
                        method.invoke(instance, object); //使用反射机制调用set方法，进入扩展对象的依赖注入
                    }
                } catch (Exception e) {
                    logger.error("Failed to inject via method " + method.getName()
                            + " of interface " + type.getName() + ": " + e.getMessage(), e);
                }

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance; //返回处理后的扩展实例
    }

    private void initExtension(T instance) {
        if (instance instanceof Lifecycle) {
            Lifecycle lifecycle = (Lifecycle) instance;
            lifecycle.initialize();
        }
    }

    /**
     * get properties name for setter, for instance: setVersion, return "version"
     * <p>
     * return "", if setter name with length less than 3
     */
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    /**
     * return true if and only if:
     * <p>
     * 1, public
     * <p>
     * 2, name starts with "set"
     * <p>
     * 3, only has one parameter
     */
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers());
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    /**
     * 从缓存中获取扩展类，若不存在则从文件中读取，并加载到缓存中
     * （缓存中存在则中缓存中取，不存在则从配置文件中读取，并加载到缓存中）
     * （缓存中不存在，可能是还没加载过，也可能是机器重启，内存中的内容被清除）
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get(); //缓存的扩展类，即可能来自SPI配置文件，也能来自API的addExtension添加的
        if (classes == null) { //锁外判断
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                /**
                 * @csy-003 synchronized + 双重判定的优势是什么？（用在单实例创建）
                 * https://www.cnblogs.com/xz816111/p/8470048.html
                 * 解：1）提升性能，若synchronized放在方法上，每次调用方法时都会加锁，降低性能
                 *    2）锁外判断，在实例不为空时，就不必进入锁内判断了
                 *       锁内判断，多个线程同时访问时，可能都通过外部判断，所以锁内要做下判断，已经创建过的对象就不在创建
                 *    3）创建的实例，应该用volatile修饰，避免指令重排，虽然有对象引用，但是对象还未创建
                 */
                if (classes == null) { //（锁内判断）
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes); //应该使用volatile，避免指令重排时，会访问到未初始化的对象
                }
            }
        }
        return classes;
    }

    /**
     * synchronized in getExtensionClasses
     */
    private Map<String, Class<?>> loadExtensionClasses() { //从SPI文件中读取配置，并按分类将扩展类加载到缓存中
        cacheDefaultExtensionName(); //在加载扩展文件前，会先缓存默认扩展名

        Map<String, Class<?>> extensionClasses = new HashMap<>(); //扩展名name与扩展类Class的映射

        for (LoadingStrategy strategy : strategies) { //兼容加载老版本的SPI接口，如com.alibaba.*
            loadDirectory(extensionClasses, strategy.directory(), type.getName(), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
            loadDirectory(extensionClasses, strategy.directory(), type.getName().replace("org.apache", "com.alibaba"), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
        }

        return extensionClasses;
    }

    /**
     * extract（提取） and cache default extension name if exists
     */
    private void cacheDefaultExtensionName() { //缓存默认扩展名，即为SPI注解上声明的value值
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation == null) {
            return;
        }

        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) { //提取SPI注解设定的值为默认扩展名
            String[] names = NAME_SEPARATOR.split(value);
            if (names.length > 1) {//扩展名不能有分隔符，不然分隔后就有多个扩展名
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
            if (names.length == 1) {
                cachedDefaultName = names[0]; //@SPI注解中若没配置值，即默认扩展名cachedDefaultName为空
            }
        }
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false, false);
    }

    /**
     * 加载指定目录下配置文件，读取扩展配置信息并写到缓存中
     */
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type, //extensionClasses引用传递，形参的改变会影响实参改变
                               boolean extensionLoaderClassLoaderFirst, boolean overridden, String... excludedPackages) {
        String fileName = dir + type; //如dir："META-INF/dubbo/internal/" ，type："org.apache.dubbo.common.extension.ExtensionFactory" （加载的是type接口所在模块下的dubbo配置文件）
        try {
            Enumeration<java.net.URL> urls = null;
            ClassLoader classLoader = findClassLoader();

            // try to load from ExtensionLoader's ClassLoader first
            if (extensionLoaderClassLoaderFirst) { //@csy-003 此处是什么含义？解：尝试用ExtensionLoader的类加载器加载文件资源
                ClassLoader extensionLoaderClassLoader = ExtensionLoader.class.getClassLoader();
                if (ClassLoader.getSystemClassLoader() != extensionLoaderClassLoader) {
                    urls = extensionLoaderClassLoader.getResources(fileName);
                }
            }

            if (urls == null || !urls.hasMoreElements()) {
                if (classLoader != null) {
                    urls = classLoader.getResources(fileName);
                } else {
                    urls = ClassLoader.getSystemResources(fileName);
                }
            }

            if (urls != null) {
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceURL, overridden, excludedPackages);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    /**
     * @csy-007 功能用途是什么？
     * 会读取扩展配置文件的所有内容，把所有的扩展名与扩展类解析，并依次放入对应的缓存
     * （把扩展配置文件中的信息，加载到缓存中）
     */
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String... excludedPackages) {
        try { //资源放在try里面创建，不使用时会自动被释放
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {//读取每一行，对每一行进行解析
                    final int ci = line.indexOf('#');
                    if (ci >= 0) { //若是注释的话，把注释内容去掉，#代表注释
                        line = line.substring(0, ci); //取#号前面的子串
                    }
                    line = line.trim(); //去除字符串头部、尾部的空格
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('='); //按等号进行分隔
                            if (i > 0) { //配置的格式为 extName = className，也可以为 className（不带扩展名，在后面的loadClass会处理）
                                name = line.substring(0, i).trim(); //扩展名（去前后空格）
                                line = line.substring(i + 1).trim(); //扩展类对应的全路径类名（去前后空格）
                            }
                            if (line.length() > 0 && !isExcluded(line, excludedPackages)) { // 配置了扩展类且没有被排除，则可将扩展类加载到缓存中
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name, overridden); //根据扩展类名产生Class，并加载到缓存中
                            }
                        } catch (Throwable t) { //加载扩展类出现异常时，将异常信息按扩展类的全路径名存起来（某一扩展类加载异常，会把异常信息缓存起来，不影响其它扩展类的加载）
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    /**
     * @csy-003 此处什么时候会进行排除？
     * 解：若指定排除的包，则加载类时，该包下的类不会加载到缓存中loadResource
     * 什么时候进行排除的话，这个主要看加载策略是否重写了LoadingStrategy#excludedPackages()，默认情况下不排除的
     */
    private boolean isExcluded(String className, String... excludedPackages) { //判断扩展类是不是在被排除的包下
        if (excludedPackages != null) {
            for (String excludePackage : excludedPackages) {
                if (className.startsWith(excludePackage + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

     /**
     * 加载配置文件中的内容，并设置到不同类型的缓存中，比如cachedAdaptiveClass、cachedWrapperClasses、extensionClasses、cachedActivates等
     * （对配置文件中对应的Class进行判断，设置到对应类型的缓存中）
     * <p>
     * clazz：是从配置文件加载的Class类，如filter=org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper，此处的clazz就是ProtocolFilterWrapper对应的class类
     */
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                           boolean overridden) throws NoSuchMethodException { //把扩展类分类加载到内存中
        /**
         * @csy-003 Class中的方法isAssignableFrom待了解实现
         * 解：isAssignableFrom 判断一个class（类或接口）是否与另一个class相同，或者是否是另一个class的父类或父接口
         * 如：type与clazz对应的Class是否相同，或type是否是clazz父类或父接口
         */
        if (!type.isAssignableFrom(clazz)) { //判断实例类是不是接口type的子类型
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        if (clazz.isAnnotationPresent(Adaptive.class)) { //缓存自适应扩展类（类上带有@Adaptive注解的扩展类）
            cacheAdaptiveClass(clazz, overridden);
        } else if (isWrapperClass(clazz)) { //缓存封装类型扩展类（即存在包含以扩展接口为参数的构造方法）
            cacheWrapperClass(clazz);
        } else { //缓存自动激活扩展类或普通扩展类
            clazz.getConstructor();
            if (StringUtils.isEmpty(name)) { //什么场景下扩展名为空？解：SPI配置文件中，扩展名为空的情况
                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }

            String[] names = NAME_SEPARATOR.split(name); //多个扩展名可以对应一个扩展类，如xxx.Ext10MultiNames配置的内容，如impl,implMultiName=xxx.Ext10MultiNamesImpl
            if (ArrayUtils.isNotEmpty(names)) {
                cacheActivateClass(clazz, names[0]); //缓存自动激活扩展类，即带有@Activate注解的扩展类（普通类在此处不会处理，所以普通类和自动激活类是区分开的）
                for (String n : names) {
                    cacheName(clazz, n); //缓存扩展类Class与扩展名的映射（允许多个扩展类Class对应同一个扩展名）
                    saveInExtensionClass(extensionClasses, clazz, n, overridden); //缓存扩展名与扩展类Class的映射
                }
            }
        } //@csy-011 若不是类上带有@Adaptive注解，而是方法上带有注解，会进行怎样的处理逻辑？ 解：会生成自适应类，带上注解的，会根据url获取扩展名
    }

    /**
     * cache name
     */
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) { //扩展类与扩展名的映射，如class org.apache.dubbo.rpc.protocol.dubbo.filter.TraceFilter -> trace
            cachedNames.put(clazz, name);
        }
    }

    /**
     * put clazz in extensionClasses
     */
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name, boolean overridden) {
        Class<?> c = extensionClasses.get(name);
        if (c == null || overridden) { //未加载过或加载过且允许扩展类覆盖时，将扩展名与扩展类进行映射，放入Map中
            extensionClasses.put(name, clazz);
        } else if (c != clazz) { //不允许扩展类覆盖时，一个扩展名对应多个扩展类抛出异常
            String duplicateMsg = "Duplicate extension " + type.getName() + " name " + name + " on " + c.getName() + " and " + clazz.getName();
            logger.error(duplicateMsg);
            throw new IllegalStateException(duplicateMsg);
        }
    }

    /**
     * cache Activate class which is annotated with <code>Activate</code>
     * <p>
     * for compatibility, also cache class with old alibaba Activate annotation
     */
    private void cacheActivateClass(Class<?> clazz, String name) { //缓存扩展名与@Activate对象的映射
        Activate activate = clazz.getAnnotation(Activate.class);
        if (activate != null) {
            cachedActivates.put(name, activate);
        } else {//代码做版本兼容处理
            // support com.alibaba.dubbo.common.extension.Activate
            com.alibaba.dubbo.common.extension.Activate oldActivate = clazz.getAnnotation(com.alibaba.dubbo.common.extension.Activate.class);
            if (oldActivate != null) {
                cachedActivates.put(name, oldActivate);
            }
        }
    }

    /**
     * cache Adaptive class which is annotated with <code>Adaptive</code>
     */
    private void cacheAdaptiveClass(Class<?> clazz, boolean overridden) {
        if (cachedAdaptiveClass == null || overridden) { //若缓存中自适应扩展类为空，或自适应扩展类不为空且允许覆盖时，更新缓存中自适应扩展类
            cachedAdaptiveClass = clazz;
        } else if (!cachedAdaptiveClass.equals(clazz)) { //若缓存中自适应扩展类不为空，且不允许覆盖时，则抛出异常，一个扩展类最多对应一个自适应扩展类
            throw new IllegalStateException("More than 1 adaptive class found: "
                    + cachedAdaptiveClass.getName()
                    + ", " + clazz.getName());
        }
    }

    /**
     * cache wrapper class
     * <p>
     * like: ProtocolFilterWrapper, ProtocolListenerWrapper
     */
    private void cacheWrapperClass(Class<?> clazz) { //缓存封装类型的扩展类
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    /**
     * test if clazz is a wrapper class
     * <p>
     * which has Constructor with given class type as its only argument
     * （判断是否有把扩展接口作为唯一参数的构造函数）
     */
    private boolean isWrapperClass(Class<?> clazz) { //如org.apache.dubbo.rpc.protocol.dubbo.filter.TraceFilter，需要看是否有如 TraceFilter(Filter filter)的构造函数
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 查找或构建缺失的扩展名
     * 具体场景：通过SPI配置扩展信息时，未填写扩展名
     * 对应逻辑：
     * 1）判断扩展类型上是否带有注解@Extension，若有去注解中的value值（@Extension已被废弃，不推荐使用）
     * 2）获取扩展类的名称，如AvailableCluster的处理方式：
     *    a）先把SPI名字去掉，即AvailableCluster去掉Cluster
     *    b）然后把得到的名称转换为小写
     */
    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        org.apache.dubbo.common.Extension extension = clazz.getAnnotation(org.apache.dubbo.common.Extension.class);
        if (extension != null) { //若是使用@Extension注解的，则取注解上的值作为扩展名
            return extension.value();
        }

        String name = clazz.getSimpleName();
        if (name.endsWith(type.getSimpleName())) {
            name = name.substring(0, name.length() - type.getSimpleName().length()); //截取扩展类的名称，作为扩展名
        }
        return name.toLowerCase(); //将扩展名小写（若扩展名没有以SPI接口名结尾，则直接将扩展名转为小写，即始终都有扩展名）
    }

    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() { //产生自适应类（对应的扩展实例，在自适应对象调用时，根据入参动态选择实例）
        try { //自适应扩展类只使用IOC功能，没有使用AOP功能
            return injectExtension((T) getAdaptiveExtensionClass().newInstance()); //创建自适应类（调用无参的构造方法）
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    private Class<?> getAdaptiveExtensionClass() { //获取自适应扩展类（若存在直接返回，否则构建代码，并编译为对应Class）
        getExtensionClasses();
        if (cachedAdaptiveClass != null) { //若配置文件中配置了自适应扩展类，就直接使用，不用产生自适应扩展代码了（即类上带有@Adaptive注解，如AdaptiveExtensionFactory）
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass(); //@csy-011 何时会进入该逻辑？解：当配置文件中没设置自适应扩展类时进入
    }

    /**
     * 创建自适应扩展类
     * 1）产生自适应类对应的代码字符串
     * 2）找到适合的编译器对代码字符串进行编译，生成对应的Class
     */
    private Class<?> createAdaptiveExtensionClass() {
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate(); //产生自适应代码对应的字符串（调试时，可以将产生的自适应代码打印出来）
        ClassLoader classLoader = findClassLoader();
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
