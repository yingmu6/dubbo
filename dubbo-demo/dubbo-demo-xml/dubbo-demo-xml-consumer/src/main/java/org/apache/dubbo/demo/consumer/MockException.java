package org.apache.dubbo.demo.consumer;

/**
 * @author chensy
 * @date 2021/10/12
 */
public class MockException extends Throwable {
    public MockException(String message) {
        System.out.println("MockException异常信息," + message);
    }

    /**
     * Mock异常的执行步骤
     * 1）ReferenceConfig#init()，引用服务初始化时会检查Mock信息
     * 2）若Mock配置的信息，若包含"throw 自定义异常类"，则会检查自定义异常类是否有
     *    包含一个字符串的构造方法且继承Throwable，因为Dubbo会把异常信息回传，且会强转为Throwable
     * 3）执行逻辑进入自定义异常类，做对应操作，由调用方决定是否需要停止操作
     */
}
