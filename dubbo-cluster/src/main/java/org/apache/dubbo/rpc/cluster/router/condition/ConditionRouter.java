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
package org.apache.dubbo.rpc.cluster.router.condition;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.router.AbstractRouter;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.rpc.cluster.Constants.*;

/**
 * ConditionRouter
 */
public class ConditionRouter extends AbstractRouter {
    public static final String NAME = "condition";

    private static final Logger logger = LoggerFactory.getLogger(ConditionRouter.class);
    protected static final Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)"); //包含 &!=, 等字符串的匹配
    protected Map<String, MatchPair> whenCondition; //when的条件
    protected Map<String, MatchPair> thenCondition; //then的条件

    /**
     * => 之前的为消费者匹配条件，所有参数和消费者的 URL 进行对比，当消费者满足匹配条件时，对该消费者执行后面的过滤规则。
     * => 之后为提供者地址列表的过滤条件，所有参数和提供者的 URL 进行对比，消费者最终只拿到过滤后的地址列表。
     * 如果匹配条件为空，表示对所有消费方应用，如：=> host != 10.20.153.11
     * 如果过滤条件为空，表示禁止访问，如：host = 10.20.153.10 =>
     * <p>
     * 路由器里面存放着匹配的规则
     * <p>
     * 官网地址
     * https://dubbo.apache.org/zh/docs/v2.7/user/examples/routing-rule-deprecated/
     */

    private boolean enabled;

    public ConditionRouter(String rule, boolean force, boolean enabled) {
        this.force = force;
        this.enabled = enabled;
        this.init(rule);
    }

    public ConditionRouter(URL url) { //对象初始化时，先解析出规则，存入whenCondition、thenCondition，然后再进行比较
        this.url = url; //url的值如：condition://0.0.0.0/com.foo.BarService?rule=+%3D%3E++host+%3D+192.168.3.16，rule对应的值是经过编码的
        this.priority = url.getParameter(PRIORITY_KEY, 0);
        this.force = url.getParameter(FORCE_KEY, false);
        this.enabled = url.getParameter(ENABLED_KEY, true);
        init(url.getParameterAndDecoded(RULE_KEY)); //将url中的rule参数对应的值进行解码
    }

    public void init(String rule) { //规则字符串，如： "=>  host = 192.168.3.16"
        try {
            if (rule == null || rule.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal route rule!");
            }
            rule = rule.replace("consumer.", "").replace("provider.", ""); //去除指定参数
            int i = rule.indexOf("=>"); //找到 字符串"=>" 位置
            String whenRule = i < 0 ? null : rule.substring(0, i).trim(); //取出消费者匹配的条件
            String thenRule = i < 0 ? rule.trim() : rule.substring(i + 2).trim(); //取出提供者地址列表过滤条件
            /**
             * 解析路由表达式rule：生成消费者条件、提供者条件
             */
            Map<String, MatchPair> when = StringUtils.isBlank(whenRule) || "true".equals(whenRule) ? new HashMap<String, MatchPair>() : parseRule(whenRule); //如果匹配条件为空，表示对所有消费方应用
            Map<String, MatchPair> then = StringUtils.isBlank(thenRule) || "false".equals(thenRule) ? null : parseRule(thenRule); //如果过滤条件为空，表示禁止访问
            // NOTE: It should be determined on the business level whether the `When condition` can be empty or not.
            this.whenCondition = when;
            this.thenCondition = then;
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Map<String, MatchPair> parseRule(String rule) //解析规则字符串
            throws ParseException {
        Map<String, MatchPair> condition = new HashMap<String, MatchPair>();
        if (StringUtils.isBlank(rule)) {
            return condition;
        }
        // Key-Value pair, stores both match and mismatch conditions
        MatchPair pair = null;
        // Multiple values（多个值）
        Set<String> values = null;
        final Matcher matcher = ROUTE_PATTERN.matcher(rule);
        while (matcher.find()) { // Try to match one by one（尝试一一匹配）
            String separator = matcher.group(1); //如rule为 "host = 192.168.3.16"， 分隔符separator值依次为，""、"="
            String content = matcher.group(2); //如rule为 "host = 192.168.3.16"，内容content值依次为"host"、"192.168.3.16"
            // Start part of the condition expression.
            if (StringUtils.isEmpty(separator)) {
                pair = new MatchPair();
                condition.put(content, pair);
            }
            // The KV part of the condition expression
            else if ("&".equals(separator)) {
                if (condition.get(content) == null) {
                    pair = new MatchPair();
                    condition.put(content, pair);
                } else {
                    pair = condition.get(content);
                }
            }
            // The Value in the KV part.
            else if ("=".equals(separator)) { // "="对应MatchPair中的匹配条件matches
                if (pair == null) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }

                values = pair.matches;
                values.add(content); //设置到匹配的条件集合
            }
            // The Value in the KV part.
            else if ("!=".equals(separator)) {  // "!="对应MatchPair中的匹配条件mismatches
                if (pair == null) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }

                values = pair.mismatches;
                values.add(content);
            }
            // The Value in the KV part, if Value have more than one items.（有多个匹配的值）
            else if (",".equals(separator)) { // Should be separated by ','
                if (values == null || values.isEmpty()) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }
                values.add(content);
            } else { //出现未知的匹配字符，则抛出异常
                throw new ParseException("Illegal route rule \"" + rule
                        + "\", The error char '" + separator + "' at index "
                        + matcher.start() + " before \"" + content + "\".", matcher.start());
            }
        }
        return condition;
    }

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation)
            throws RpcException {
        if (!enabled) { //若路由器是禁用的，不对invoker列表路由，直接返回
            return invokers;
        }

        if (CollectionUtils.isEmpty(invokers)) {
            return invokers;
        }
        try {
            if (!matchWhen(url, invocation)) { //先匹配whenCondition条件（匹配消费者条件）
                return invokers; //when条件不匹配，则不进行过滤
            }
            List<Invoker<T>> result = new ArrayList<Invoker<T>>();
            if (thenCondition == null) { //若过滤条件thenCondition为空，返回空列表，表示禁止访问
                logger.warn("The current consumer in the service blacklist. consumer: " + NetUtils.getLocalHost() + ", service: " + url.getServiceKey());
                return result;
            }
            for (Invoker<T> invoker : invokers) {
                if (matchThen(invoker.getUrl(), url)) { //再匹配thenCondition条件（匹配提供者条件）
                    result.add(invoker); //若invoker中的url，满足匹配的条件，则加入到结果列表中
                }
            }
            if (!result.isEmpty()) { //若按路由条件筛选到invoker列表，则做对应返回
                return result;
            } else if (force) { //若设置force=true：未匹配上时返回空列表，否则原样返回列表
                logger.warn("The route result is empty and force execute. consumer: " + NetUtils.getLocalHost() + ", service: " + url.getServiceKey() + ", router: " + url.getParameterAndDecoded(RULE_KEY));
                return result;
            }
        } catch (Throwable t) {
            logger.error("Failed to execute condition router rule: " + getUrl() + ", invokers: " + invokers + ", cause: " + t.getMessage(), t);
        }
        return invokers;
    }

    @Override
    public boolean isRuntime() {
        // We always return true for previously（之前的） defined Router, that is, old Router doesn't support cache anymore.
//        return true;
        return this.url.getParameter(RUNTIME_KEY, false);
    }

    @Override
    public URL getUrl() {
        return url;
    }

    boolean matchWhen(URL url, Invocation invocation) { //匹配消费者的条件，在whenCondition条件为空，即 "=>" 之前的条件为空时，表明对所有消费者应用，返回true
        return CollectionUtils.isEmptyMap(whenCondition) || matchCondition(whenCondition, url, null, invocation);
    }

    private boolean matchThen(URL url, URL param) { //匹配提供者的条件，在thenCondition条件为空，即 "=>" 之前的条件为空时，表明禁止访问，返回false
        return CollectionUtils.isNotEmptyMap(thenCondition) && matchCondition(thenCondition, url, param, null);
    }

    private boolean matchCondition(Map<String, MatchPair> condition, URL url, URL param, Invocation invocation) {
        Map<String, String> sample = url.toMap();
        boolean result = false;
        for (Map.Entry<String, MatchPair> matchPair : condition.entrySet()) {
            String key = matchPair.getKey();
            String sampleValue; //样品值：提供者相关比较的值（从url或invocation中取出condition的key对应的值）
            //get real invoked method name from invocation
            if (invocation != null && (METHOD_KEY.equals(key) || METHODS_KEY.equals(key))) { //在Invocation不为空的时候，通过invocation取值
                sampleValue = invocation.getMethodName();
            } else if (ADDRESS_KEY.equals(key)) { //根据参数key名称进行比较
                sampleValue = url.getAddress();
            } else if (HOST_KEY.equals(key)) {
                sampleValue = url.getHost();
            } else {
                sampleValue = sample.get(key);
                if (sampleValue == null) {
                    sampleValue = sample.get(key);
                }
            }
            if (sampleValue != null) { //在url和invocation中存在指定key的值
                if (!matchPair.getValue().isMatch(sampleValue, param)) { //将sampleValue的值与事先归纳好的匹配对matchPair进行比较
                    return false;
                } else {
                    result = true;
                }
            } else { //在url和invocation中不存在指定key的值
                //not pass the condition
                if (!matchPair.getValue().matches.isEmpty()) {
                    return false;
                } else {
                    result = true;
                }
            }
        }
        return result;
    }

    protected static final class MatchPair { //键值对：存放匹配和不匹配的条件
        final Set<String> matches = new HashSet<String>(); //匹配的列表
        final Set<String> mismatches = new HashSet<String>(); //不匹配的列表

        private boolean isMatch(String value, URL param) {
            if (!matches.isEmpty() && mismatches.isEmpty()) { //只有matches匹配集合
                for (String match : matches) {
                    if (UrlUtils.isMatchGlobPattern(match, value, param)) { //判断输入的值是否在当前的matchers集合中
                        return true; //在匹配集合中，则返回true
                    }
                }
                return false;
            }

            if (!mismatches.isEmpty() && matches.isEmpty()) { //只有mismatches不匹配集合
                for (String mismatch : mismatches) {
                    if (UrlUtils.isMatchGlobPattern(mismatch, value, param)) {
                        return false; //在不匹配的集合中，则返回false
                    }
                }
                return true;
            }

            if (!matches.isEmpty() && !mismatches.isEmpty()) { //matches、mismatches都不为空
                //when both mismatches and matches contain the same value, then using mismatches first（当不匹配的集合和匹配的集合都包含相同的值，优先使用不匹配集合的比较结果）
                for (String mismatch : mismatches) { //优先使用不匹配集合进行比较
                    if (UrlUtils.isMatchGlobPattern(mismatch, value, param)) {
                        return false;
                    }
                }
                for (String match : matches) {
                    if (UrlUtils.isMatchGlobPattern(match, value, param)) {
                        return true;
                    }
                }
                return false;
            }
            return false;
        }
    }
}
