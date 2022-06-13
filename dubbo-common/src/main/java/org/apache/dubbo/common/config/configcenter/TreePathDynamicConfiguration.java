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
package org.apache.dubbo.common.config.configcenter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.file.FileSystemDynamicConfiguration;
import org.apache.dubbo.common.utils.StringUtils;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Collections.emptySortedSet;
import static java.util.Collections.unmodifiableSortedSet;
import static org.apache.dubbo.common.config.configcenter.Constants.CONFIG_NAMESPACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;
import static org.apache.dubbo.common.utils.CollectionUtils.isEmpty;
import static org.apache.dubbo.common.utils.PathUtils.buildPath;
import static org.apache.dubbo.common.utils.PathUtils.normalize;

/**
 * An abstract implementation of {@link DynamicConfiguration} is like "TREE-STRUCTURE" path : //像树形结构的路径
 * <ul>
 *     <li>{@link FileSystemDynamicConfiguration "file"}</li>
 *     <li>{@link org.apache.dubbo.configcenter.support.zookeeper.ZookeeperDynamicConfiguration "zookeeper"}</li>
 *     <li>{@link org.apache.dubbo.configcenter.consul.ConsulDynamicConfiguration "consul"}</li>
 * </ul>
 *
 * @see DynamicConfiguration
 * @see AbstractDynamicConfiguration
 * @since 2.7.8
 */
public abstract class TreePathDynamicConfiguration extends AbstractDynamicConfiguration {

    /**
     * The parameter name of URL for the config root path （这个url的参数名用于配置root路径）
     */
    public static final String CONFIG_ROOT_PATH_PARAM_NAME = PARAM_NAME_PREFIX + "root-path";

    /**
     * The parameter name of URL for the config base path
     */
    public static final String CONFIG_BASE_PATH_PARAM_NAME = PARAM_NAME_PREFIX + "base-path";

    /**
     * The default value of parameter of URL for the config base path
     */
    public static final String DEFAULT_CONFIG_BASE_PATH = "/config";

    private final String rootPath;

    public TreePathDynamicConfiguration(URL url) {
        super(url);
        this.rootPath = getRootPath(url);
    }

    public TreePathDynamicConfiguration(String rootPath,
                                        String threadPoolPrefixName,
                                        int threadPoolSize,
                                        long keepAliveTime,
                                        String group,
                                        long timeout) {
        super(threadPoolPrefixName, threadPoolSize, keepAliveTime, group, timeout);
        this.rootPath = rootPath;
    }

    @Override
    protected final String doGetConfig(String key, String group) throws Exception {
        String pathKey = buildPathKey(group, key); //如存储的配置文件路径：/Users/chenshengyong/.dubbo/config-center/dubbo/metadata/org.apache.dubbo.rpc.service.EchoService/provider/test-service
        return doGetConfig(pathKey);
    }

    @Override
    public final boolean publishConfig(String key, String group, String content) { //子类FileSystemDynamicConfiguration中没有重写该方法，所以就直接用父类，即当前publishConfig()方法
        String pathKey = buildPathKey(group, key); //路径key如：/Users/chenshengyong/.dubbo/config-center/dubbo/metadata/org.apache.dubbo.rpc.service.EchoService/provider/test-service

        // 异步发布配置（将元数据信息写到远端或本地）
        return execute(() -> doPublishConfig(pathKey, content), getDefaultTimeout()); //execute(Callable<V> task, long timeout) 参数Callable是函数式接口，可以用lambda表示
    }

    @Override
    protected final boolean doRemoveConfig(String key, String group) throws Exception {
        String pathKey = buildPathKey(group, key);
        return doRemoveConfig(pathKey);
    }

    @Override
    public final void addListener(String key, String group, ConfigurationListener listener) {
        String pathKey = buildPathKey(group, key);
        doAddListener(pathKey, listener);
    }

    @Override
    public final void removeListener(String key, String group, ConfigurationListener listener) {
        String pathKey = buildPathKey(group, key);
        doRemoveListener(pathKey, listener);
    }

    @Override
    public final SortedSet<String> getConfigKeys(String group) throws UnsupportedOperationException {
        String groupPath = buildGroupPath(group);
        Collection<String> configKeys = doGetConfigKeys(groupPath);
        return isEmpty(configKeys) ? emptySortedSet() : unmodifiableSortedSet(new TreeSet<>(configKeys));
    }

    /**
     * 发布配置（在远端或本地把配置文件内容保存下来）
     * 有不同的实现：
     * 比如若是Zookeeper实现的ZookeeperDynamicConfiguration，就把配置在Zookeeper目录下创建一个节点
     * 如本地文件实现的FileSystemDynamicConfiguration，就把内容写到本地文件中
     * 或者consul实现的配置中心ConsulDynamicConfiguration，就内容以key-value形式写到远程
     */
    protected abstract boolean doPublishConfig(String pathKey, String content) throws Exception;

    protected abstract String doGetConfig(String pathKey) throws Exception;

    /**
     * 移除配置：
     * 有不同的实现：
     * 比如：本地文件实现的FileSystemDynamicConfiguration，就把指定路径的文件删除
     * 比如：Zookeeper实现的ZookeeperDynamicConfiguration，就会删除指定路径的节点
     */
    protected abstract boolean doRemoveConfig(String pathKey) throws Exception;

    protected abstract Collection<String> doGetConfigKeys(String groupPath);

    protected abstract void doAddListener(String pathKey, ConfigurationListener listener);

    protected abstract void doRemoveListener(String pathKey, ConfigurationListener listener);

    protected String buildGroupPath(String group) { //构建组的路径：根路径+组
        return buildPath(rootPath, group);
    }

    protected String buildPathKey(String group, String key) {
        return buildPath(buildGroupPath(group), key);
    }

    /**
     * Get the root path from the specified {@link URL connection URl}
     *
     * @param url the specified {@link URL connection URl}
     * @return non-null
     */
    protected String getRootPath(URL url) { //获取根目录的路径

        String rootPath = url.getParameter(CONFIG_ROOT_PATH_PARAM_NAME, buildRootPath(url));

        rootPath = normalize(rootPath);

        int rootPathLength = rootPath.length();

        if (rootPathLength > 1 && rootPath.endsWith(PATH_SEPARATOR)) {
            rootPath = rootPath.substring(0, rootPathLength - 1);
        }

        return rootPath;
    }

    private String buildRootPath(URL url) {
        return PATH_SEPARATOR + getConfigNamespace(url) + getConfigBasePath(url);
    }

    /**
     * Get the namespace from the specified {@link URL connection URl}
     * （从指定url中获取命名空间）
     *
     * @param url the specified {@link URL connection URl}
     * @return non-null
     */
    protected String getConfigNamespace(URL url) {
        return url.getParameter(CONFIG_NAMESPACE_KEY, DEFAULT_GROUP);
    }

    /**
     * Get the config base path from the specified {@link URL connection URl}
     *
     * @param url the specified {@link URL connection URl}
     * @return non-null
     */
    protected String getConfigBasePath(URL url) {
        String configBasePath = url.getParameter(CONFIG_BASE_PATH_PARAM_NAME, DEFAULT_CONFIG_BASE_PATH);
        if (StringUtils.isNotEmpty(configBasePath) && !configBasePath.startsWith(PATH_SEPARATOR)) {
            configBasePath = PATH_SEPARATOR + configBasePath;
        }
        return configBasePath;
    }
}
