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
package org.apache.dubbo.metadata.report.support;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.Executors.*;
import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.common.utils.StringUtils.replace;
import static org.apache.dubbo.metadata.report.support.Constants.*;

/**
 *
 */
public abstract class AbstractMetadataReport implements MetadataReport {

    protected final static String DEFAULT_ROOT = "dubbo";

    private static final int ONE_DAY_IN_MILLISECONDS = 60 * 24 * 60 * 1000;
    private static final int FOUR_HOURS_IN_MILLISECONDS = 60 * 4 * 60 * 1000;
    // Log output
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    final Map<MetadataIdentifier, Object> allMetadataReports = new ConcurrentHashMap<>(4); //所有的元数据Map

    final Map<MetadataIdentifier, Object> failedReports = new ConcurrentHashMap<>(4); //失败的元数据Map

    private URL reportURL;
    boolean syncReport; //是否同步上报的标识

    // Local disk cache file
    File localCacheFile;
    // Local disk cache, where the special key value.registries records the list of metadata centers, and the others are the list of notified service providers
    final Properties properties = new Properties();

    private final AtomicLong lastCacheChanged = new AtomicLong();

    // ThreadPoolExecutors
    private final ExecutorService reportCacheExecutor;

    public final MetadataReportRetry metadataReportRetry;

    private final ScheduledExecutorService cycleReportExecutor;

    public AbstractMetadataReport(URL reportServerURL) { //把构造函数应用到最极致了，所有的初始化操作都在构造方法中进行
        setUrl(reportServerURL); //没有直接使用赋值，如:this.reportURL = reportServerURL; 是因为setUrl()方法中做了业务逻辑

        this.localCacheFile = initializeLocalCacheFile(reportServerURL);
        loadProperties(); //个人习惯上：一般后一个需要的内容，会通过参数传递，但dubbo内部更多是选择操作成员变量，如此处没有传递localCacheFile，而是方法中按成员变量操作
        syncReport = reportServerURL.getParameter(SYNC_REPORT_KEY, false); //获取是否同步上报的标识，默认是false，即按异步来上报
        metadataReportRetry = new MetadataReportRetry(reportServerURL.getParameter(RETRY_TIMES_KEY, DEFAULT_METADATA_REPORT_RETRY_TIMES),
                reportServerURL.getParameter(RETRY_PERIOD_KEY, DEFAULT_METADATA_REPORT_RETRY_PERIOD));
        this.reportCacheExecutor = newSingleThreadExecutor(new NamedThreadFactory("DubboSaveMetadataReport", true));
        this.cycleReportExecutor = newSingleThreadScheduledExecutor(new NamedThreadFactory("DubboMetadataReportTimer", true));
        // cycle（循环，周期） report the data switch
        if (reportServerURL.getParameter(CYCLE_REPORT_KEY, DEFAULT_METADATA_REPORT_CYCLE_REPORT)) { //默认是周期上报元数据
            cycleReportExecutor.scheduleAtFixedRate(this::publishAll, calculateStartTime(), ONE_DAY_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
        }
    }

    private File initializeLocalCacheFile(URL reportServerURL) { //初始化本地元数据缓存文件：只是创建了文件所在的目录，并没有创建.cache文件
        // Start file save timer
        String defaultFilename = System.getProperty("user.home") +
                "/.dubbo/dubbo-metadata-" +
                reportServerURL.getParameter(APPLICATION_KEY) + "-" +
                replace(reportServerURL.getAddress(), ":", "-") + //字符串替换，若url的地址为host:port，则替换为host-port
                ".cache"; //defaultFilename的值如：/Users/chenshengyong/.dubbo/dubbo-metadata-vic-192.168.3.16-4444.cache，其中via是应用名，192.168.3.16-4444是host和port
        String filename = reportServerURL.getParameter(FILE_KEY, defaultFilename); //从url获取设置的文件路径，若没有则取默认文件路径
        File file = null;
        if (ConfigUtils.isNotEmpty(filename)) {
            file = new File(filename);
            if (!file.exists() && file.getParentFile() != null && !file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs()) { //若文件目录不存在，则进行创建
                    throw new IllegalArgumentException("Invalid service store file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                }
            }
            // if this file exist, firstly delete it. (若存在文件，首先删除她)
            if (!initialized.getAndSet(true) && file.exists()) { //AtomicBoolean中的getAndSet()，会自动设置传入的新值，并返回老的值
                file.delete();
            }
        }
        return file;
    }

    public URL getUrl() {
        return reportURL;
    }

    protected void setUrl(URL url) {
        if (url == null) { //元数据url不能为空
            throw new IllegalArgumentException("metadataReport url == null");
        }
        this.reportURL = url;
    }

    private void doSaveProperties(long version) { //此处都是怎样保存的？保存在属性文件中吗？ 解答：此处的功能是将属性对象Properties，保存到文件中
        if (version < lastCacheChanged.get()) { //使用版本号，进行乐观锁处理并发问题
            return;
        }
        if (localCacheFile == null) {
            return;
        }
        // Save
        try {
            File lockfile = new File(localCacheFile.getAbsolutePath() + ".lock"); //创建本地文件，文件路径如：/Users/chenshengyong/.dubbo/dubbo-metadata-test-null.cache.lock
            if (!lockfile.exists()) { //文件不存在，则创建文件
                lockfile.createNewFile();
            }
            try (RandomAccessFile raf = new RandomAccessFile(lockfile, "rw");
                 FileChannel channel = raf.getChannel()) { //把资源处理，放在try里面，就可以不用手动关闭资源
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    throw new IOException("Can not lock the metadataReport cache file " + localCacheFile.getAbsolutePath() + ", ignore and retry later, maybe multi java process use the file, please config: dubbo.metadata.file=xxx.properties");
                }
                // Save
                try {
                    if (!localCacheFile.exists()) {
                        localCacheFile.createNewFile();
                    }
                    try (FileOutputStream outputFile = new FileOutputStream(localCacheFile)) {
                        properties.store(outputFile, "Dubbo metadataReport Cache");
                    }
                } finally {
                    lock.release();
                }
            }
        } catch (Throwable e) {
            if (version < lastCacheChanged.get()) {
                return;
            } else {
                reportCacheExecutor.execute(new SaveProperties(lastCacheChanged.incrementAndGet()));
            }
            logger.warn("Failed to save service store file, cause: " + e.getMessage(), e);
        }
    }

    void loadProperties() {
        if (localCacheFile != null && localCacheFile.exists()) {
            try (InputStream in = new FileInputStream(localCacheFile)) {
                properties.load(in); //在存在属性文件时，从文件中读取属性值加载到Properties中 （文件中存储的内容是key-value对）
                if (logger.isInfoEnabled()) {
                    logger.info("Load service store file " + localCacheFile + ", data: " + properties);
                }
            } catch (Throwable e) {
                logger.warn("Failed to load service store file " + localCacheFile, e);
            }
        }
    }

    private void saveProperties(MetadataIdentifier metadataIdentifier, String value, boolean add, boolean sync) {
        if (localCacheFile == null) { //localCacheFile值如："/Users/chenshengyong/.dubbo/dubbo-metadata-test-null.cache"
            return;
        }

        try {
            if (add) { //先把内容写到Properties属性对象中
                properties.setProperty(metadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), value);
            } else {
                properties.remove(metadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
            }
            long version = lastCacheChanged.incrementAndGet();
            if (sync) { //然后把属性对象写到文件中
                new SaveProperties(version).run();
            } else {
                reportCacheExecutor.execute(new SaveProperties(version));
            }

        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    @Override
    public String toString() {
        return getUrl().toString();
    }

    private class SaveProperties implements Runnable { //保存属性对象Properties的线程
        private long version;

        private SaveProperties(long version) {
            this.version = version;
        }

        @Override
        public void run() {
            doSaveProperties(version);
        }
    }

    @Override
    public void storeProviderMetadata(MetadataIdentifier providerMetadataIdentifier, ServiceDefinition serviceDefinition) {
        if (syncReport) { //同步处理
            storeProviderMetadataTask(providerMetadataIdentifier, serviceDefinition);
        } else { //异步上报，使用线程池执行
            reportCacheExecutor.execute(() -> storeProviderMetadataTask(providerMetadataIdentifier, serviceDefinition));
        }
    }

    private void storeProviderMetadataTask(MetadataIdentifier providerMetadataIdentifier, ServiceDefinition serviceDefinition) { //上报提供者的元数据
        try {
            if (logger.isInfoEnabled()) {
                logger.info("store provider metadata. Identifier : " + providerMetadataIdentifier + "; definition: " + serviceDefinition);
            }
            allMetadataReports.put(providerMetadataIdentifier, serviceDefinition);
            failedReports.remove(providerMetadataIdentifier); //存储成功后，从失败Mao中移除对应的元素
            Gson gson = new Gson();
            String data = gson.toJson(serviceDefinition); //JSON字符串，data数据如：{"parameters":{"application":"test-service","side":"provider"},"canonicalName":"org.apache.dubbo.rpc.service.EchoService","codeSource":"file:/Users/chenshengyong/self-db/dubbo/dubbo-common/target/classes/","methods":[{"name":"$echo","parameterTypes":["java.lang.Object"],"returnType":"java.lang.Object"}],"types":[{"type":"java.lang.Object","typeBuilderName":"org.apache.dubbo.metadata.definition.builder.DefaultTypeBuilder"}]}
            /**
             * 存储元数据的组件有：Zookeeper、Nacos、Etcd等
             */
            doStoreProviderMetadata(providerMetadataIdentifier, data); //将服务定义的数据，转换为json字符串，存储到远程，如将Zookeeper作为元数据中心的话，会在Zookeeper创建对应的节点
            saveProperties(providerMetadataIdentifier, data, true, !syncReport); //元数据上报到元数据中心后，也会存储一份到本地文件中
        } catch (Exception e) { //若存储元数据异常，则将异常的暂存起来，然后启动重试任务进行重试
            // retry again. If failed again, throw exception.
            failedReports.put(providerMetadataIdentifier, serviceDefinition);
            metadataReportRetry.startRetryTask(); //存储元数据失败时，才会启动重试任务
            logger.error("Failed to put provider metadata " + providerMetadataIdentifier + " in  " + serviceDefinition + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, Map<String, String> serviceParameterMap) {
        if (syncReport) {
            storeConsumerMetadataTask(consumerMetadataIdentifier, serviceParameterMap);
        } else {
            reportCacheExecutor.execute(() -> storeConsumerMetadataTask(consumerMetadataIdentifier, serviceParameterMap));
        }
    }

    public void storeConsumerMetadataTask(MetadataIdentifier consumerMetadataIdentifier, Map<String, String> serviceParameterMap) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info("store consumer metadata. Identifier : " + consumerMetadataIdentifier + "; definition: " + serviceParameterMap);
            }
            allMetadataReports.put(consumerMetadataIdentifier, serviceParameterMap);
            failedReports.remove(consumerMetadataIdentifier);

            Gson gson = new Gson();
            String data = gson.toJson(serviceParameterMap);
            doStoreConsumerMetadata(consumerMetadataIdentifier, data);
            saveProperties(consumerMetadataIdentifier, data, true, !syncReport);
        } catch (Exception e) {
            // retry again. If failed again, throw exception.
            failedReports.put(consumerMetadataIdentifier, serviceParameterMap);
            metadataReportRetry.startRetryTask();
            logger.error("Failed to put consumer metadata " + consumerMetadataIdentifier + ";  " + serviceParameterMap + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveServiceMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
        if (syncReport) {
            doSaveMetadata(metadataIdentifier, url);
        } else { //默认按异步上报
            reportCacheExecutor.execute(() -> doSaveMetadata(metadataIdentifier, url));
        }
    }

    @Override
    public void removeServiceMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        if (syncReport) {
            doRemoveMetadata(metadataIdentifier);
        } else {
            reportCacheExecutor.execute(() -> doRemoveMetadata(metadataIdentifier));
        }
    }

    @Override
    public List<String> getExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        // TODO, fallback to local cache
        return doGetExportedURLs(metadataIdentifier);
    }

    @Override
    public void saveSubscribedData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, Collection<String> urls) {
        if (syncReport) {
            doSaveSubscriberData(subscriberMetadataIdentifier, new Gson().toJson(urls));
        } else {
            reportCacheExecutor.execute(() -> doSaveSubscriberData(subscriberMetadataIdentifier, new Gson().toJson(urls)));
        }
    }


    @Override
    public Set<String> getSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
        String content = doGetSubscribedURLs(subscriberMetadataIdentifier);
        Type setType = new TypeToken<SortedSet<String>>() {
        }.getType();
        return new Gson().fromJson(content, setType);
    }

    String getProtocol(URL url) {
        String protocol = url.getParameter(SIDE_KEY);
        protocol = protocol == null ? url.getProtocol() : protocol;
        return protocol;
    }

    /**
     * @return if need to continue
     */
    public boolean retry() {
        return doHandleMetadataCollection(failedReports);
    }

    private boolean doHandleMetadataCollection(Map<MetadataIdentifier, Object> metadataMap) { //此处接口设计的很应用，metadataMap：可以是成功上报的Map，也可以是失败上报的Map，只是集合不一样，执行逻辑都是一样的
        if (metadataMap.isEmpty()) { //若数据为空，直接返回
            return true; //返回true，表明不再重试
        }
        Iterator<Map.Entry<MetadataIdentifier, Object>> iterable = metadataMap.entrySet().iterator();
        while (iterable.hasNext()) {
            Map.Entry<MetadataIdentifier, Object> item = iterable.next();
            if (PROVIDER_SIDE.equals(item.getKey().getSide())) {
                this.storeProviderMetadata(item.getKey(), (FullServiceDefinition) item.getValue());
            } else if (CONSUMER_SIDE.equals(item.getKey().getSide())) {
                this.storeConsumerMetadata(item.getKey(), (Map) item.getValue());
            }

        }
        return false; //返回false，表明还需要重试，一直重试，直到超过重试次数或失败的缓存Map为空
    }

    /**
     * not private. just for unittest. （仅仅提供给单元测试）
     */
    void publishAll() {
        logger.info("start to publish all metadata.");
        this.doHandleMetadataCollection(allMetadataReports); //发布所有的元数据
    }

    /**
     * between 2:00 am to 6:00 am, the time is random. （凌晨 2:00 至 6:00，时间随机）
     *
     * @return
     */
    long calculateStartTime() { //计算开始时间对应的时间戳，算出来的值，要做为延迟任务的初次延迟时间
        Calendar calendar = Calendar.getInstance(); //通过默认time zone和locate获取Calendar实例（查找默认时区时，会先从系统属性System.getProperty()中查找，若没有设置则从java.home中查找）
        long nowMill = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0); // calendar.getTimeInMillis() 是当前时间的0时0分0秒，比如此处在6月5日的值是1654358400000，对应的时间为2022-06-05 00:00:00
        /**
         * calculateStartTime()方法计算出来的时间，将作为延迟任务开始启动的时间
         * 1）subtract：具体今天结束的时间
         * 2）subtract + 2h小时间戳 + 4小时随机事件戳
         *    a）subtract这个时间段，可以让任务到00:00:00
         *    b）然后在subtract基础上加两个小时，即从2:00 am 凌晨2点开始
         *    c）再在b）基础上加4个小时随机值，即从2:00 am 到6:00 am
         * （对比：在工作中，一般回写cron表达是，就不用计算这种值了）
         */
        long subtract = calendar.getTimeInMillis() + ONE_DAY_IN_MILLISECONDS - nowMill; //subtract：减去，此处subtract对应的时间戳：指的是距离今天结束还剩的时间戳
        return subtract + (FOUR_HOURS_IN_MILLISECONDS / 2) + ThreadLocalRandom.current().nextInt(FOUR_HOURS_IN_MILLISECONDS);
    }

    class MetadataReportRetry { //内部类：元数据重试上报
        protected final Logger logger = LoggerFactory.getLogger(getClass());

        final ScheduledExecutorService retryExecutor = newScheduledThreadPool(0, new NamedThreadFactory("DubboMetadataReportRetryTimer", true));
        volatile ScheduledFuture retryScheduledFuture; //volatile的两个作用：1）确保内存可见性，2）防止指令重排（成员变量为引用类型时，若没有赋值，则为null）
        final AtomicInteger retryCounter = new AtomicInteger(0);
        // retry task schedule period （重试任务的时间间隔）
        long retryPeriod; //重试周期（long类型的成员变量，初始值为0）
        // if no failed report, wait how many times to run retry task. （在没有失败的上报时，允许的最大重试次数）
        int retryTimesIfNonFail = 600;

        int retryLimit; //重试次数（int类型的成员变量，初始值为0）

        public MetadataReportRetry(int retryTimes, int retryPeriod) { //进入构造函数的前，对象的成员变量已经初始化了
            this.retryPeriod = retryPeriod;
            this.retryLimit = retryTimes;
        }

        void startRetryTask() { //开启重试任务
            if (retryScheduledFuture == null) { //双重判定 + synchronized + volatile 实现懒汉式单例的创建
                synchronized (retryCounter) { //对retryCounter对象加锁
                    if (retryScheduledFuture == null) {
                        retryScheduledFuture = retryExecutor.scheduleWithFixedDelay(new Runnable() {
                            @Override
                            public void run() { //线程的执行体
                                // Check and connect to the metadata
                                try {
                                    int times = retryCounter.incrementAndGet(); // 统计执行的次数
                                    logger.info("start to retry task for metadata report. retry times:" + times);
                                    if (retry() && times > retryTimesIfNonFail) { //在失败集合为空，且超过最大重试次数时，取消重试任务
                                        cancelRetryTask();
                                    }
                                    if (times > retryLimit) { //若实际执行的次数超过预定的次数，则取消重试任务
                                        cancelRetryTask();
                                    }
                                } catch (Throwable t) { // Defensive fault tolerance
                                    logger.error("Unexpected error occur at failed retry, cause: " + t.getMessage(), t);
                                }
                            }
                        }, 500, retryPeriod, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        void cancelRetryTask() {
            if (retryScheduledFuture != null) {
                retryScheduledFuture.cancel(false);
            }
            shutdown(retryExecutor);
        }
    }

    private void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, List<String> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }
        List<String> encodedUrlList = new ArrayList<>(urls.size());
        for (String url : urls) {
            encodedUrlList.add(URL.encode(url));
        }
        doSaveSubscriberData(subscriberMetadataIdentifier, encodedUrlList);
    }

    @Override
    public final void close() throws Exception {
        this.shutdownThreadPoolExecutors();
        this.clearCache();
        doClose();
    }

    protected abstract void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions);

    protected abstract void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString); //存储提供者、消费者数据，底层调用的接口都是一样的，都是传入MetadataIdentifier元数据，只是内容不一致而已

    protected abstract void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url);

    protected abstract void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier);

    protected abstract List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier);

    protected abstract void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urlListStr);

    protected abstract String doGetSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier);

    /**
     * Close other resources
     *
     * @since 2.7.8
     */
    protected void doClose() throws Exception {

    }

    private void clearCache() {
        this.properties.clear();
        this.allMetadataReports.clear();
        this.failedReports.clear();
        this.localCacheFile.delete();
    }

    private void shutdownThreadPoolExecutors() {
        this.metadataReportRetry.cancelRetryTask();
        shutdown(this.reportCacheExecutor);
        shutdown(cycleReportExecutor);
    }

    private static void shutdown(ExecutorService executorService) {
        if (executorService == null) {
            return;
        }
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

}
