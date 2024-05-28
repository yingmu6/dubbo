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
package org.apache.dubbo.remoting.zookeeper.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryNTimes;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.zookeeper.ChildListener;
import org.apache.dubbo.remoting.zookeeper.DataListener;
import org.apache.dubbo.remoting.zookeeper.EventType;
import org.apache.dubbo.remoting.zookeeper.StateListener;
import org.apache.dubbo.remoting.zookeeper.support.AbstractZookeeperClient;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

public class CuratorZookeeperClient extends AbstractZookeeperClient<CuratorZookeeperClient.CuratorWatcherImpl, CuratorZookeeperClient.CuratorWatcherImpl> { //Curator实现的Zookeeper客户端

    protected static final Logger logger = LoggerFactory.getLogger(CuratorZookeeperClient.class);
    private static final String ZK_SESSION_EXPIRE_KEY = "zk.session.expire";

    static final Charset CHARSET = Charset.forName("UTF-8");
    private final CuratorFramework client;
    private Map<String, TreeCache> treeCacheMap = new ConcurrentHashMap<>();

    /**
     * 流程分析：创建Zookeeper客户端的流程
     * 1）读取url中注册中心的地址、连接超时等信息
     * 2）添加连接状态变更的监听器CuratorConnectionStateListener
     * 3）通过zk客户端CuratorFramework进行阻塞连接，若连接超时，则抛出异常
     */
    public CuratorZookeeperClient(URL url) { //创建Zookeeper客户端（不管消费者还是提供者启动时都会去连接Zookeeper，如果连接不上会抛出异常）
        super(url);
        try {
            int timeout = url.getParameter(TIMEOUT_KEY, DEFAULT_CONNECTION_TIMEOUT_MS);
            int sessionExpireMs = url.getParameter(ZK_SESSION_EXPIRE_KEY, DEFAULT_SESSION_TIMEOUT_MS);
            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(url.getBackupAddress())
                    .retryPolicy(new RetryNTimes(1, 1000)) //若连接不上，则重试一次
                    .connectionTimeoutMs(timeout)
                    .sessionTimeoutMs(sessionExpireMs);
            String authority = url.getAuthority();
            if (authority != null && authority.length() > 0) {
                builder = builder.authorization("digest", authority.getBytes());
            }
            client = builder.build(); //通过CuratorFrameworkFactory中的构建器创建Zookeeper客户端
            client.getConnectionStateListenable().addListener(new CuratorConnectionStateListener(url));
            client.start();
            boolean connected = client.blockUntilConnected(timeout, TimeUnit.MILLISECONDS); //阻塞连接上zk服务端，若超时未连接上（默认5秒），则抛出连接异常
            if (!connected) {
                throw new IllegalStateException("zookeeper not connected");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void createPersistent(String path) {
        try {
            client.create().forPath(path);
        } catch (NodeExistsException e) {
            logger.warn("ZNode " + path + " already exists.", e);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void createEphemeral(String path) {
        try {
            client.create().withMode(CreateMode.EPHEMERAL).forPath(path); //使用Zookeeper客户端创建节点
        } catch (NodeExistsException e) { //异常处理
            logger.warn("ZNode " + path + " already exists, since we will only try to recreate a node on a session expiration" +
                    ", this duplication might be caused by a delete delay from the zk server, which means the old expired session" +
                    " may still holds this ZNode and the server just hasn't got time to do the deletion. In this case, " +
                    "we can just try to delete and create again.", e);
            deletePath(path);
            createEphemeral(path); //尝试删除节点，再做一次创建节点处理
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createPersistent(String path, String data) {
        byte[] dataBytes = data.getBytes(CHARSET);
        try {
            client.create().forPath(path, dataBytes); //创建节点，并设置数据
        } catch (NodeExistsException e) { //容错处理（在节点已经存在时，直接设置节点的数据）
            try {
                client.setData().forPath(path, dataBytes); //此处是为路径写数据吗？怎么查看到数据？解：是为指定的路径写数据，有看到节点的数据，zk命令中使用get path就可以看到
            } catch (Exception e1) {
                throw new IllegalStateException(e.getMessage(), e1);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createEphemeral(String path, String data) {
        byte[] dataBytes = data.getBytes(CHARSET);
        try {
            client.create().withMode(CreateMode.EPHEMERAL).forPath(path, dataBytes); //按临时节点往节点中写数据
        } catch (NodeExistsException e) {
            logger.warn("ZNode " + path + " already exists, since we will only try to recreate a node on a session expiration" +
                    ", this duplication might be caused by a delete delay from the zk server, which means the old expired session" +
                    " may still holds this ZNode and the server just hasn't got time to do the deletion. In this case, " +
                    "we can just try to delete and create again.", e);
            deletePath(path);
            createEphemeral(path, data);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void deletePath(String path) { //删除路径
        try {
            client.delete().deletingChildrenIfNeeded().forPath(path);
        } catch (NoNodeException e) {
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getChildren(String path) {
        try {
            return client.getChildren().forPath(path);
        } catch (NoNodeException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public boolean checkExists(String path) { //检查路径在注册中心是否存在
        try {
            if (client.checkExists().forPath(path) != null) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        return client.getZookeeperClient().isConnected();
    }

    @Override
    public String doGetContent(String path) { //获取指定路径的节点的数据内容
        try {
            byte[] dataBytes = client.getData().forPath(path); //通过zk客户端curator获取指定路径对应的内容
            return (dataBytes == null || dataBytes.length == 0) ? null : new String(dataBytes, CHARSET);
        } catch (NoNodeException e) {
            // ignore NoNode Exception.
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void doClose() {
        client.close();
    }

    @Override
    public CuratorZookeeperClient.CuratorWatcherImpl createTargetChildListener(String path, ChildListener listener) {
        return new CuratorZookeeperClient.CuratorWatcherImpl(client, listener, path);
    }

    @Override
    public List<String> addTargetChildListener(String path, CuratorWatcherImpl listener) {
        try {
            return client.getChildren().usingWatcher(listener).forPath(path); //为指定路径的子节点添加监听器
        } catch (NoNodeException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected CuratorZookeeperClient.CuratorWatcherImpl createTargetDataListener(String path, DataListener listener) {
        return new CuratorWatcherImpl(client, listener);
    }

    @Override
    protected void addTargetDataListener(String path, CuratorZookeeperClient.CuratorWatcherImpl treeCacheListener) {
        this.addTargetDataListener(path, treeCacheListener, null);
    }

    @Override
    protected void addTargetDataListener(String path, CuratorZookeeperClient.CuratorWatcherImpl treeCacheListener, Executor executor) {
        try {
            TreeCache treeCache = TreeCache.newBuilder(client, path).setCacheData(false).build();
            treeCacheMap.putIfAbsent(path, treeCache); //将TreeCache缓存起来（TreeCache：用来将zk路径的所有子节点的数据，保存在本地缓存中）

            if (executor == null) { //添加缓存的监听器
                treeCache.getListenable().addListener(treeCacheListener);
            } else {
                treeCache.getListenable().addListener(treeCacheListener, executor);
            }

            treeCache.start(); //启动缓存（不能自动启动）
        } catch (Exception e) {
            throw new IllegalStateException("Add treeCache listener for path:" + path, e);
        }
    }

    @Override
    protected void removeTargetDataListener(String path, CuratorZookeeperClient.CuratorWatcherImpl treeCacheListener) {
        TreeCache treeCache = treeCacheMap.get(path);
        if (treeCache != null) {
            treeCache.getListenable().removeListener(treeCacheListener);
        }
        treeCacheListener.dataListener = null;
    }

    @Override
    public void removeTargetChildListener(String path, CuratorWatcherImpl listener) {
        listener.unwatch();
    }

    static class CuratorWatcherImpl implements CuratorWatcher, TreeCacheListener { //CuratorWatcher观察者的实现类

        private CuratorFramework client;
        private volatile ChildListener childListener;
        private volatile DataListener dataListener;
        private String path;

        public CuratorWatcherImpl(CuratorFramework client, ChildListener listener, String path) {
            this.client = client;
            this.childListener = listener;
            this.path = path;
        }

        public CuratorWatcherImpl(CuratorFramework client, DataListener dataListener) {
            this.dataListener = dataListener;
        }

        protected CuratorWatcherImpl() {
        }

        public void unwatch() { //不监听（即将监听器置为null）
            this.childListener = null;
        }

        @Override
        public void process(WatchedEvent event) throws Exception {
            // if client connect or disconnect to server, zookeeper will queue
            // watched event(Watcher.Event.EventType.None, .., path = null).
            if (event.getType() == Watcher.Event.EventType.None) {
                return;
            }

            if (childListener != null) { //childListener在unwatch()会置为null，所以需要进行非空判断
                childListener.childChanged(path, client.getChildren().usingWatcher(this).forPath(path));
            }
        }

        @Override
        public void childEvent(CuratorFramework client, TreeCacheEvent event) throws Exception {
            if (dataListener != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("listen the zookeeper changed. The changed data:" + event.getData());
                }
                TreeCacheEvent.Type type = event.getType();
                EventType eventType = null;
                String content = null;
                String path = null;
                switch (type) { //将Zookeeper定义的事件类型转换为Dubbo定义的事件类型
                    case NODE_ADDED:
                        eventType = EventType.NodeCreated;
                        path = event.getData().getPath();
                        content = event.getData().getData() == null ? "" : new String(event.getData().getData(), CHARSET);
                        break;
                    case NODE_UPDATED:
                        eventType = EventType.NodeDataChanged;
                        path = event.getData().getPath();
                        content = event.getData().getData() == null ? "" : new String(event.getData().getData(), CHARSET);
                        break;
                    case NODE_REMOVED:
                        path = event.getData().getPath();
                        eventType = EventType.NodeDeleted;
                        break;
                    case INITIALIZED:
                        eventType = EventType.INITIALIZED;
                        break;
                    case CONNECTION_LOST:
                        eventType = EventType.CONNECTION_LOST;
                        break;
                    case CONNECTION_RECONNECTED:
                        eventType = EventType.CONNECTION_RECONNECTED;
                        break;
                    case CONNECTION_SUSPENDED:
                        eventType = EventType.CONNECTION_SUSPENDED;
                        break;

                }
                dataListener.dataChanged(path, content, eventType);
            }
        }
    }

    private class CuratorConnectionStateListener implements ConnectionStateListener { //连接状态变更的监听器
        private final long UNKNOWN_SESSION_ID = -1L;

        private long lastSessionId;
        private URL url;

        public CuratorConnectionStateListener(URL url) {
            this.url = url;
        }

        @Override
        public void stateChanged(CuratorFramework client, ConnectionState state) { //处理连接状态的变更
            int timeout = url.getParameter(TIMEOUT_KEY, DEFAULT_CONNECTION_TIMEOUT_MS);
            int sessionExpireMs = url.getParameter(ZK_SESSION_EXPIRE_KEY, DEFAULT_SESSION_TIMEOUT_MS);

            long sessionId = UNKNOWN_SESSION_ID;
            try {
                sessionId = client.getZookeeperClient().getZooKeeper().getSessionId();
            } catch (Exception e) {
                logger.warn("Curator client state changed, but failed to get the related zk session instance.");
            }

            if (state == ConnectionState.LOST) { //session过期
                logger.warn("Curator zookeeper session " + Long.toHexString(lastSessionId) + " expired.");
                CuratorZookeeperClient.this.stateChanged(StateListener.SESSION_LOST);
            } else if (state == ConnectionState.SUSPENDED) { //连接超时
                logger.warn("Curator zookeeper connection of session " + Long.toHexString(sessionId) + " timed out. " +
                        "connection timeout value is " + timeout + ", session expire timeout value is " + sessionExpireMs);
                CuratorZookeeperClient.this.stateChanged(StateListener.SUSPENDED);
            } else if (state == ConnectionState.CONNECTED) { //连接成功
                lastSessionId = sessionId;
                logger.info("Curator zookeeper client instance initiated successfully, session id is " + Long.toHexString(sessionId));
                CuratorZookeeperClient.this.stateChanged(StateListener.CONNECTED);
            } else if (state == ConnectionState.RECONNECTED) {
                if (lastSessionId == sessionId && sessionId != UNKNOWN_SESSION_ID) {
                    logger.warn("Curator zookeeper connection recovered from connection lose, " +
                            "reuse the old session " + Long.toHexString(sessionId));
                    CuratorZookeeperClient.this.stateChanged(StateListener.RECONNECTED);
                } else {
                    logger.warn("New session created after old session lost, " +
                            "old session " + Long.toHexString(lastSessionId) + ", new session " + Long.toHexString(sessionId));
                    lastSessionId = sessionId;
                    CuratorZookeeperClient.this.stateChanged(StateListener.NEW_SESSION_CREATED);
                }
            }
        }

    }

    /**
     * just for unit test
     *
     * @return
     */
    CuratorFramework getClient() {
        return client;
    }
}
