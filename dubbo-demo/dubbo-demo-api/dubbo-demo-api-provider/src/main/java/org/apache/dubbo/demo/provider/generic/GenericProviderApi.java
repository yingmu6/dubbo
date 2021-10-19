package org.apache.dubbo.demo.provider.generic;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.demo.IAnimalService;

/**
 * @author chensy
 * @date 2021/10/19
 */
public class GenericProviderApi {
    public static void main(String[] args) throws Exception {
        ServiceConfig<MyGenericService> serviceConfig = new ServiceConfig();
        serviceConfig.setInterface(IAnimalService.class);

        MyGenericService myGenericService = new MyGenericService();
        serviceConfig.setRef(myGenericService);

        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setProtocol("zookeeper");
        registryConfig.setAddress("127.0.0.1");
        registryConfig.setPort(2181);
        serviceConfig.setRegistry(registryConfig);

        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.setName("generic-application");
        serviceConfig.setApplication(applicationConfig);

        serviceConfig.export();

        System.in.read();
    }
}
