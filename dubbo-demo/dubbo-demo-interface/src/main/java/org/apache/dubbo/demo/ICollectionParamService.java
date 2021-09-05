package org.apache.dubbo.demo;

import java.util.List;
import java.util.Map;

/**
 * @author chensy
 * @date 2021/9/4
 */
public interface ICollectionParamService {
    String getValWithMapParam(Map<Integer, String> map); //参数中传递Map

    String getValWitchListParam(List<String> params);
}