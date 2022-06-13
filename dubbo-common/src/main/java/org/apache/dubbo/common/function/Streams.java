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
package org.apache.dubbo.common.function;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.function.Predicates.and;
import static org.apache.dubbo.common.function.Predicates.or;

/**
 * The utilities class for {@link Stream}
 *
 * @since 2.7.5
 */
public interface Streams { //Stream工具

    static <T, S extends Iterable<T>> Stream<T> filterStream(S values, Predicate<T> predicate) { //带着谓词对stream流进行过滤
        return stream(values.spliterator(), false).filter(predicate);
    }

    static <T, S extends Iterable<T>> List<T> filterList(S values, Predicate<T> predicate) { //Predicate是函数式接口，本质上也是一个接口，只是可以用lambda表达式传。此处是方法声明，只要在接口调用时，传入接口对应的具体实现即可
        return filterStream(values, predicate).collect(toList());
    }

    static <T, S extends Iterable<T>> Set<T> filterSet(S values, Predicate<T> predicate) { //Predicate：先声明，后使用，面向接口编程
        // new Set with insertion order（新建Set集合，带着插入）
        return filterStream(values, predicate).collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    static <T, S extends Iterable<T>> S filter(S values, Predicate<T> predicate) {
        final boolean isSet = Set.class.isAssignableFrom(values.getClass()); //this.class.isAssignableFrom(cls) 判断当前类或接口是否与指定的类或接口相同，或者是指定类的超类或超接口，比如此处values的类型为LinkedHashSet，Set即为LinkedHashSet的超类，所以返回true
        return (S) (isSet ? filterSet(values, predicate) : filterList(values, predicate)); //若是Set类型，则按Set类型解析，否则按List解析
    }

    static <T, S extends Iterable<T>> S filterAll(S values, Predicate<T>... predicates) {
        return filter(values, and(predicates));
    }

    static <T, S extends Iterable<T>> S filterAny(S values, Predicate<T>... predicates) {
        return filter(values, or(predicates));
    }

    static <T> T filterFirst(Iterable<T> values, Predicate<T>... predicates) { //查找符合条件的第一个元素
        return stream(values.spliterator(), false)
                .filter(and(predicates))
                .findFirst()
                .orElse(null);
    }
}


