package org.apache.dubbo.rpc.cluster.loadbalance;

/**
 * @author orange
 * @date 2024/6/12
 */
class A {
    private int age = 1;
    public void setAge(int age) {
        this.age = age;
    }
    public int getAge(){
        return age;
    }
}

public class ATest {

    public static void main(String[] args) {
        A a = new A();
        a.setAge(111);
        System.out.println(a.getAge());
    }
}
