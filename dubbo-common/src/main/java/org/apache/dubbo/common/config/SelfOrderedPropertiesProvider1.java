package org.apache.dubbo.common.config;

import java.util.Properties;

/**
 * @author chensy
 * @date 2023/6/1
 */
public class SelfOrderedPropertiesProvider1 implements OrderedPropertiesProvider{
    @Override
    public int priority() {
        return 1;
    }

    @Override
    public Properties initProperties() {
        Properties properties = new Properties();
        properties.put("selfKey", "333");
        return properties;
    }
}
