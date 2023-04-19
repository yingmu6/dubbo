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

import javassist.*;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClassGenerator（Class生成器，内部建立了与Javassist相对应的数据模型，并使用javassist动态生成Class）
 */
public final class ClassGenerator { //@csy-001 该类的用途是什么？解：Class生成工具类，先将数据转换为Dubbo内部数据形式，再在底层进行转换，转换为javassist所需的数据形式

    private static final AtomicLong CLASS_NAME_COUNTER = new AtomicLong(0); //未指定类名时，默认产生类名，用到的下标
    private static final String SIMPLE_NAME_TAG = "<init>";
    private static final Map<ClassLoader, ClassPool> POOL_MAP = new ConcurrentHashMap<ClassLoader, ClassPool>(); //ClassLoader - ClassPool（类加载器与javassist中的类池对应缓存）
    private ClassPool mPool; //javassist中的类池
    private CtClass mCtc;   //javassist中的编译时类
    private String mClassName;  //动态生成的类名
    private String mSuperClass; //父类对应的名称
    private Set<String> mInterfaces; //存放类实现的接口列表
    private List<String> mFields; //存放字段对应的代码片段，如ccp.addField("public static java.lang.reflect.Method[] methods;");
    private List<String> mConstructors; //存放构造函数对应的代码片段
    private List<String> mMethods; //存放方法对应的代码片段
    private Map<String, Method> mCopyMethods; // <method desc,method instance>  方法描述符与方法实例的映射
    private Map<String, Constructor<?>> mCopyConstructors; // <constructor desc,constructor instance> 方法描述符与构造实例的映射
    private boolean mDefaultConstructor = false; //是否使用默认构造函数

    private ClassGenerator() { //私有的构造函数，不直接对外暴露
    }

    private ClassGenerator(ClassPool pool) { //私有的构造函数
        mPool = pool; //设置类池
    }

    public static ClassGenerator newInstance() { //静态方法，创建类生成器的实例（指定ClassPool）
        return new ClassGenerator(getClassPool(Thread.currentThread().getContextClassLoader()));
    }

    public static ClassGenerator newInstance(ClassLoader loader) {
        return new ClassGenerator(getClassPool(loader));
    }

    public static boolean isDynamicClass(Class<?> cl) {
        return ClassGenerator.DC.class.isAssignableFrom(cl);
    }

    public static ClassPool getClassPool(ClassLoader loader) { //获取javassist的ClassPool（先从缓存中获取，若没有则创建类池）
        if (loader == null) { //未指定类加载器时，返回默认类池
            return ClassPool.getDefault();
        }

        ClassPool pool = POOL_MAP.get(loader);
        if (pool == null) { //若缓存中没有类池，则创建类型，并与ClassLoader映射设置到缓存中
            pool = new ClassPool(true);
            pool.appendClassPath(new LoaderClassPath(loader));
            POOL_MAP.put(loader, pool);
        }
        return pool;
    }

    private static String modifier(int mod) { //根据修饰符值获取到对应的描述字符串
        StringBuilder modifier = new StringBuilder();
        if (Modifier.isPublic(mod)) {
            modifier.append("public");
        } else if (Modifier.isProtected(mod)) {
            modifier.append("protected");
        } else if (Modifier.isPrivate(mod)) {
            modifier.append("private");
        }

        if (Modifier.isStatic(mod)) {
            modifier.append(" static");
        }
        if (Modifier.isVolatile(mod)) {
            modifier.append(" volatile");
        }

        return modifier.toString();
    }

    public String getClassName() {
        return mClassName;
    }

    public ClassGenerator setClassName(String name) {
        mClassName = name;
        return this;
    }

    public ClassGenerator addInterface(String cn) {
        if (mInterfaces == null) {
            mInterfaces = new HashSet<String>();
        }
        mInterfaces.add(cn); //将需要处理的接口名添加到待处理的集合中
        return this;
    }

    public ClassGenerator addInterface(Class<?> cl) {
        return addInterface(cl.getName());
    }

    public ClassGenerator setSuperClass(String cn) {
        mSuperClass = cn;
        return this;
    }

    public ClassGenerator setSuperClass(Class<?> cl) {
        mSuperClass = cl.getName();
        return this;
    }

    public ClassGenerator addField(String code) {
        if (mFields == null) {
            mFields = new ArrayList<String>();
        }
        mFields.add(code); //将属性对应的字符串加到列表中
        return this;
    }

    public ClassGenerator addField(String name, int mod, Class<?> type) {
        return addField(name, mod, type, null);
    }

    public ClassGenerator addField(String name, int mod, Class<?> type, String def) {
        StringBuilder sb = new StringBuilder();
        sb.append(modifier(mod)).append(' ').append(ReflectUtils.getName(type)).append(' ');
        sb.append(name);
        if (StringUtils.isNotEmpty(def)) {
            sb.append('=');
            sb.append(def);
        }
        sb.append(';');
        return addField(sb.toString());
    }

    public ClassGenerator addMethod(String code) {
        if (mMethods == null) {
            mMethods = new ArrayList<String>();
        }
        mMethods.add(code);
        return this;
    }

    public ClassGenerator addMethod(String name, int mod, Class<?> rt, Class<?>[] pts, String body) {
        return addMethod(name, mod, rt, pts, null, body);
    }

    /**
     * 构建方法描述信息：方法声明 + 方法体
     */
    public ClassGenerator addMethod(String name, int mod, Class<?> rt, Class<?>[] pts, Class<?>[] ets,
                                    String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(modifier(mod)).append(' ').append(ReflectUtils.getName(rt)).append(' ').append(name); //如：ProxyTest.ITest中方法public java.lang.String getName
        sb.append('(');
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ReflectUtils.getName(pts[i]));
            sb.append(" arg").append(i);
        }
        sb.append(')');
        if (ArrayUtils.isNotEmpty(ets)) { //处理方法异常声明
            sb.append(" throws ");
            for (int i = 0; i < ets.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(ReflectUtils.getName(ets[i]));
            }
        }
        sb.append('{').append(body).append('}'); //方法拼接：方法声明 + {方法体}
        /**
         * //如ProxyTest.ITest中方法
         * public java.lang.String getName(){Object[] args = new Object[0];
         * Object ret = handler.invoke(this, methods[0], args); return (java.lang.String)ret;}
         *
         * public void setName(java.lang.String arg0,java.lang.String arg1){Object[] args = new Object[2];
         * args[0] = ($w)$1; args[1] = ($w)$2; Object ret = handler.invoke(this, methods[1], args);}
         */
        return addMethod(sb.toString());
    }

    public ClassGenerator addMethod(Method m) {
        addMethod(m.getName(), m);
        return this;
    }

    public ClassGenerator addMethod(String name, Method m) {
        String desc = name + ReflectUtils.getDescWithoutMethodName(m);
        addMethod(':' + desc);
        if (mCopyMethods == null) {
            mCopyMethods = new ConcurrentHashMap<String, Method>(8);
        }
        mCopyMethods.put(desc, m);
        return this;
    }

    public ClassGenerator addConstructor(String code) {
        if (mConstructors == null) {
            mConstructors = new LinkedList<String>();
        }
        mConstructors.add(code);
        return this;
    }

    public ClassGenerator addConstructor(int mod, Class<?>[] pts, String body) {
        return addConstructor(mod, pts, null, body);
    }

    public ClassGenerator addConstructor(int mod, Class<?>[] pts, Class<?>[] ets, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(modifier(mod)).append(' ').append(SIMPLE_NAME_TAG);
        sb.append('(');
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ReflectUtils.getName(pts[i]));
            sb.append(" arg").append(i);
        }
        sb.append(')');
        if (ArrayUtils.isNotEmpty(ets)) {
            sb.append(" throws ");
            for (int i = 0; i < ets.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(ReflectUtils.getName(ets[i]));
            }
        }
        sb.append('{').append(body).append('}');
        return addConstructor(sb.toString());
    }

    public ClassGenerator addConstructor(Constructor<?> c) {
        String desc = ReflectUtils.getDesc(c);
        addConstructor(":" + desc);
        if (mCopyConstructors == null) {
            mCopyConstructors = new ConcurrentHashMap<String, Constructor<?>>(4);
        }
        mCopyConstructors.put(desc, c);
        return this;
    }

    public ClassGenerator addDefaultConstructor() {
        mDefaultConstructor = true;
        return this;
    }

    public ClassPool getClassPool() {
        return mPool;
    }

    public Class<?> toClass() {
        return toClass(ClassUtils.getClassLoader(ClassGenerator.class),
                getClass().getProtectionDomain());
    }

    /**
     * 动态创建Class的流程：
     * 1）将设置的代码字符串，如继承的类、实现的接口、设置的方法、字段等转换javassist对应的数据模型，如CtMethod、CtFiled等
     * 2）然后按类或接口的组成进行组装，如设置继承的类、设置实现的接口、设置类中的方法和字段等
     * 3）使用javassist的CtClass.toClass()获取到动态生成的Class
     * （类似Mybatis的动态SQL，按字符串动态组装，最终形成SQL）
     */
    public Class<?> toClass(ClassLoader loader, ProtectionDomain pd) { //将当前维护的成员方法、成员变量对应字符串转换为Class对象
        if (mCtc != null) {
            mCtc.detach(); //detach:分离， 从ClassPool中移除CtClass
        }
        // 基于当前类维护的数据，进行逻辑处理
        long id = CLASS_NAME_COUNTER.getAndIncrement();
        try {
            CtClass ctcs = mSuperClass == null ? null : mPool.get(mSuperClass); // 从类池ClassPool中获取类名mSuperClass对应的CtClass
            if (mClassName == null) { //若没显示设置类名时，自动生成对应的类名，如 org.apache.dubbo.common.bytecode.ClassGenerator0
                mClassName = (mSuperClass == null || javassist.Modifier.isPublic(ctcs.getModifiers()) // ||都优先级大于?: 且结合性是从左到右的
                        ? ClassGenerator.class.getName() : mSuperClass + "$sc") + id; //构建类名：取ClassGenerator名称或mSuperClass名称
            }
            mCtc = mPool.makeClass(mClassName); //根据类名className创建对应的CtClass对象
            if (mSuperClass != null) { // 设置继承的类（java是单继承，所以只会设置一个父类）
                mCtc.setSuperclass(ctcs);
            }
            mCtc.addInterface(mPool.get(DC.class.getName())); // add dynamic class tag. (每一个动态类都实现了DC接口)
            if (mInterfaces != null) { // 设置实现的接口
                for (String cl : mInterfaces) {
                    mCtc.addInterface(mPool.get(cl));
                }
            }
            if (mFields != null) { // 设置字段
                for (String code : mFields) {
                    mCtc.addField(CtField.make(code, mCtc)); // 将字段对应的字符串，转换为CtField
                }
            }
            if (mMethods != null) { // 设置方法
                for (String code : mMethods) {
                    if (code.charAt(0) == ':') {
                        mCtc.addMethod(CtNewMethod.copy(getCtMethod(mCopyMethods.get(code.substring(1))),
                                code.substring(1, code.indexOf('(')), mCtc, null));
                    } else {
                        mCtc.addMethod(CtNewMethod.make(code, mCtc)); // 将方法对应的字符串，转换为CtMethod
                    }
                }
            }
            if (mDefaultConstructor) { // 设置默认的构造函数（无参的构造函数）
                mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
            }
            if (mConstructors != null) { // 处理构造函数
                for (String code : mConstructors) {
                    if (code.charAt(0) == ':') {
                        mCtc.addConstructor(CtNewConstructor
                                .copy(getCtConstructor(mCopyConstructors.get(code.substring(1))), mCtc, null));
                    } else {
                        String[] sn = mCtc.getSimpleName().split("\\$+"); // inner class name include $.
                        mCtc.addConstructor(
                                CtNewConstructor.make(code.replaceFirst(SIMPLE_NAME_TAG, sn[sn.length - 1]), mCtc));
                    }
                }
            }
            CtClass.debugDump = "./javassist-debug"; //设置javassist产生的字节码目录，方便查看动态生成的字节码文件
            return mCtc.toClass(loader, pd); //使用CtClass转换到Class
        } catch (RuntimeException e) {
            throw e;
        } catch (NotFoundException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (CannotCompileException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void release() {//清除当前对象维护的数据
        if (mCtc != null) {
            mCtc.detach();
        }
        if (mInterfaces != null) {
            mInterfaces.clear();
        }
        if (mFields != null) {
            mFields.clear();
        }
        if (mMethods != null) {
            mMethods.clear();
        }
        if (mConstructors != null) {
            mConstructors.clear();
        }
        if (mCopyMethods != null) {
            mCopyMethods.clear();
        }
        if (mCopyConstructors != null) {
            mCopyConstructors.clear();
        }
    }

    private CtClass getCtClass(Class<?> c) throws NotFoundException {
        return mPool.get(c.getName());
    }

    private CtMethod getCtMethod(Method m) throws NotFoundException {
        return getCtClass(m.getDeclaringClass())
                .getMethod(m.getName(), ReflectUtils.getDescWithoutMethodName(m));
    }

    private CtConstructor getCtConstructor(Constructor<?> c) throws NotFoundException {
        return getCtClass(c.getDeclaringClass()).getConstructor(ReflectUtils.getDesc(c));
    }

    public static interface DC { //空接口，动态类标识接口（Wrapper或ClassGenerator封装的类或接口，都会实现该接口）

    } // dynamic class tag interface
}