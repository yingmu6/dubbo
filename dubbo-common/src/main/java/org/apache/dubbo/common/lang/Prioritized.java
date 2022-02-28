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
package org.apache.dubbo.common.lang;

import java.util.Comparator;

import static java.lang.Integer.compare;

/**
 * {@code Prioritized（优先）} interface can be implemented by objects that
 * should be sorted, for example the tasks in executable(可执行的) queue.
 * （排序时使用，通过优先级进行排序，实现类包含：LoadingStrategy、EventListener等等）
 *
 * @since 2.7.5
 */
public interface Prioritized extends Comparable<Prioritized> { //比较逻辑是啥？Prioritized优先级是指啥？解：按对象的优先级的值进行比较

    /**
     * The {@link Comparator} of {@link Prioritized}
     */
    Comparator<Object> COMPARATOR = (one, two) -> { //对象比较器，todo @csy-02-28 该比较器的处理逻辑是怎样的？
        boolean b1 = one instanceof Prioritized;
        boolean b2 = two instanceof Prioritized;
        if (b1 && !b2) {        // one is Prioritized, two is not
            return -1;
        } else if (b2 && !b1) { // two is Prioritized, one is not
            return 1;
        } else if (b1 && b2) {  //  one and two both are Prioritized
            return ((Prioritized) one).compareTo((Prioritized) two);
        } else {                // no different
            return 0;
        }
    };

    /**
     * The maximum priority
     */
    int MAX_PRIORITY = Integer.MIN_VALUE;

    /**
     * The minimum priority
     */
    int MIN_PRIORITY = Integer.MAX_VALUE;

    /**
     * Normal Priority
     */
    int NORMAL_PRIORITY = 0;

    /**
     * Get the priority
     *
     * @return the default is {@link #MIN_PRIORITY minimum one}
     */
    default int getPriority() {
        return NORMAL_PRIORITY;
    }

    @Override
    default int compareTo(Prioritized that) { //按对象的优先级值进行比较compare
        return compare(this.getPriority(), that.getPriority());
    }
}
