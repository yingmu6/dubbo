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
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.*;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.common.constants.RegistryConstants.*;
import static org.apache.dubbo.registry.Constants.REGISTRY_FILESAVE_SYNC_KEY;
import static org.apache.dubbo.registry.Constants.REGISTRY__LOCAL_FILE_CACHE_ENABLED;

/**
 * AbstractRegistry. (SPI, Prototype, ThreadSafe)
 */
public abstract class AbstractRegistry implements Registry { //注册中心抽象实现类

    // URL address separator, used in file cache, service provider URL separation
    private static final char URL_SEPARATOR = ' ';
    // URL address separated regular expression for parsing the service provider URL list in the file cache
    private static final String URL_SPLIT = "\\s+";
    // Max times to retry to save properties to local cache file
    private static final int MAX_RETRY_TIMES_SAVE_PROPERTIES = 3; //将属性值保存到本地缓存文件的最大重试次数
    // Log output
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    // Local disk cache, where the special key value.registries records the list of registry centers, and the others are the list of notified service providers
    private final Properties properties = new Properties();
    // File cache timing writing
    private final ExecutorService registryCacheExecutor = Executors.newFixedThreadPool(1, new NamedThreadFactory("DubboSaveRegistryCache", true));
    // Is it synchronized to save the file
    private boolean syncSaveFile; //是否同步保存文件
    private final AtomicLong lastCacheChanged = new AtomicLong(); //本地缓存文件，最近变更的版本，可用于乐观锁处理
    private final AtomicInteger savePropertiesRetryTimes = new AtomicInteger();

    // 使用Set集合，根据Set的特性不会出现重复的元素，也就是可以反复注册同一个url，使用集合支持幂等
    private final Set<URL> registered = new ConcurrentHashSet<>(); //被注册的url列表
    // 订阅的url与关联的监听器列表的缓存（一个url的数据变更可以被多个监听器监听）
    private final ConcurrentMap<URL, Set<NotifyListener>> subscribed = new ConcurrentHashMap<>();
    // notified数据格式：ConcurrentMap<URL, Map<category, List<URL>>>
    private final ConcurrentMap<URL, Map<String, List<URL>>> notified = new ConcurrentHashMap<>();
    private URL registryUrl; //注册的url
    // Local disk cache file
    private File file; //本地缓存文件

    /**
     * AbstractRegistry构造函数主要处理逻辑：
     * 1）查找指定路径的缓存文件，若不存在则创建file
     * 2）读取缓存文件file的内容，写到属性对象Properties中
     * 3）从缓存subscribed查出指定url对应的监听器NotifyListener，依次做通知处理
     * 并把变更的内容写到缓存文件中
     */
    public AbstractRegistry(URL url) { // 构造函数中做初始化操作
        setUrl(url);
        if (url.getParameter(REGISTRY__LOCAL_FILE_CACHE_ENABLED, true)) { //默认将注册的数据缓存到本地
            // Start file save timer
            syncSaveFile = url.getParameter(REGISTRY_FILESAVE_SYNC_KEY, false); //是否同步保存文件，默认false：异步

            //缓存文件的完整限定名：/Users/chenshengyong/.dubbo/dubbo-registry-null-172.16.140.148-51198.cache
            String defaultFilename = System.getProperty("user.home") + "/.dubbo/dubbo-registry-" + url.getParameter(APPLICATION_KEY) + "-" + url.getAddress().replaceAll(":", "-") + ".cache";
            String filename = url.getParameter(FILE_KEY, defaultFilename); //可以在url中执行本地缓存文件的路径，未指定的话，去默认的值
            File file = null;
            if (ConfigUtils.isNotEmpty(filename)) {
                file = new File(filename);
                if (!file.exists() && file.getParentFile() != null && !file.getParentFile().exists()) {
                    if (!file.getParentFile().mkdirs()) { //若文件目录不存在，则去创建，创建文件目录失败，则抛出异常
                        throw new IllegalArgumentException("Invalid registry cache file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                    }
                }
            }
            this.file = file;
            // When starting the subscription center,
            // we need to read the local cache file for future Registry fault tolerance processing（容错处理）
            // （我们需要读取本地缓存文件以供以后的注册表容错处理）.
            loadProperties();
            notify(url.getBackupUrls());
        }
    }

    protected static List<URL> filterEmpty(URL url, List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) { //在url列表为空时，设置一个空协议的url加入列表并返回
            List<URL> result = new ArrayList<>(1);
            result.add(url.setProtocol(EMPTY_PROTOCOL));
            return result;
        }
        return urls;
    }

    @Override
    public URL getUrl() {
        return registryUrl;
    }

    protected void setUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("registry url == null");
        }
        this.registryUrl = url;
    }

    public Set<URL> getRegistered() {
        return Collections.unmodifiableSet(registered);
    }

    public Map<URL, Set<NotifyListener>> getSubscribed() {
        return Collections.unmodifiableMap(subscribed);
    }

    public Map<URL, Map<String, List<URL>>> getNotified() {
        return Collections.unmodifiableMap(notified);
    }

    public File getCacheFile() {
        return file;
    }

    public Properties getCacheProperties() {
        return properties;
    }

    public AtomicLong getLastCacheChanged() {
        return lastCacheChanged;
    }

    /**
     * 将属性对象的Properties写入本地缓存文件file的主要逻辑：
     * 1）检查版本号，判断是否有并发操作过。
     * 2）查找file绝对路径下是否有.lock辅助文件，若没有则创建
     * 3）尝试对.lock文件加锁，若加锁失败，表明有其它线程在操作该文件，则抛出异常进行重新保存操作
     * 4）查找file文件，若不存在文件，则创建文件，并将属性对象Properties内容写到文件中
     */
    public void doSaveProperties(long version) {
        if (version < lastCacheChanged.get()) { //在操作前，先判断版本是否有落后，落后表明有其它线程操作过了，就不处理了
            return;
        }
        if (file == null) {
            return;
        }
        // Save
        try {
            File lockfile = new File(file.getAbsolutePath() + ".lock"); //.lock文件并没有写具体内容，只是做辅助作用。用来判断是否有别的线程操作
            if (!lockfile.exists()) {
                lockfile.createNewFile(); //若文件不存在，则进行创建
            }
            try (RandomAccessFile raf = new RandomAccessFile(lockfile, "rw"); //随机访问文件指定为可读可写
                 FileChannel channel = raf.getChannel()) {
                FileLock lock = channel.tryLock();
                if (lock == null) { //加锁失败后，会进行异常捕获，并进行重试操作
                    throw new IOException("Can not lock the registry cache file " + file.getAbsolutePath() + ", ignore and retry later, maybe multi java process use the file, please config: dubbo.registry.file=xxx.properties");
                }
                // Save
                try {
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    try (FileOutputStream outputFile = new FileOutputStream(file)) {
                        //把属性对象的值，写到本地文件中， 实现内存 -》文件的数据转换
                        properties.store(outputFile, "Dubbo Registry Cache"); // 第二个参数会作为注释内容 带上"#"写到文件中
                    }
                } finally {
                    lock.release(); //释放锁
                }
            }
        } catch (Throwable e) { //保存失败后做重试操作
            savePropertiesRetryTimes.incrementAndGet();
            if (savePropertiesRetryTimes.get() >= MAX_RETRY_TIMES_SAVE_PROPERTIES) { //超过最大重试次数，不进行后续重试操作
                logger.warn("Failed to save registry cache file after retrying " + MAX_RETRY_TIMES_SAVE_PROPERTIES + " times, cause: " + e.getMessage(), e);
                savePropertiesRetryTimes.set(0); //不再执行时，将重试次数置为0
                return;
            }
            if (version < lastCacheChanged.get()) {
                savePropertiesRetryTimes.set(0);
                return;
            } else { //重新尝试保存属性到缓存文件，并把版本号+1
                registryCacheExecutor.execute(new SaveProperties(lastCacheChanged.incrementAndGet()));
            }
            logger.warn("Failed to save registry cache file, will retry, cause: " + e.getMessage(), e);
        }
    }

    private void loadProperties() { //加载本地缓存文件，并将文件的内容写到属性对象Properties中
        if (file != null && file.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                properties.load(in); //若缓存文件存在，则从文件中读取内容，写到Properties属性对象中
                if (logger.isInfoEnabled()) {
                    logger.info("Load registry cache file " + file + ", data: " + properties);
                }
            } catch (Throwable e) {
                logger.warn("Failed to load registry cache file " + file, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
            }
        }
    }

    public List<URL> getCacheUrls(URL url) { //获取缓存中指定serviceKey对应的URL列表
        for (Map.Entry<Object, Object> entry : properties.entrySet()) { //遍历Properties中的键值对
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if (StringUtils.isNotEmpty(key) && key.equals(url.getServiceKey()) //匹配url的serviceKey
                    && (Character.isLetter(key.charAt(0)) || key.charAt(0) == '_') //第一个字符是字母或为"_"
                    && StringUtils.isNotEmpty(value)) {
                String[] arr = value.trim().split(URL_SPLIT); //按分隔符进行分隔
                List<URL> urls = new ArrayList<>();
                for (String u : arr) {
                    urls.add(URL.valueOf(u));
                }
                return urls;
            }
        }
        return null;
    }

    @Override
    public List<URL> lookup(URL url) { //查询符合条件的注册数据
        List<URL> result = new ArrayList<>();
        Map<String, List<URL>> notifiedUrls = getNotified().get(url); //获取需要通知的数据
        if (CollectionUtils.isNotEmptyMap(notifiedUrls)) { //从缓存中查到数据
            for (List<URL> urls : notifiedUrls.values()) {
                for (URL u : urls) {
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        } else {
            final AtomicReference<List<URL>> reference = new AtomicReference<>();
            NotifyListener listener = reference::set;
            subscribe(url, listener); // Subscribe logic guarantees the first notify to return（订阅逻辑保证第一个通知返回）
            List<URL> urls = reference.get();
            if (CollectionUtils.isNotEmpty(urls)) {
                for (URL u : urls) {
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void register(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("register url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Register: " + url);
        }
        registered.add(url); //将url添加到集合中
    }

    @Override
    public void unregister(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("unregister url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unregister: " + url);
        }
        registered.remove(url); //从已经注册的集合中，移除对应的url
    }

    @Override
    public void subscribe(URL url, NotifyListener listener) { //订阅符合条件的注册数据
        if (url == null) {
            throw new IllegalArgumentException("subscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("subscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Subscribe: " + url);
        }
        Set<NotifyListener> listeners = subscribed.computeIfAbsent(url, n -> new ConcurrentHashSet<>()); //若Map的key已存在，会返回原来的值。此处的逻辑即为，如果订阅条件url相同，会在原来的基础添加NotifyListener
        listeners.add(listener); //将监听器添加到缓存中
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) { //取消订阅，并删除对应的监听器
        if (url == null) {
            throw new IllegalArgumentException("unsubscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("unsubscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unsubscribe: " + url);
        }
        Set<NotifyListener> listeners = subscribed.get(url);
        if (listeners != null) {
            listeners.remove(listener); //此处删除的是监听器，而不是Map中对应的key，因为一个url可以被多个监听器监听，不能把别的监听器移除
        }
    }

    protected void recover() throws Exception { //恢复处理，recover：恢复（支持冥等操作，因为设计存储的Set集合本身就会对重复的元素处理）
        // register
        Set<URL> recoverRegistered = new HashSet<>(getRegistered());
        if (!recoverRegistered.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover register url " + recoverRegistered);
            }
            for (URL url : recoverRegistered) { //重新注册数据
                register(url);
            }
        }
        // subscribe
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover subscribe url " + recoverSubscribed.keySet());
            }
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) { //重新订阅注册数据
                    subscribe(url, listener);
                }
            }
        }
    }

    protected void notify(List<URL> urls) { //通知处理
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }

        for (Map.Entry<URL, Set<NotifyListener>> entry : getSubscribed().entrySet()) { //遍历订阅的缓存Map
            URL url = entry.getKey(); //消费端的url

            // 若缓存中url与传入的url不匹配，则不进行后续的通知处理
            if (!UrlUtils.isMatch(url, urls.get(0))) {
                continue;
            }

            Set<NotifyListener> listeners = entry.getValue();
            if (listeners != null) {
                for (NotifyListener listener : listeners) { //遍历url对应的消费端监听器，依次做通知处理
                    try {
                        notify(url, listener, filterEmpty(url, urls));
                    } catch (Throwable t) {
                        logger.error("Failed to notify registry event, urls: " + urls + ", cause: " + t.getMessage(), t);
                    }
                }
            }
        }
    }

    /**
     * Notify changes from the Provider side.（从提供者端通知改变）
     *
     * @param url      consumer side url（消费端的url）
     * @param listener listener
     * @param urls     provider latest urls（服务者最新的url列表）
     */
    protected void notify(URL url, NotifyListener listener, List<URL> urls) { //通知数据变更
        if (url == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }
        if ((CollectionUtils.isEmpty(urls))
                && !ANY_VALUE.equals(url.getServiceInterface())) {
            logger.warn("Ignore empty notify urls for subscribe url " + url);
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("Notify urls for subscribe url " + url + ", urls: " + urls);
        }
        // keep every provider's category.
        Map<String, List<URL>> result = new HashMap<>();
        for (URL u : urls) { //将url按照category分类
            if (UrlUtils.isMatch(url, u)) {
                String category = u.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
                List<URL> categoryList = result.computeIfAbsent(category, k -> new ArrayList<>());
                categoryList.add(u); //保留提供者的分类信息
            }
        }
        if (result.size() == 0) {
            return;
        }
        Map<String, List<URL>> categoryNotified = notified.computeIfAbsent(url, u -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            List<URL> categoryList = entry.getValue();
            categoryNotified.put(category, categoryList); //缓存设置
            listener.notify(categoryList); //根据传入的监听器实例，做通知处理，如RegistryDirectory#notify的处理
            // We will update our cache file after each notification.（在每次通知后，会更新缓存文件）
            // When our Registry has a subscribe failure due to network jitter, we can return at least the existing cache URL.
            // (当我们的Registry由于网络抖动导致订阅失败时，我们至少可以返回现有的缓存URL)
            saveProperties(url); //通知变更，并把变更的内容存在本地内容和文件
        }
    }

    private void saveProperties(URL url) { //将属性对象的值写到本地缓存文件中
        if (file == null) {
            return;
        }

        try {
            StringBuilder buf = new StringBuilder();
            Map<String, List<URL>> categoryNotified = notified.get(url);
            if (categoryNotified != null) {
                for (List<URL> us : categoryNotified.values()) {
                    for (URL u : us) { //将url列表的值依次拼接
                        if (buf.length() > 0) {
                            buf.append(URL_SEPARATOR);
                        }
                        buf.append(u.toFullString());
                    }
                }
            }
            properties.setProperty(url.getServiceKey(), buf.toString()); //将拼接的内容存在属性对象中
            long version = lastCacheChanged.incrementAndGet(); //将版本号自动加1
            if (syncSaveFile) {
                doSaveProperties(version); //同步保存
            } else {
                registryCacheExecutor.execute(new SaveProperties(version)); //异步保存
            }
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    @Override
    public void destroy() { //销毁处理
        if (logger.isInfoEnabled()) {
            logger.info("Destroy registry:" + getUrl());
        }
        Set<URL> destroyRegistered = new HashSet<>(getRegistered());
        if (!destroyRegistered.isEmpty()) {
            for (URL url : new HashSet<>(getRegistered())) {
                if (url.getParameter(DYNAMIC_KEY, true)) { //若节点设置为动态的，则做取消注册操作，从本地缓存Map中移除该节点
                    try {
                        unregister(url); //取消注册
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unregister url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unregister url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
        Map<URL, Set<NotifyListener>> destroySubscribed = new HashMap<>(getSubscribed());
        if (!destroySubscribed.isEmpty()) {
            for (Map.Entry<URL, Set<NotifyListener>> entry : destroySubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    try {
                        unsubscribe(url, listener); //取消订阅
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unsubscribe url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unsubscribe url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
        AbstractRegistryFactory.removeDestroyedRegistry(this);
    }

    protected boolean acceptable(URL urlToRegistry) { //acceptable：可接受的
        String pattern = registryUrl.getParameter(ACCEPTS_KEY);
        if (StringUtils.isEmpty(pattern)) {
            return true;
        }

        return Arrays.stream(COMMA_SPLIT_PATTERN.split(pattern))
                .anyMatch(p -> p.equalsIgnoreCase(urlToRegistry.getProtocol())); //如果设置了accepts参数，则匹配配置的协议是否在可接受的范围内
    }

    @Override
    public String toString() {
        return getUrl().toString();
    }

    private class SaveProperties implements Runnable {
        private long version;

        private SaveProperties(long version) {
            this.version = version;
        }

        @Override
        public void run() {
            doSaveProperties(version);
        }
    }

}
