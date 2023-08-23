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
package org.apache.dubbo.common.utils;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * PojoUtils. Travel object deeply, and convert complex type to simple type. (深度遍历对象，并将复杂类型转换为简单类型),同时也提供了反向转换。
 * <p/>
 * Simple type below will be remained:（如下类型为简单类型）
 * <ul>
 * <li> Primitive Type, also include <b>String</b>, <b>Number</b>(Integer, Long), <b>Date</b>
 * <li> Array of Primitive Type（元素为基本类型的数组）
 * <li> Collection, eg: List, Map, Set etc.（集合类型）
 * </ul>
 * <p/>
 * Other type will be covert to a map which contains the attributes and value pair of object.（其它的类型，将被转换为Map形式）
 */
public class PojoUtils { //@csy-023-P1 该类的功能用途是什么？ PojoUtils 提供了将复杂对象转换为简单的对象，通过简单对象转换为复杂对象。有点像序列化和反序列
     /**
     * POJO（Plain Ordinary Java Object）简单的Java对象，实际就是普通JavaBeans，是为了避免和EJB混淆所创造的简称。
     * 使用POJO名称是为了避免和EJB混淆起来, 而且简称比较直接. 其中有一些属性及其getter setter方法的类,没有业务逻辑
     * <p>
     * JavaBean则比POJO复杂很多， Java Bean是可复用的组件，对Java Bean 并没有严格的规范，理论上讲，任何一个 Java 类都可以是一个 Bean
     */

    private static final Logger logger = LoggerFactory.getLogger(PojoUtils.class);
    private static final ConcurrentMap<String, Method> NAME_METHODS_CACHE = new ConcurrentHashMap<String, Method>(); //方法名与Method的缓存（为了减少反射获取Method调用），key的值用类名和参数类型拼接，如："org.apache.dubbo.common.model.Person.setName(java.lang.String)"
    private static final ConcurrentMap<Class<?>, ConcurrentMap<String, Field>> CLASS_FIELD_CACHE = new ConcurrentHashMap<Class<?>, ConcurrentMap<String, Field>>(); //字段所在类Class、字段名、字段信息Filed的缓存
    private static final boolean GENERIC_WITH_CLZ = Boolean.parseBoolean(ConfigUtils.getProperty(CommonConstants.GENERIC_WITH_CLZ_KEY, "true"));

    public static Object[] generalize(Object[] objs) {
        Object[] dests = new Object[objs.length];
        for (int i = 0; i < objs.length; i++) { //遍历数组依次按pojo对象转换
            dests[i] = generalize(objs[i]);
        }
        return dests;
    }

    public static Object[] realize(Object[] objs, Class<?>[] types) {
        if (objs.length != types.length) {
            throw new IllegalArgumentException("args.length != types.length");
        }

        Object[] dests = new Object[objs.length];
        for (int i = 0; i < objs.length; i++) {
            dests[i] = realize(objs[i], types[i]);
        }

        return dests;
    }

    public static Object[] realize(Object[] objs, Class<?>[] types, Type[] gtypes) {
        if (objs.length != types.length || objs.length != gtypes.length) { //传入的参数数目与调用方法的参数个数不相等异常
            throw new IllegalArgumentException("args.length != types.length");
        }
        Object[] dests = new Object[objs.length];
        for (int i = 0; i < objs.length; i++) {
            dests[i] = realize(objs[i], types[i], gtypes[i]);
        }
        return dests;
    }

    public static Object generalize(Object pojo) { //将pojo对象的内容进行概括（如：将pojo转换为Map，generalize：概括、归纳）
        return generalize(pojo, new IdentityHashMap<Object, Object>()); //IdentityHashMap的key使用==来查找key的
    }

    @SuppressWarnings("unchecked")
    private static Object generalize(Object pojo, Map<Object, Object> history) { //pojo对象的成员属性（会递归转换，pojo=》Map，直到所有属性为dubbo定义的基本类型）
        if (pojo == null) {
            return null;
        }

        if (pojo instanceof Enum<?>) {
            return ((Enum<?>) pojo).name(); //枚举类型，输出枚举的名称
        }
        if (pojo.getClass().isArray() && Enum.class.isAssignableFrom(pojo.getClass().getComponentType())) { //处理Enum数组
            int len = Array.getLength(pojo);
            String[] values = new String[len];
            for (int i = 0; i < len; i++) { //枚举数组会转换为String数组，数组元素的值为枚举名
                values[i] = ((Enum<?>) Array.get(pojo, i)).name();
            }
            return values;
        }

        if (ReflectUtils.isPrimitives(pojo.getClass())) { //基本类型直接返回，不做处理
            return pojo;
        }

        if (pojo instanceof Class) { //Class类的实例，返回类名称
            return ((Class) pojo).getName();
        }

        Object o = history.get(pojo);
        if (o != null) {
            return o;
        }
        history.put(pojo, pojo);

        if (pojo.getClass().isArray()) { //pojo对象为数组类型
            int len = Array.getLength(pojo);
            Object[] dest = new Object[len];
            history.put(pojo, dest);
            for (int i = 0; i < len; i++) {
                Object obj = Array.get(pojo, i);
                dest[i] = generalize(obj, history);
            }
            return dest;
        }
        if (pojo instanceof Collection<?>) { //pojo对象为集合类型
            Collection<Object> src = (Collection<Object>) pojo;
            int len = src.size();
            Collection<Object> dest = (pojo instanceof List<?>) ? new ArrayList<Object>(len) : new HashSet<Object>(len); //区分出List或Set类型，并创建对应的集合实例
            history.put(pojo, dest);
            for (Object obj : src) { //遍历集合元素，依次将元素进行转换
                dest.add(generalize(obj, history));
            }
            return dest;
        }
        if (pojo instanceof Map<?, ?>) { //pojo对象为Map类型
            Map<Object, Object> src = (Map<Object, Object>) pojo;
            Map<Object, Object> dest = createMap(src); //根据原Map类型，创建对应的Map对象
            history.put(pojo, dest);
            for (Map.Entry<Object, Object> obj : src.entrySet()) {
                dest.put(generalize(obj.getKey(), history), generalize(obj.getValue(), history)); //key、value都可能是pojo对象，所以都需要进行转换
            }
            return dest;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        history.put(pojo, map);
        if (GENERIC_WITH_CLZ) {
            map.put("class", pojo.getClass().getName()); //设置pojo对象的Class类
        }
        for (Method method : pojo.getClass().getMethods()) {
            if (ReflectUtils.isBeanPropertyReadMethod(method)) { //判断是否读取bean的方法（即get/is方法）
                try {
                    /**
                     * 处理步骤：
                     * 1）从方法名中获取到属性名
                     * 2）调用pojo对应的方法获取值，并通过generalize转换（传入history是做临时缓存，若能从history取到则用之）
                     * 3）将属性名和值设置到map中
                     */
                    map.put(ReflectUtils.getPropertyNameFromBeanReadMethod(method), generalize(method.invoke(pojo), history));
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
        // public field
        for (Field field : pojo.getClass().getFields()) {
            if (ReflectUtils.isPublicInstanceField(field)) { //判断是否是公共的实例字段
                try {
                    Object fieldValue = field.get(pojo);
                    if (history.containsKey(pojo)) {
                        Object pojoGeneralizedValue = history.get(pojo); //已经转换过的字段，就不再转换
                        if (pojoGeneralizedValue instanceof Map
                                && ((Map) pojoGeneralizedValue).containsKey(field.getName())) {
                            continue;
                        }
                    }
                    if (fieldValue != null) {
                        map.put(field.getName(), generalize(fieldValue, history));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
        return map;
    }

    public static Object realize(Object pojo, Class<?> type) { //将简单对象转换为指定类型的复杂对象
        return realize0(pojo, type, null, new IdentityHashMap<Object, Object>());
    }

    public static Object realize(Object pojo, Class<?> type, Type genericType) { //将对象转换为指定类型的对象（如：将Map对象转换为pojo对象）
        return realize0(pojo, type, genericType, new IdentityHashMap<Object, Object>());
    }

    private static class PojoInvocationHandler implements InvocationHandler {

        private Map<Object, Object> map; //在创建代理对象时指定的

        public PojoInvocationHandler(Map<Object, Object> map) {
            this.map = map;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable { //为接口生成代理对象，在调用接口方法时，会回调该方法
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(map, args);
            }
            String methodName = method.getName();
            Object value = null;
            if (methodName.length() > 3 && methodName.startsWith("get")) {
                value = map.get(methodName.substring(3, 4).toLowerCase() + methodName.substring(4)); //截取方法名，获取属性名，从map中获取相应的值
            } else if (methodName.length() > 2 && methodName.startsWith("is")) {
                value = map.get(methodName.substring(2, 3).toLowerCase() + methodName.substring(3));
            } else {
                value = map.get(methodName.substring(0, 1).toLowerCase() + methodName.substring(1));
            }
            if (value instanceof Map<?, ?> && !Map.class.isAssignableFrom(method.getReturnType())) {
                value = realize0((Map<String, Object>) value, method.getReturnType(), null, new IdentityHashMap<Object, Object>());
            }
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> createCollection(Class<?> type, int len) { //创建指定类型集合
        if (type.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<Object>(len);
        }
        if (type.isAssignableFrom(HashSet.class)) {
            return new HashSet<Object>(len);
        }
        if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
            try {
                return (Collection<Object>) type.newInstance();
            } catch (Exception e) {
                // ignore
            }
        }
        return new ArrayList<Object>(); //若上面都没有创建集合，则默认使用ArrayList创建集合
    }

    private static Map createMap(Map src) { //判断Map的实际类型，创建对应类型的实例
        Class<? extends Map> cl = src.getClass();
        Map result = null;
        if (HashMap.class == cl) {
            result = new HashMap();
        } else if (Hashtable.class == cl) {
            result = new Hashtable();
        } else if (IdentityHashMap.class == cl) {
            result = new IdentityHashMap();
        } else if (LinkedHashMap.class == cl) {
            result = new LinkedHashMap();
        } else if (Properties.class == cl) {
            result = new Properties();
        } else if (TreeMap.class == cl) {
            result = new TreeMap();
        } else if (WeakHashMap.class == cl) {
            return new WeakHashMap();
        } else if (ConcurrentHashMap.class == cl) {
            result = new ConcurrentHashMap();
        } else if (ConcurrentSkipListMap.class == cl) {
            result = new ConcurrentSkipListMap();
        } else {
            try {
                result = cl.newInstance(); //不为上面的Map类型，可能为不常用的Map，或自定义的Map，利用反射创建实例
            } catch (Exception e) { /* ignore */ }

            if (result == null) {
                try {
                    Constructor<?> constructor = cl.getConstructor(Map.class);
                    result = (Map) constructor.newInstance(Collections.EMPTY_MAP); //若上面方式没创建Map实例，则创建EmptyMap作为默认实例（可能上面创建的自定义Map，没有定义无参的构造方法）
                } catch (Exception e) { /* ignore */ }
            }
        }

        if (result == null) {
            result = new HashMap<Object, Object>();
        }

        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * realize：实现，获得
     */
    private static Object realize0(Object pojo, Class<?> type, Type genericType, final Map<Object, Object> history) { //将简单类型转换为复杂类型（如：将Map对象转换为指定类型的pojo对象，将Map的属性值，通过set方法设置到pojo对象中，type为目标类型）
        if (pojo == null) {
            return null;
        }

        if (type != null && type.isEnum() && pojo.getClass() == String.class) { //将String转换为枚举类型
            return Enum.valueOf((Class<Enum>) type, (String) pojo);
        }

        if (ReflectUtils.isPrimitives(pojo.getClass())
                && !(type != null && type.isArray()
                && type.getComponentType().isEnum()
                && pojo.getClass() == String[].class)) { //将String数组转换为枚举数组
            return CompatibleTypeUtils.compatibleTypeConvert(pojo, type);
        }

        Object o = history.get(pojo); //history：当方法在递归调用时，会用到（用缓存使用）

        if (o != null) {
            return o;
        }

        history.put(pojo, pojo);

        if (pojo.getClass().isArray()) { //处理数组类型的pojo
            if (Collection.class.isAssignableFrom(type)) { //目标类型是集合类型
                Class<?> ctype = pojo.getClass().getComponentType(); //获取数组元素的类型
                int len = Array.getLength(pojo); //获取数组对应长度
                Collection dest = createCollection(type, len);
                history.put(pojo, dest);
                for (int i = 0; i < len; i++) {
                    Object obj = Array.get(pojo, i); //返回数组中指定下标的值
                    Object value = realize0(obj, ctype, null, history); //依次将对象转换为目标类型，如Map转换为pojo类型
                    dest.add(value);
                }
                return dest;
            } else {
                Class<?> ctype = (type != null && type.isArray() ? type.getComponentType() : pojo.getClass().getComponentType());
                int len = Array.getLength(pojo);
                Object dest = Array.newInstance(ctype, len);
                history.put(pojo, dest);
                for (int i = 0; i < len; i++) {
                    Object obj = Array.get(pojo, i);
                    Object value = realize0(obj, ctype, null, history);
                    Array.set(dest, i, value);
                }
                return dest;
            }
        }

        if (pojo instanceof Collection<?>) { //处理集合类型的pojo
            if (type.isArray()) { //集合转换为数组
                Class<?> ctype = type.getComponentType();
                Collection<Object> src = (Collection<Object>) pojo;
                int len = src.size();
                Object dest = Array.newInstance(ctype, len); //创建指定类型和长度的数组
                history.put(pojo, dest);
                int i = 0;
                for (Object obj : src) {
                    Object value = realize0(obj, ctype, null, history); //将数组中的元素依次转换为指定类型pojo对象
                    Array.set(dest, i, value);
                    i++;
                }
                return dest;
            } else {
                Collection<Object> src = (Collection<Object>) pojo;
                int len = src.size();
                Collection<Object> dest = createCollection(type, len);
                history.put(pojo, dest);
                for (Object obj : src) { //遍历集合元素，依次转化到目标类型的pojo对象
                    Type keyType = getGenericClassByIndex(genericType, 0);
                    Class<?> keyClazz = obj == null ? null : obj.getClass();
                    if (keyType instanceof Class) {
                        keyClazz = (Class<?>) keyType;
                    }
                    Object value = realize0(obj, keyClazz, keyType, history);
                    dest.add(value);
                }
                return dest;
            }
        }

        if (pojo instanceof Map<?, ?> && type != null) { //处理Map类型的pojo（JSONObject：JSON对象，实现了Map接口，也属于Map的实例对象，所以会进入此处）
            Object className = ((Map<Object, Object>) pojo).get("class"); //获取Map中的"class"键对应的值，是在generalize方法中设置的（单个的pojo中设置的）
            if (className instanceof String) {
                try {
                    type = ClassUtils.forName((String) className); //解析的目标类的Class类
                } catch (ClassNotFoundException e) {
                    // ignore
                }
            }

            // special logic for enum
            if (type.isEnum()) { //目标类型为枚举
                Object name = ((Map<Object, Object>) pojo).get("name"); //取出枚举名称
                if (name != null) {
                    return Enum.valueOf((Class<Enum>) type, name.toString()); //使用枚举名，构建枚举对象
                }
            }
            Map<Object, Object> map;
            // when return type is not the subclass of return type from the signature and not an interface
            if (!type.isInterface() && !type.isAssignableFrom(pojo.getClass())) { //type非接口且pojo不是type的子类型
                try {
                    map = (Map<Object, Object>) type.newInstance();
                    Map<Object, Object> mapPojo = (Map<Object, Object>) pojo;
                    map.putAll(mapPojo);
                    if (GENERIC_WITH_CLZ) {
                        map.remove("class");
                    }
                } catch (Exception e) {
                    //ignore error
                    map = (Map<Object, Object>) pojo; //type类型不为Map时，使用原始的pojo转换
                }
            } else {
                map = (Map<Object, Object>) pojo; //直接强转为Map类型
            }

            if (Map.class.isAssignableFrom(type) || type == Object.class) { //解析的目标类为Map时
                final Map<Object, Object> result;
                // fix issue#5939
                Type mapKeyType = getKeyTypeForMap(map.getClass()); //获取key的泛型参数对应的实际类型
                Type typeKeyType = getGenericClassByIndex(genericType, 0); //获取目标类型的泛型参数的第一个实际参数
                boolean typeMismatch = mapKeyType instanceof Class //Type为Class实例时，表明是基本类型或原始类型，如String、int等
                        && typeKeyType instanceof Class
                        && !typeKeyType.getTypeName().equals(mapKeyType.getTypeName()); //判断key、value类型为基本类型或原始类型，且类型相同
                if (typeMismatch) { //输入对象的key与目标Map的key类型不匹配是，创建新的Map
                    result = createMap(new HashMap(0));
                } else {
                    result = createMap(map); //类型匹配时，直接使用目标Map
                }

                history.put(pojo, result);
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    Type keyType = getGenericClassByIndex(genericType, 0);
                    Type valueType = getGenericClassByIndex(genericType, 1);
                    Class<?> keyClazz;
                    if (keyType instanceof Class) { //基本类型或原始类型
                        keyClazz = (Class<?>) keyType;
                    } else if (keyType instanceof ParameterizedType) { //参数化类型
                        keyClazz = (Class<?>) ((ParameterizedType) keyType).getRawType();
                    } else { //keyType为Null时，取条目中key的类型
                        keyClazz = entry.getKey() == null ? null : entry.getKey().getClass();
                    }
                    Class<?> valueClazz;
                    if (valueType instanceof Class) {
                        valueClazz = (Class<?>) valueType;
                    } else if (valueType instanceof ParameterizedType) {
                        valueClazz = (Class<?>) ((ParameterizedType) valueType).getRawType();
                    } else {
                        valueClazz = entry.getValue() == null ? null : entry.getValue().getClass();
                    }

                    Object key = keyClazz == null ? entry.getKey() : realize0(entry.getKey(), keyClazz, keyType, history); //将key转换为目标类型
                    Object value = valueClazz == null ? entry.getValue() : realize0(entry.getValue(), valueClazz, valueType, history); //将value转换为目标类型
                    result.put(key, value);
                }
                return result;
            } else if (type.isInterface()) { //解析的目标类为接口时，产生接口对应的代理类
                Object dest = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[] {type}, new PojoInvocationHandler(map)); //使用jdk代理机制为接口创代理对象，并指定处理器PojoInvocationHandler，在接口方法被调用时，就会触发处理器中方法执行指定逻辑
                history.put(pojo, dest);
                return dest;
            } else {
                Object dest = newInstance(type); //构造解析的目标类的实例
                history.put(pojo, dest); //设置到缓存Map中
                for (Map.Entry<Object, Object> entry : map.entrySet()) { //遍历Map中的条目，找到对应目标对象的属性，使用反射机制调用Method，依次设置值
                    Object key = entry.getKey();
                    if (key instanceof String) { //只处理key为String的条目
                        String name = (String) key;
                        Object value = entry.getValue();
                        if (value != null) {
                            Method method = getSetterMethod(dest.getClass(), name, value.getClass()); //通过属性获取set方法（从缓存中获取，若没有则通过反射获取Method，再设置到缓存中）
                            Field field = getField(dest.getClass(), name); //获取属性对应的字段Filed信息
                            if (method != null) {
                                if (!method.isAccessible()) {
                                    method.setAccessible(true);
                                }
                                Type ptype = method.getGenericParameterTypes()[0]; //获取set方法的第一个参数类型
                                value = realize0(value, method.getParameterTypes()[0], ptype, history); //将值转换为指定类型的对象
                                try {
                                    method.invoke(dest, value); //使用反射机制，设置目标对象的属性值
                                } catch (Exception e) {
                                    String exceptionDescription = "Failed to set pojo " + dest.getClass().getSimpleName() + " property " + name
                                            + " value " + value + "(" + value.getClass() + "), cause: " + e.getMessage();
                                    logger.error(exceptionDescription, e);
                                    throw new RuntimeException(exceptionDescription, e);
                                }
                            } else if (field != null) {
                                value = realize0(value, field.getType(), field.getGenericType(), history);
                                try {
                                    field.set(dest, value);
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException("Failed to set field " + name + " of pojo " + dest.getClass().getName() + " : " + e.getMessage(), e);
                                }
                            }
                        }
                    }
                }
                if (dest instanceof Throwable) { //异常信息处理
                    Object message = map.get("message");
                    if (message instanceof String) {
                        try {
                            Field field = Throwable.class.getDeclaredField("detailMessage");
                            if (!field.isAccessible()) {
                                field.setAccessible(true);
                            }
                            field.set(dest, message);
                        } catch (Exception e) {
                        }
                    }
                }
                return dest;
            }
        }
        return pojo;
    }

     /**
     * Get key type for {@link Map} directly implemented by {@code clazz}.
     * If {@code clazz} does not implement {@link Map} directly, return {@code null}.
     *
     * @param clazz {@link Class}
     * @return Return String.class for {@link com.alibaba.fastjson.JSONObject}
     */
    private static Type getKeyTypeForMap(Class<?> clazz) { //获取key的泛型参数对应的实际类型
        Type[] interfaces = clazz.getGenericInterfaces(); //获取Class类直接实现的结果
        if (!ArrayUtils.isEmpty(interfaces)) {
            for (Type type : interfaces) {
                if (type instanceof ParameterizedType) { //处理泛型
                    ParameterizedType t = (ParameterizedType) type;
                    if ("java.util.Map".equals(t.getRawType().getTypeName())) {
                        return t.getActualTypeArguments()[0]; //获取Map中key对应的实际类型
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get parameterized type
     *
     * @param genericType generic type
     * @param index       index of the target parameterized type
     * @return Return Person.class for List<Person>, return Person.class for Map<String, Person> when index=0
     */
    private static Type getGenericClassByIndex(Type genericType, int index) { //获取泛型参数指定位置index的实际参数类型
        Type clazz = null;
        // find parameterized type
        if (genericType instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType) genericType;
            Type[] types = t.getActualTypeArguments(); //泛型参数的时间参数，可能有多个，如Map<K,V>，就有两个实际参数
            clazz = types[index];
        }
        return clazz;
    }

    private static Object newInstance(Class<?> cls) { //创建指定类型的对象实例
        try {
            return cls.newInstance();
        } catch (Throwable t) { //若指定类中没有无参的构造函数，就会抛出异常。做容错处理，找出构造器Constructor，设置默认参数时，进行实例构造
            try {
                Constructor<?>[] constructors = cls.getDeclaredConstructors();
                /**
                 * From Javadoc java.lang.Class#getDeclaredConstructors
                 * This method returns an array of Constructor objects reflecting all the constructors
                 * declared by the class represented by this Class object.
                 * This method returns an array of length 0,
                 * if this Class object represents an interface, a primitive type, an array class, or void.
                 */
                if (constructors.length == 0) {
                    throw new RuntimeException("Illegal constructor: " + cls.getName());
                }
                Constructor<?> constructor = constructors[0]; //取第一个构造器即可（此处主要是创造对象实例，至于用哪个构造器都可）
                if (constructor.getParameterTypes().length > 0) {
                    for (Constructor<?> c : constructors) {
                        if (c.getParameterTypes().length < constructor.getParameterTypes().length) {
                            constructor = c;
                            if (constructor.getParameterTypes().length == 0) {
                                break;
                            }
                        }
                    }
                }
                constructor.setAccessible(true);
                Object[] parameters = Arrays.stream(constructor.getParameterTypes()).map(PojoUtils::getDefaultValue).toArray(); //使用默认值作为构造参数
                return constructor.newInstance(parameters);
            } catch (InstantiationException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (IllegalAccessException e) {
                /**
                 * invoke调用时若有枚举类型的参数，若invoke hello({"name":"APPLE","class":"org.apache.dubbo.demo.FruitEnum"})，
                 * 填写的name、class有错，会抛出java.lang.IllegalArgumentException: Cannot reflectively create enum objects
                 */
                throw new RuntimeException(e.getMessage(), e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    /**
     * return init value
     *
     * @param parameterType
     * @return
     */
    private static Object getDefaultValue(Class<?> parameterType) {
        if ("char".equals(parameterType.getName())) {
            return Character.MIN_VALUE;
        }
        if ("bool".equals(parameterType.getName())) {
            return false;
        }
        return parameterType.isPrimitive() ? 0 : null;
    }

    private static Method getSetterMethod(Class<?> cls, String property, Class<?> valueCls) {
        String name = "set" + property.substring(0, 1).toUpperCase() + property.substring(1); //构建方法名，如setAge
        Method method = NAME_METHODS_CACHE.get(cls.getName() + "." + name + "(" + valueCls.getName() + ")"); //构建缓存key，尝试缓存中获取Method
        if (method == null) { //缓存中没有，则通过反射，获取到Method（使用缓存，减少反射的调用）
            try {
                method = cls.getMethod(name, valueCls);
            } catch (NoSuchMethodException e) {
                for (Method m : cls.getMethods()) {
                    if (ReflectUtils.isBeanPropertyWriteMethod(m) && m.getName().equals(name)) {
                        method = m;
                        break;
                    }
                }
            }
            if (method != null) {
                NAME_METHODS_CACHE.put(cls.getName() + "." + name + "(" + valueCls.getName() + ")", method);
            }
        }
        return method;
    }

    private static Field getField(Class<?> cls, String fieldName) {
        Field result = null;
        if (CLASS_FIELD_CACHE.containsKey(cls) && CLASS_FIELD_CACHE.get(cls).containsKey(fieldName)) { //判断缓存中是否存在指定类对应的字段
            return CLASS_FIELD_CACHE.get(cls).get(fieldName);
        }
        try {
            result = cls.getDeclaredField(fieldName);
            result.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field field : cls.getFields()) {
                if (fieldName.equals(field.getName()) && ReflectUtils.isPublicInstanceField(field)) {
                    result = field;
                    break;
                }
            }
        }
        if (result != null) {
            ConcurrentMap<String, Field> fields = CLASS_FIELD_CACHE.computeIfAbsent(cls, k -> new ConcurrentHashMap<>());
            fields.putIfAbsent(fieldName, result);
        }
        return result;
    }

    public static boolean isPojo(Class<?> cls) { //判断是否是pojo类型，非基本类型、且非集合类型、且非Map类型
        return !ReflectUtils.isPrimitives(cls)
                && !Collection.class.isAssignableFrom(cls)
                && !Map.class.isAssignableFrom(cls);
    }

    /**
     * Update the property if absent
     * （如果属性值不存在的话，则更新值）
     *
     * @param getterMethod the getter method
     * @param setterMethod the setter method
     * @param newValue     the new value
     * @param <T>          the value type
     * @since 2.7.8
     */
    public static <T> void updatePropertyIfAbsent(Supplier<T> getterMethod, Consumer<T> setterMethod, T newValue) {
        if (newValue != null && getterMethod.get() == null) {
            setterMethod.accept(newValue);
        }
    }

}
