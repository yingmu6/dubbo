/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.metadata.rest.jaxrs;

import org.apache.dubbo.metadata.rest.AbstractServiceRestMetadataResolver;
import org.apache.dubbo.metadata.rest.ServiceRestMetadataResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.dubbo.common.utils.AnnotationUtils.*;
import static org.apache.dubbo.common.utils.PathUtils.buildPath;
import static org.apache.dubbo.metadata.rest.RestMetadataConstants.JAX_RS.*;

/**
 * JAX-RS {@link ServiceRestMetadataResolver} implementation
 * <p>
 * JAX-RS（Java API for RESTful Web Services） Java API提供的注解可在java中开发RESTful应用程序
 *
 * @since 2.7.6
 */
public class JAXRSServiceRestMetadataResolver extends AbstractServiceRestMetadataResolver { //Rest元数据解析器
    /**
     * JAX-RS是标准的Java REST API，得到了业界的广泛支持和应用，其著名的开源实现就有很多，
     * 包括Oracle的Jersey，RedHat的RestEasy，Apache的CXF和Wink，以及restlet等等。另外，所有支持JavaEE 6.0以上规范的商用JavaEE应用服务器都对JAX-RS提供了支持。
     * 因此，JAX-RS是一种已经非常成熟的解决方案，并且采用它没有任何所谓vendor lock-in的问题。
     * <p>
     * https://dubbo.apache.org/zh/docs/references/protocols/rest/
     */

    @Override
    protected boolean supports0(Class<?> serviceType) {
        return isAnnotationPresent(serviceType, PATH_ANNOTATION_CLASS_NAME);
    }

    @Override
    protected boolean isRestCapableMethod(Method serviceMethod, Class<?> serviceType, Class<?> serviceInterfaceClass) {
        return isAnnotationPresent(serviceMethod, HTTP_METHOD_ANNOTATION_CLASS_NAME);
    }

    @Override
    protected String resolveRequestMethod(Method serviceMethod, Class<?> serviceType, Class<?> serviceInterfaceClass) {
        Annotation httpMethod = findMetaAnnotation(serviceMethod, HTTP_METHOD_ANNOTATION_CLASS_NAME);
        return getValue(httpMethod);
    }

    @Override
    protected String resolveRequestPath(Method serviceMethod, Class<?> serviceType, Class<?> serviceInterfaceClass) {
        String requestBasePath = resolveRequestPathFromType(serviceType, serviceInterfaceClass);
        String requestRelativePath = resolveRequestPathFromMethod(serviceMethod);
        return buildPath(requestBasePath, requestRelativePath);
    }

    private String resolveRequestPathFromType(Class<?> serviceType, Class<?> serviceInterfaceClass) {
        Annotation path = findAnnotation(serviceType, PATH_ANNOTATION_CLASS_NAME);
        if (path == null) {
            path = findAnnotation(serviceInterfaceClass, PATH_ANNOTATION_CLASS_NAME);
        }
        return getValue(path);
    }

    private String resolveRequestPathFromMethod(Method serviceMethod) {
        Annotation path = findAnnotation(serviceMethod, PATH_ANNOTATION_CLASS_NAME);
        return getValue(path);
    }

    @Override
    protected void processProduces(Method serviceMethod, Class<?> serviceType, Class<?> serviceInterfaceClass,
                                   Set<String> produces) {
        addAnnotationValues(serviceMethod, PRODUCES_ANNOTATION_CLASS_NAME, produces);
    }

    @Override
    protected void processConsumes(Method serviceMethod, Class<?> serviceType, Class<?> serviceInterfaceClass,
                                   Set<String> consumes) {
        addAnnotationValues(serviceMethod, CONSUMES_ANNOTATION_CLASS_NAME, consumes);
    }

    private void addAnnotationValues(Method serviceMethod, String annotationAttributeName, Set<String> result) {
        Annotation annotation = findAnnotation(serviceMethod, annotationAttributeName);
        String[] value = getValue(annotation);
        if (value != null) {
            Stream.of(value).forEach(result::add);
        }
    }
}
