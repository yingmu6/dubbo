package org.apache.dubbo.config.spring.context;

import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * @author chensy
 * @date 2021/12/9
 */
//@Component 使用该注解不生效
//@Service 使用该注解不生效
public class DubboBootstrapApplicationListenerTest extends OneTimeExecutionApplicationContextEventListener {

    public static final String BEAN_NAME = "dubboBootstrapApplicationListenerTest";

    @Override  //继承OneTimeExecutionApplicationContextEventListener方式
    protected void onApplicationContextEvent(ApplicationContextEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            System.out.println("测试spring监听器");
        }
    }

//    @Override //直接实现ApplicationListener方式
//    public void onApplicationEvent(ApplicationEvent event) {
//        if (event instanceof ContextRefreshedEvent) {
//            System.out.println("测试spring监听器，实现ApplicationListener");
//        }
//    }

    /**
     * Spring的Listener是观察模式，即一个主题对应多个观察者，所以实现ApplicationListener接口的实例都能收到Spring的事件
     * 在Dubbo中的spring bean，除了xml中定义的bean，普通的bean，要在DubboBeanUtils#registerCommonBeans，否则不生效，比如@@Component、@Service都不生效
     */
}
