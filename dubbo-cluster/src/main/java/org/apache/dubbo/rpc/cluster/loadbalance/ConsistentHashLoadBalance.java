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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;

/**
 * ConsistentHashLoadBalance
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance { //一致性Hash算法

    /**
     * 背景介绍：
     * 1）Hash：是把任意长度的输入通过散列算法变换成固定长度的输出，该输出就是散列值。
     *   https://baike.baidu.com/item/Hash/390310  Hash百科
     *
     * 2）一致性Hash算法：是一种特殊的哈希算法，目的是解决分布式系统的数据分区问题，即分布式集群中存在的节点动态伸缩的问题。
     *   a）通常取模算法hash(key) % N，会根据服务器数量进行取模找到服务器节点，但N可能增加或减少，一经变动导致大量缓存同一时间失效，造成缓存雪崩。
     *   b）一致性哈希算法本质上也是一种取模算法，对固定值2^32取模，所以只要key值固定，所请求的服务器节点也是固定的。
     *   c）实现原理：
     *      c.1）哈希环：一致性哈希算法将整个哈希值空间映射成一个虚拟的圆环，取值范围在0~2^32-1。
     *      c.2）将服务器映射到哈希环：可以基于IP或其它信息，计算服务器的hash值，映射到哈希环上。
     *      c.3）请求时查找服务器节点：将请求的key计算哈希值，映射到哈希环上的具体位置，然后沿着哈希环顺时针查找，遇到的第一个节点即为要查找的节点
     *   d）服务器扩容&缩容：
     *      d.1）服务器扩容：计算新增节点的哈希值并加入到哈希环，只要将上一个节点到新节点的数据映射到新的数据节点即可，其它节点数据不受影响。
     *      d.2）服务器缩容：集群中的某个节点故障，原本映射到该节点的请求，会找到哈希环中的下一个节点，其它节点数据不受影响。
     *   e）数据倾斜和虚拟节点：
     *      e.1）由于哈希计算的随机性，大多数的访问请求会集中在少量几个节点。特别是节点太少情况下，容易因为节点分布不均匀造成数据访问的冷热不均，失去了集群和负载均衡的意义。
     *      e.2）引入虚拟节点机制，对每一个物理服务节点映射多个虚拟节点，然后将虚拟节点映射到哈希环上，当找到某个虚拟节点后，对应找到具体的物理节点。
     *   https://developer.aliyun.com/article/1082388 图解一致性哈希算法
     *
     * 3）MD5：信息摘要算法（英语：MD5 Message-Digest Algorithm），一种被广泛使用的密码散列函数，可以产生出一个128位（16字节）的散列值（hash value），用于确保信息传输完整一致。
     *    https://baike.baidu.com/item/MD5/212708 MD5百科
     *
     */

    public static final String NAME = "consistenthash";

    /**
     * Hash nodes name
     */
    public static final String HASH_NODES = "hash.nodes"; //虚拟节点数

    /**
     * Hash arguments name
     */
    public static final String HASH_ARGUMENTS = "hash.arguments"; //参数hash计算的参数下标值

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>(); //缓存调用服务key与一致性hash选择器的映射

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String methodName = RpcUtils.getMethodName(invocation);
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName; //获取调用方法对应的key
        // using the hashcode of list to compute the hash only pay attention to（专注于） the elements in the list
        int invokersHashCode = invokers.hashCode(); //获取invokers原始的hashCode（通过invoker列表的hash是否改变，来判断invoker列表是否改变）
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        if (selector == null || selector.identityHashCode != invokersHashCode) { //服务者数量发生变化，即增加或减少时，创建新的ConsistentHashSelector
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }
        return selector.select(invocation); //选择Invoker
    }

    private static final class ConsistentHashSelector<T> { //一致性Hash选择器

        private final TreeMap<Long, Invoker<T>> virtualInvokers; //存储Invoker虚拟节点（虚拟节点的hash值与invoker的映射）

        private final int replicaNumber; //每个Invoker对应的虚拟节点数

        private final int identityHashCode;

        private final int[] argumentIndex; //参与hash计算的参数下标数组

        /**
         * 初始化流程：
         * 1）获取参数hash计算的虚拟节点数、参数下标，并对成员变量的初始化。
         * 2）遍历invoker列表，计算虚拟节点hash值，并与invoker进行映射。
         *    2.1）基于address+i，计算出16字节数组digest。
         *    2.2）对digest进行4次位运算，得到long型正整数。
         *    2.3）将最终计算的虚拟节点hash值与invoker的关系存入map中。
         */
        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) { //进行初始化
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160); //获取虚拟节点数，默认为160个
            String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0")); //获取参与hash计算的参数下标值，默认对第一个参数进行hash计算
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]); //初始化参与hash计算的参数下标数组
            }
            for (Invoker<T> invoker : invokers) {
                //举例说明：此处若Invoker为3，replicaNumber为16，即会为每个Invoker产生16个虚拟节点，最终产生16*3=48个虚拟节点
                // （注明：此处设置的虚拟节点数若不能整除4，最终产生的虚拟节点数会偏少，如replicaNumber=10，则经过 (10/4) * 4 = 8 ）
                String address = invoker.getUrl().getAddress();
                for (int i = 0; i < replicaNumber / 4; i++) { //此处除以4，后面会对16字节数组进行4次运算，相当于乘以4，所以虚拟节点总数不变
                    byte[] digest = md5(address + i); //对address+i进行md5运算，得到长度为16的字节数组
                    for (int h = 0; h < 4; h++) { //对digest字节数组进行4次hash运算，得到4个不同的long型正整数
                        // h = 0 时，取 digest 中下标为 0~3 的4个字节进行位运算，得到long型正整数（其它类推）
                        long m = hash(digest, h);
                        virtualInvokers.put(m, invoker); //将hash 到 invoker的映射关系存储virtualInvokers中
                    }
                }
            }
        }

        public static void main(String[] args) {
            System.out.println(10/4);
        }

        /**
         * 选择Invoker流程：
         * 1）拼接参数下标对应的值生成参数key，对参数key进行md5以及hash运算，得到hash值。
         * 2）然后根据计算的hash值，从TreeMap中找到第一个大于或等于该hash的元素，即可找到Invoker。
         */
        public Invoker<T> select(Invocation invocation) {
            String key = toKey(invocation.getArguments()); //将进行hash计算的参数值，拼接成参数key
            byte[] digest = md5(key); //对参数key进行md5运算
            return selectForKey(hash(digest, 0)); // 对digest的前4个字节进行hash运算，然后再通过selectForKey寻找合适的invoker
        }

        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) { //遍历数组中的元素（i为数组中元素值，而不是自增变量）
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]); //拼接参数下标对应的Invocation中的参数值，生成key
                }
            }
            return buf.toString();
        }

        private Invoker<T> selectForKey(long hash) {
            // 从TreeMap中查找第一个大于或等于hash值的invoker
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash); //ceilingEntry：找到大于或等于key对应的值
            if (entry == null) { //在hash大于invoker圆环上的最大位置时，此时entry=null，需要将头结点赋值给entry
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) { //取字节数组中指定范围的字节进行位运算，得到long型正整数
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }

    }

}
