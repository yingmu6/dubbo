package org.apache.dubbo.demo.basic;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.apache.dubbo.common.bytecode.ClassGenerator;
import org.apache.dubbo.common.bytecode.NoSuchMethodException;
import org.apache.dubbo.common.bytecode.NoSuchPropertyException;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.demo.Fruit;
import org.apache.dubbo.demo.FruitEnum;
import org.apache.dubbo.demo.provider.GreetingServiceImpl;

/**
 * org.apache.dubbo.demo.GreetingService 暴露接口对应的Wrapper类，在运行时产生的，通过arthas的
 * 命令"jad org.apache.dubbo.common.bytecode.Wrapper1"获取到的封装类
 * （可用于阅读、调试Wrapper的Wrapper#makeWrapper(java.lang.Class)对照）
 *
 * @Author chenSy
 * @Date 2023/04/17 11:06
 * @Description
 */
public class Wrapper1_Look
        extends Wrapper
        implements ClassGenerator.DC {
    public static String[] pns;
    public static Map pts;
    public static String[] mns;
    public static String[] dmns;
    public static Class[] mts0;
    public static Class[] mts1;
    public static Class[] mts2;
    public static Class[] mts3;
    public static Class[] mts4;
    public static Class[] mts5;
    public static Class[] mts6;

    @Override
    public String[] getPropertyNames() {
        return pns;
    }

    @Override
    public boolean hasProperty(String string) {
        return pts.containsKey(string);
    }

    public Class getPropertyType(String string) {
        return (Class)pts.get(string);
    }

    @Override
    public String[] getMethodNames() {
        return mns;
    }

    @Override
    public String[] getDeclaredMethodNames() {
        return dmns;
    }

    @Override
    public void setPropertyValue(Object object, String string, Object object2) {
        GreetingServiceImpl greetingServiceImpl;
        try {
            greetingServiceImpl = (GreetingServiceImpl)object;
        }
        catch (Throwable throwable) {
            throw new IllegalArgumentException(throwable);
        }
        if (string.equals("msg")) {
            greetingServiceImpl.setMsg((String)object2);
            return;
        }
        throw new NoSuchPropertyException(new StringBuffer().append("Not found property \"").append(string).append("\" field or setter method in class org.apache.dubbo.demo.provider.GreetingServiceImpl.").toString());
    }

    @Override
    public Object getPropertyValue(Object object, String string) {
        GreetingServiceImpl greetingServiceImpl;
        try {
            greetingServiceImpl = (GreetingServiceImpl)object;
        }
        catch (Throwable throwable) {
            throw new IllegalArgumentException(throwable);
        }
        if (string.equals("msg")) {
            return greetingServiceImpl.getMsg();
        }
        throw new NoSuchPropertyException(new StringBuffer().append("Not found property \"").append(string).append("\" field or setter method in class org.apache.dubbo.demo.provider.GreetingServiceImpl.").toString());
    }

    public Object invokeMethod(Object object, String string, Class[] classArray, Object[] objectArray) throws InvocationTargetException {
        GreetingServiceImpl greetingServiceImpl;
        try {
            greetingServiceImpl = (GreetingServiceImpl)object;
        }
        catch (Throwable throwable) {
            throw new IllegalArgumentException(throwable);
        }
        try {
            if ("getMsg".equals(string) && classArray.length == 0) {
                return greetingServiceImpl.getMsg();
            }
            if ("setMsg".equals(string) && classArray.length == 1) {
                greetingServiceImpl.setMsg((String)objectArray[0]);
                return null;
            }
            if ("hello".equals(string) && classArray.length == 1 && classArray[0].getName().equals("java.lang.Integer")) {
                return greetingServiceImpl.hello((Integer)objectArray[0]);
            }
            if ("hello".equals(string) && classArray.length == 1 && classArray[0].getName().equals("org.apache.dubbo.demo.Fruit")) {
                return greetingServiceImpl.hello((Fruit)objectArray[0]);
            }
            if ("hello".equals(string) && classArray.length == 1 && classArray[0].getName().equals("org.apache.dubbo.demo.FruitEnum")) {
                return greetingServiceImpl.hello((FruitEnum)objectArray[0]);
            }
            if ("hello".equals(string) && classArray.length == 1 && classArray[0].getName().equals("java.lang.String")) {
                return greetingServiceImpl.hello((String)objectArray[0]);
            }
            if ("hello".equals(string) && classArray.length == 0) {
                return greetingServiceImpl.hello();
            }
        }
        catch (Throwable throwable) {
            throw new InvocationTargetException(throwable);
        }
        throw new NoSuchMethodException(new StringBuffer().append("Not found method \"").append(string).append("\" in class org.apache.dubbo.demo.provider.GreetingServiceImpl.").toString());
    }
}
