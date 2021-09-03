package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ext_self.fruit.Fruit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author chensy
 * @date 2021/9/3
 */
public class ExtensionLoader_Self_Fruit_Test {

    private static final String APPLE = "apple";
    private static final String ORANGE = "orange";
    private static final String PEER = "peer";


    @Test
    public void testBasic() { //会在SPI接口所在的模块查找配置文件
        ExtensionLoader<Fruit> extensionLoader = getExtensionLoader(Fruit.class);
        Fruit fruit = extensionLoader.getExtension(APPLE); //直接从配置文件中查找扩展类
        assertEquals("苹果", fruit.getFruitName());
    }

    @Test
    public void testAdaptive() { //测试自适应方法
        try {
            ExtensionLoader<Fruit> extensionLoader = getExtensionLoader(Fruit.class);
            extensionLoader.getAdaptiveExtension();
        } catch (IllegalStateException expected) { // 若SPI接口中没有@Adaptive标识的方法，会抛出
            assertThat(expected.getMessage(),
                    containsString("Can't create adaptive extension interface"));
        }
    }

    // 不管是根据扩展名获取扩展，还是自适应、自动激活，都是要在配置文件中配置扩展名与扩展类的映射，然后加载的缓存中，后续进行筛选
    @Test
    public void testActivate() { //从ExtensionLoader的缓存中找到@Activate实例列表，并按照传入的group、value与@Activate注解上设置的值进行比较
        ExtensionLoader<Fruit> extensionLoader = getExtensionLoader(Fruit.class);
        URL url = URL.valueOf("dubbo://192.168.1.106:20881/org.apache.dubbo.common.extension.ext_self.fruit.Fruit?anyhost=true&&dynamic=true");
        List<Fruit> fruits = extensionLoader.getActivateExtension(url, new String[] {}, "sweet");
        assertEquals(2, fruits.size());
    }


}
