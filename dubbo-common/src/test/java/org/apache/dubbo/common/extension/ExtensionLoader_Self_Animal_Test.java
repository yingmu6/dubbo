package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ext_self.animal.Animal;
import org.junit.jupiter.api.Test;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;

/**
 * @author chensy
 * @date 2021/9/3
 */
public class ExtensionLoader_Self_Animal_Test {
    private static final String DOG = "dog";
    private static final String DUCK = "duck";
    private static final String PIG = "pig";

    @Test
    public void testBasic() {
        ExtensionLoader<Animal> extensionLoader = getExtensionLoader(Animal.class);
        Animal animal = extensionLoader.getExtension(DOG);
        System.out.println(animal.cry(null));
    }

    @Test
    public void testBasicWrap() {
        ExtensionLoader<Animal> extensionLoader = getExtensionLoader(Animal.class);
        Animal animal = extensionLoader.getExtension(DOG, true);
        System.out.println(animal.cry(null));
    }

    @Test
    public void testAdaptive_Default() { //自适应扩展（取默认扩展名）
        ExtensionLoader<Animal> extensionLoader = getExtensionLoader(Animal.class);
        Animal animal = extensionLoader.getAdaptiveExtension();
        URL url = URL.valueOf("dubbo://192.168.1.106:20881/org.apache.dubbo.common.extension.ext_self.animal.Animal?anyhost=true&&dynamic=true");

        System.out.println(animal.cry(url));
    }

    @Test
    public void testAdaptive_Specific() throws Exception { //自适应扩展（没有默认扩展名，从url中取参数处理）
        ExtensionLoader<Animal> extensionLoader = getExtensionLoader(Animal.class);
        Animal animal = extensionLoader.getAdaptiveExtension();
        URL url = URL.valueOf("dubbo://192.168.1.106:20881/org.apache.dubbo.common.extension.ext_self.animal.Animal?anyhost=true&&dynamic=true&animal=pig");

        System.out.println(animal.cry(url));
        System.in.read();
    }
}
