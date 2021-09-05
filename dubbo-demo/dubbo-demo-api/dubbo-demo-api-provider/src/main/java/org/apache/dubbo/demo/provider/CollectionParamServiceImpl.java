package org.apache.dubbo.demo.provider;

import org.apache.dubbo.demo.ICollectionParamService;

import java.util.List;
import java.util.Map;

/**
 * @author chensy
 * @date 2021/9/4
 */
public class CollectionParamServiceImpl implements ICollectionParamService {

    @Override
    public String getValWithMapParam(Map<Integer, String> map) {
        return "测试Map:" + map.keySet() + ":" + map.values();
    }

    @Override
    public String getValWitchListParam(List<String> params) {
        return "测试List：" + params;
    }
}
