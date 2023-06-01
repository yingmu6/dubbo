package org.apache.dubbo.common.config;

import java.util.Properties;

/**
 * @author chensy
 * @date 2023/6/1
 */
public class SelfOrderedPropertiesProvider2 implements OrderedPropertiesProvider{
    @Override
    public int priority() {
        return 3;
    }

    @Override
    public Properties initProperties() {
        Properties properties = new Properties();
        properties.put("selfKey2", "555");
        return properties;
    }
}
