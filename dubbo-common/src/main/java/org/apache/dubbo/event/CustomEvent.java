package org.apache.dubbo.event;

/**
 * @Author chenSy
 * @Date 2023/04/28 10:53
 * @Description
 */
public class CustomEvent extends Event {

    public CustomEvent(Object source) {
        super(source);
        System.out.println("进入自定义事件！");
    }

    @Override
    public Object getSource() {
        return this.source;
    }
}
