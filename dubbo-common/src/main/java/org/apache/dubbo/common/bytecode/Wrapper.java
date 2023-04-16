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
package org.apache.dubbo.common.bytecode;

import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.ReflectUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

/**
 * Wrapper.
 */
public abstract class Wrapper { //封装类
    /**
     * 包装类，封装类的创建以及使用点是怎样的？
     * 解：Wrapper用于“包裹”目标类，Wrapper是一个抽象类，仅可通过 getWrapper(Class) 方法创建子类。在创建Wrapper子类的过程中，
     * 子类代码生成逻辑会对getWrapper方法传入的Class对象进行解析，拿到诸如类方法，类成员变量等信息。以及生成 invokeMethod
     * 方法代码和其他一些方法代码。代码生成完毕后，通过 Javassist 生成 Class 对象，最后再通过反射创建Wrapper实例
     */
    private static final Map<Class<?>, Wrapper> WRAPPER_MAP = new ConcurrentHashMap<Class<?>, Wrapper>(); //class wrapper map：类与Wrapper的缓存，当需要执行调用时，根据Class即可找到Wrapper，然后通过Wrapper调用目标对象中方法，减少反射调用
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String[] OBJECT_METHODS = new String[] {"getClass", "hashCode", "toString", "equals"};
    private static final Wrapper OBJECT_WRAPPER = new Wrapper() { //Object对应的封装类
        @Override
        public String[] getMethodNames() { //匿名内部类，对应实现抽象方法
            return OBJECT_METHODS;
        }

        @Override
        public String[] getDeclaredMethodNames() {
            return OBJECT_METHODS;
        }

        @Override
        public String[] getPropertyNames() {
            return EMPTY_STRING_ARRAY;
        }

        @Override
        public Class<?> getPropertyType(String pn) { //Object的属性类型都返回null
            return null;
        }

        @Override
        public Object getPropertyValue(Object instance, String pn) throws NoSuchPropertyException {
            throw new NoSuchPropertyException("Property [" + pn + "] not found.");
        }

        @Override
        public void setPropertyValue(Object instance, String pn, Object pv) throws NoSuchPropertyException {
            throw new NoSuchPropertyException("Property [" + pn + "] not found.");
        }

        @Override
        public boolean hasProperty(String name) {
            return false;
        }

        @Override
        public Object invokeMethod(Object instance, String mn, Class<?>[] types, Object[] args) throws NoSuchMethodException {
            if ("getClass".equals(mn)) { //根据方法名选择执行不同的方法
                return instance.getClass();
            }
            if ("hashCode".equals(mn)) { // Object的封装类Wrapper，只对部分的方法做处理，如getClass()、hashCode()等
                return instance.hashCode();
            }
            if ("toString".equals(mn)) {
                return instance.toString();
            }
            if ("equals".equals(mn)) {
                if (args.length == 1) {
                    return instance.equals(args[0]);
                }
                throw new IllegalArgumentException("Invoke method [" + mn + "] argument number error.");
            }
            throw new NoSuchMethodException("Method [" + mn + "] not found.");
        }
    };
    private static AtomicLong WRAPPER_CLASS_COUNTER = new AtomicLong(0);

    /**
     * get wrapper.（获取指定Class对象的封装类）
     *
     * @param c Class instance.
     * @return Wrapper instance(not null).
     */
    public static Wrapper getWrapper(Class<?> c) { //获取Wrapper的实例（先从缓存中获取，若没有则对应创建）
        while (ClassGenerator.isDynamicClass(c)) // can not wrapper on dynamic class.
        {
            c = c.getSuperclass(); //不能封装动态类，动态类取它的父类进行封装
        }

        if (c == Object.class) { //Object 返回默认的对象封装类
            return OBJECT_WRAPPER;
        }

        return WRAPPER_MAP.computeIfAbsent(c, key -> makeWrapper(key)); //构建封装类，并设置到缓存中，key的值与c相同
    }

    /**
     * 创建封装类的问题点
     * 1）创建的封装类，做了哪些功能增强，还是说只是为了减少反射调用，只实现了目标类的方法调用？
     * 2）本地方法调用，底层原理是怎样的？是不是class的invoke方法
     */
    private static Wrapper makeWrapper(Class<?> c) { //为指定class构建Wrapper封装类的实例，c的实例如：org.apache.dubbo.demo.provider.GreetingServiceImpl
        if (c.isPrimitive()) { //基本类型不能创建封装类
            throw new IllegalArgumentException("Can not create wrapper for primitive type: " + c);
        }

        String name = c.getName(); //被封装的类的全限定名，如：org.apache.dubbo.demo.GreetingService
        ClassLoader cl = ClassUtils.getClassLoader(c); //获取类加载器

        // 拼接类代码对应的字符串 (对应Wrapper类中的抽象方法)
        StringBuilder c1 = new StringBuilder("public void setPropertyValue(Object o, String n, Object v){ "); //构建当前类中的setPropertyValue()抽象方法
        StringBuilder c2 = new StringBuilder("public Object getPropertyValue(Object o, String n){ ");
        StringBuilder c3 = new StringBuilder("public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws " + InvocationTargetException.class.getName() + "{ ");

        c1.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }"); //将setPropertyValue方法中的Object强制转化为具体类型，如：(org.apache.dubbo.demo.GreetingService)$1
        c2.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }"); //将getPropertyValue方法中的Object强制转化为具体类型
        c3.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }"); //将invokeMethod方法中的Object强制转化为具体类型

        Map<String, Class<?>> pts = new HashMap<>(); // <property name, property types>
        Map<String, Method> ms = new LinkedHashMap<>(); // <method desc, Method instance> 方法对应的描述符与方法实例的映射Map
        List<String> mns = new ArrayList<>(); // method names. 方法名列表
        List<String> dmns = new ArrayList<>(); // declaring method names. 被封装的类或接口中，声明的方法名列表

        // get all public field.
        for (Field f : c.getFields()) { //处理被封装类的所有public字段
            String fn = f.getName(); //获取字段名称
            Class<?> ft = f.getType(); //获取字段类型，如 java.lang.String
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) { //static、transient修饰的字段不处理（接口中的字段，都是public static final字段，所以不会处理，那这里处理就是对类封装时处理）
                continue;
            }

            c1.append(" if( $2.equals(\"").append(fn).append("\") ){ w.").append(fn).append("=").append(arg(ft, "$3")).append("; return; }"); //通过setPropertyValue方法，为目标对象设置成员属性的值，如：if( $2.equals("employeeName") ){ w.employeeName=(java.lang.String)$3;
            c2.append(" if( $2.equals(\"").append(fn).append("\") ){ return ($w)w.").append(fn).append("; }"); //通过getPropertyValue方法，获取目标对象的成员变量值，如：if( $2.equals("employeeName") ){ return ($w)w.employeeName; }
            pts.put(fn, ft); //设置成员属性名与属性类型的关系，如Map<"employeeName,"java.lang.String">
        }

        Method[] methods = c.getMethods();
        // get all public method.
        boolean hasMethod = hasMethods(methods); //处理被封装类的所有public方法（判断是否有非Object中的方法）
        if (hasMethod) { //存在方法时处理（把被封装的类或接口中的声明方法，依次拼接起来）
            c3.append(" try{");
            for (Method m : methods) { //对类中的方法依次封装处理（构造Wrapper中的invokeMethod方法，如org.apache.dubbo.demo.GreetingService中声明中的所有方法）
                //ignore Object's method.（忽略Object对象中的方法）
                if (m.getDeclaringClass() == Object.class) {
                    continue;
                }

                String mn = m.getName();
                c3.append(" if( \"").append(mn).append("\".equals( $2 ) "); //$2指当前类中的invokeMethod()的第二个参数（比较方法名称）
                int len = m.getParameterTypes().length;
                c3.append(" && ").append(" $3.length == ").append(len);// 比较方法参数个数（需要方法名称和参数个数都相等）

                boolean override = false; //判断同一个接口或类中是存在重载的方法
                for (Method m2 : methods) { //按方法名，判断是否重写
                    if (m != m2 && m.getName().equals(m2.getName())) {
                        override = true;
                        break;
                    }
                }
                if (override) { //若有重载的方法（只按方法名称不能匹配出方法，还得按参数类型进行匹配）
                    if (len > 0) { //方法参数个数
                        for (int l = 0; l < len; l++) { //
                            c3.append(" && ").append(" $3[").append(l).append("].getName().equals(\"")
                                    .append(m.getParameterTypes()[l].getName()).append("\")");
                        }
                    }
                }

                c3.append(" ) { "); //组装出判断条件，如：if( "hello".equals( $2 )  &&  $3.length == 1 &&  $3[0].getName().equals("org.apache.dubbo.demo.Fruit"))

                if (m.getReturnType() == Void.TYPE) { //返回类型为void
                    c3.append(" w.").append(mn).append('(').append(args(m.getParameterTypes(), "$4")).append(");").append(" return null;");
                } else { //方法有返回类型（组装方法的返回类型，如：return ($w)w.hello((org.apache.dubbo.demo.Fruit)$4[0] ）
                    c3.append(" return ($w)w.").append(mn).append('(').append(args(m.getParameterTypes(), "$4")).append(");");
                }

                c3.append(" }");
                //如：public Object invokeMethod(Object o, String n, Class[] p, Object[] v)
                // throws java.lang.reflect.InvocationTargetException{ org.apache.dubbo.demo.GreetingService w;
                // try{ w = ((org.apache.dubbo.demo.GreetingService)$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }
                // try{ if( "hello".equals( $2 )  &&  $3.length == 0 ) {  return ($w)w.hello(); }

                mns.add(mn); //加入到方法名列表
                if (m.getDeclaringClass() == c) {
                    dmns.add(mn); //被封装的类或接口中声明的方法
                }
                ms.put(ReflectUtils.getDesc(m), m); //将方法描述符与方法实例缓存起来
            }
            c3.append(" } catch(Throwable e) { ");
            c3.append("     throw new java.lang.reflect.InvocationTargetException(e); ");
            c3.append(" }");
        }

        c3.append(" throw new " + NoSuchMethodException.class.getName() + "(\"Not found method \\\"\"+$2+\"\\\" in class " + c.getName() + ".\"); }"); //若没有找到方法，则抛出“未找到方法”的异常

        // deal with get/set method.（处理set/get方法）
        Matcher matcher; //todo @pause
        for (Map.Entry<String, Method> entry : ms.entrySet()) {
            String md = entry.getKey(); //暴露接口中的方法描述信息，如hello(Lorg/apache/dubbo/demo/FruitEnum;)Ljava/lang/String;
            Method method = entry.getValue();
            if ((matcher = ReflectUtils.GETTER_METHOD_DESC_PATTERN.matcher(md)).matches()) { //判断是否匹配get方法对应的描述信息
                String pn = propertyName(matcher.group(1));
                c2.append(" if( $2.equals(\"").append(pn).append("\") ){ return ($w)w.").append(method.getName()).append("(); }");
                pts.put(pn, method.getReturnType());
            } else if ((matcher = ReflectUtils.IS_HAS_CAN_METHOD_DESC_PATTERN.matcher(md)).matches()) { //匹配is、has、can方法
                String pn = propertyName(matcher.group(1));
                c2.append(" if( $2.equals(\"").append(pn).append("\") ){ return ($w)w.").append(method.getName()).append("(); }");
                pts.put(pn, method.getReturnType());
            } else if ((matcher = ReflectUtils.SETTER_METHOD_DESC_PATTERN.matcher(md)).matches()) { //匹配set方法
                Class<?> pt = method.getParameterTypes()[0];
                String pn = propertyName(matcher.group(1));
                c1.append(" if( $2.equals(\"").append(pn).append("\") ){ w.").append(method.getName()).append("(").append(arg(pt, "$3")).append("); return; }");
                pts.put(pn, pt);
            }
        }
        c1.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" field or setter method in class " + c.getName() + ".\"); }");
        c2.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" field or setter method in class " + c.getName() + ".\"); }");

        // make class（构建Class对象）
        long id = WRAPPER_CLASS_COUNTER.getAndIncrement();
        ClassGenerator cc = ClassGenerator.newInstance(cl);
        cc.setClassName((Modifier.isPublic(c.getModifiers()) ? Wrapper.class.getName() : c.getName() + "$sw") + id); //org.apache.dubbo.common.bytecode.Wrapper0，判断类是否是public，然后进行类名拼接
        cc.setSuperClass(Wrapper.class); //将Wrapper指定为父类

        cc.addDefaultConstructor(); //添加默认构造函数
        cc.addField("public static String[] pns;"); // property name array.
        cc.addField("public static " + Map.class.getName() + " pts;"); // property type map.
        cc.addField("public static String[] mns;"); // all method name array.
        cc.addField("public static String[] dmns;"); // declared method name array.
        for (int i = 0, len = ms.size(); i < len; i++) {
            cc.addField("public static Class[] mts" + i + ";");
        }

        cc.addMethod("public String[] getPropertyNames(){ return pns; }");
        cc.addMethod("public boolean hasProperty(String n){ return pts.containsKey($1); }");
        cc.addMethod("public Class getPropertyType(String n){ return (Class)pts.get($1); }");
        cc.addMethod("public String[] getMethodNames(){ return mns; }");
        cc.addMethod("public String[] getDeclaredMethodNames(){ return dmns; }");
        cc.addMethod(c1.toString()); //处理setPropertyValue()方法
        cc.addMethod(c2.toString()); //处理getPropertyValue()方法
        cc.addMethod(c3.toString()); //处理invokeMethod()方法

        try {
            Class<?> wc = cc.toClass(); //将CtClass转换为Class
            // setup static field.
            wc.getField("pts").set(null, pts);
            wc.getField("pns").set(null, pts.keySet().toArray(new String[0]));
            wc.getField("mns").set(null, mns.toArray(new String[0]));
            wc.getField("dmns").set(null, dmns.toArray(new String[0]));
            int ix = 0;
            for (Method m : ms.values()) { //遍历方法参数列表
                wc.getField("mts" + ix++).set(null, m.getParameterTypes());
            }
            return (Wrapper) wc.newInstance(); //使用Class对象创建实例，并强转为Wrapper类型
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            cc.release();
            ms.clear();
            mns.clear();
            dmns.clear();
        }
    }

    private static String arg(Class<?> cl, String name) { //将参数按指定的类型转换
        if (cl.isPrimitive()) {
            if (cl == Boolean.TYPE) {
                return "((Boolean)" + name + ").booleanValue()"; //转换为封装类
            }
            if (cl == Byte.TYPE) {
                return "((Byte)" + name + ").byteValue()";
            }
            if (cl == Character.TYPE) {
                return "((Character)" + name + ").charValue()";
            }
            if (cl == Double.TYPE) {
                return "((Number)" + name + ").doubleValue()";
            }
            if (cl == Float.TYPE) {
                return "((Number)" + name + ").floatValue()";
            }
            if (cl == Integer.TYPE) {
                return "((Number)" + name + ").intValue()";
            }
            if (cl == Long.TYPE) {
                return "((Number)" + name + ").longValue()";
            }
            if (cl == Short.TYPE) {
                return "((Number)" + name + ").shortValue()";
            }
            throw new RuntimeException("Unknown primitive type: " + cl.getName());
        }
        return "(" + ReflectUtils.getName(cl) + ")" + name; //不是基本类型，做强制转换，如 (org.apache.dubbo.demo.FruitEnum)$4[0]
    }

    private static String args(Class<?>[] cs, String name) {
        int len = cs.length;
        if (len == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arg(cs[i], name + "[" + i + "]"));
        }
        return sb.toString();
    }

    private static String propertyName(String pn) {//获取属性名称
        return pn.length() == 1 || Character.isLowerCase(pn.charAt(1)) ? Character.toLowerCase(pn.charAt(0)) + pn.substring(1) : pn;
    }

    private static boolean hasMethods(Method[] methods) { //判断是否有非Object中的方法
        if (methods == null || methods.length == 0) {
            return false;
        }
        for (Method m : methods) {
            if (m.getDeclaringClass() != Object.class) {
                return true;
            }
        }
        return false; //若所有的方法都是Object中的，表明是Object对象，不处理
    }

    /**
     * get property name array.
     *
     * @return property name array.
     */
    abstract public String[] getPropertyNames();

    /**
     * get property type.
     *
     * @param pn property name.
     * @return Property type or nul.
     */
    abstract public Class<?> getPropertyType(String pn);

    /**
     * has property.
     *
     * @param name property name.
     * @return has or has not.
     */
    abstract public boolean hasProperty(String name);

    /**
     * get property value.
     *
     * @param instance instance.
     * @param pn       property name.
     * @return value.
     */
    abstract public Object getPropertyValue(Object instance, String pn) throws NoSuchPropertyException, IllegalArgumentException;

    /**
     * set property value.
     *
     * @param instance instance.
     * @param pn       property name.
     * @param pv       property value.
     */
    abstract public void setPropertyValue(Object instance, String pn, Object pv) throws NoSuchPropertyException, IllegalArgumentException;

    /**
     * get property value.
     *
     * @param instance instance.
     * @param pns      property name array.
     * @return value array.
     */
    public Object[] getPropertyValues(Object instance, String[] pns) throws NoSuchPropertyException, IllegalArgumentException {
        Object[] ret = new Object[pns.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = getPropertyValue(instance, pns[i]);
        }
        return ret;
    }

    /**
     * set property value.
     *
     * @param instance instance.
     * @param pns      property name array.
     * @param pvs      property value array.
     */
    public void setPropertyValues(Object instance, String[] pns, Object[] pvs) throws NoSuchPropertyException, IllegalArgumentException {
        if (pns.length != pvs.length) {
            throw new IllegalArgumentException("pns.length != pvs.length");
        }

        for (int i = 0; i < pns.length; i++) {
            setPropertyValue(instance, pns[i], pvs[i]);
        }
    }

    /**
     * get method name array.（获取被封装的类中的方法（包含继承的方法））
     *
     * @return method name array.
     */
    abstract public String[] getMethodNames();

    /**
     * get method name array.（获取被封装的类中声明的方法）
     *
     * @return method name array.
     */
    abstract public String[] getDeclaredMethodNames();

    /**
     * has method.
     *
     * @param name method name.
     * @return has or has not.
     */
    public boolean hasMethod(String name) {
        for (String mn : getMethodNames()) {
            if (mn.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * invoke method.(调用实例中的对应方法)
     *
     * @param instance instance.
     * @param mn       method name.（方法名称）
     * @param types （参数类型对应的数组）
     * @param args     argument array.（参数值对应的数组）
     * @return return value.
     */
    abstract public Object invokeMethod(Object instance, String mn, Class<?>[] types, Object[] args) throws NoSuchMethodException, InvocationTargetException;
}
