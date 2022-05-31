package org.apache.dubbo.metadata.definition.protobuf;

import org.apache.dubbo.metadata.definition.builder.TypeBuilder;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author chensy
 * @date 2022/5/31
 */
public class ProtobufTypeBuilderTest implements TypeBuilder {
    @Override
    public boolean accept(Type type, Class<?> clazz) {
        return false;
    }

    @Override
    public TypeDefinition build(Type type, Class<?> clazz, Map<Class<?>, TypeDefinition> typeCache) {
        return null;
    }
}
