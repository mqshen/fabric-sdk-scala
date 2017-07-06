package com.ynet.belink.clients.consumer;

import  com.ynet.belink.clients.ApiVersions;
import  com.ynet.belink.clients.ClientUtils;
import  com.ynet.belink.clients.Metadata;
import  com.ynet.belink.clients.NetworkClient;
import com.ynet.belink.clients.consumer.internals.*;
import  com.ynet.belink.clients.consumer.internals.ConsumerNetworkClient.PollCondition;
import  com.ynet.belink.common.Cluster;
import  com.ynet.belink.common.BelinkException;
import  com.ynet.belink.common.Metric;
import  com.ynet.belink.common.MetricName;
import  com.ynet.belink.common.PartitionInfo;
import  com.ynet.belink.common.TopicPartition;
import  com.ynet.belink.common.config.ConfigException;
import  com.ynet.belink.common.errors.InterruptException;
import com.ynet.belink.common.internals.ClusterResourceListeners;
import  com.ynet.belink.common.metrics.MetricConfig;
import  com.ynet.belink.common.metrics.Metrics;
import  com.ynet.belink.common.metrics.MetricsReporter;
import com.ynet.belink.common.metrics.Sensor;
import  com.ynet.belink.common.network.ChannelBuilder;
import  com.ynet.belink.common.network.Selector;
import com.ynet.belink.common.requests.IsolationLevel;
import  com.ynet.belink.common.requests.MetadataRequest;
import  com.ynet.belink.common.serialization.Deserializer;
import  com.ynet.belink.common.utils.AppInfoParser;
import  com.ynet.belink.common.utils.Time;
import  com.ynet.belink.common.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Created by goldratio on 05/07/2017.
 */
public class BelinkConsumer<K, V> implements Consumer<K, V> {
    private static final Logger log = LoggerFactory.getLogger(BelinkConsumer.class);
    private static final long NO_CURRENT_THREAD = -1L;
    private static final AtomicInteger CONSUMER_CLIENT_ID_SEQUENCE = new AtomicInteger(1);
    private static final String JMX_PREFIX = "kafka.consumer";
    static final long DEFAULT_CLOSE_TIMEOUT_MS = 30 * 1000;

    private final String clientId;
    private final ConsumerCoordinator coordinator;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;
    private final Fetcher<K, V> fetcher;
    private final ConsumerInterceptors<K, V> interceptors;

    private final Time time;
    private final ConsumerNetworkClient client;
    private final Metrics metrics;
    private final SubscriptionState subscriptions;
    private final Metadata metadata;
    private final long retryBackoffMs;
    private final long requestTimeoutMs;
    private volatile boolean closed = false;

    // currentThread holds the threadId of the current thread accessing BelinkConsumer
    // and is used to prevent multi-threaded access
    private final AtomicLong currentThread = new AtomicLong(NO_CURRENT_THREAD);
    // refcount is used to allow reentrant access by the thread who has acquired currentThread
    private final AtomicInteger refcount = new AtomicInteger(0);

    /**
     * A consumer is instantiated by providing a set of key-value pairs as configuration. Valid configuration strings
     * are documented <a href="http://kafka.apache.org/documentation.html#consumerconfigs" >here</a>. Values can be
     * either strings or objects of the appropriate type (for example a numeric configuration would accept either the
     * string "42" or the integer 42).
     * <p>
     * Valid configuration strings are documented at {@link ConsumerConfig}
     *
     * @param configs The consumer configs
     */
    public BelinkConsumer(Map<String, Object> configs) {
        this(configs, null, null);
    }

    /**
     * A consumer is instantiated by providing a set of key-value pairs as configuration, and a key and a value {@link Deserializer}.
     * <p>
     * Valid configuration strings are documented at {@link ConsumerConfig}
     *
     * @param configs The consumer configs
     * @param keyDeserializer The deserializer for key that implements {@link Deserializer}. The configure() method
     *            won't be called in the consumer when the deserializer is passed in directly.
     * @param valueDeserializer The deserializer for value that implements {@link Deserializer}. The configure() method
     *            won't be called in the consumer when the deserializer is passed in directly.
     */
    public BelinkConsumer(Map<String, Object> configs,
                         Deserializer<K> keyDeserializer,
                         Deserializer<V> valueDeserializer) {
        this(new ConsumerConfig(ConsumerConfig.addDeserializerToConfig(configs, keyDeserializer, valueDeserializer)),
                keyDeserializer,
                valueDeserializer);
    }

    /**
     * A consumer is instantiated by providing a {@link java.util.Properties} object as configuration.
     * <p>
     * Valid configuration strings are documented at {@link ConsumerConfig}
     *
     * @param properties The consumer configuration properties
     */
    public BelinkConsumer(Properties properties) {
        this(properties, null, null);
    }

    /**
     * A consumer is instantiated by providing a {@link java.util.Properties} object as configuration, and a
     * key and a value {@link Deserializer}.
     * <p>
     * Valid configuration strings are documented at {@link ConsumerConfig}
     *
     * @param properties The consumer configuration properties
     * @param keyDeserializer The deserializer for key that implements {@link Deserializer}. The configure() method
     *            won't be called in the consumer when the deserializer is passed in directly.
     * @param valueDeserializer The deserializer for value that implements {@link Deserializer}. The configure() method
     *            won't be called in the consumer when the deserializer is passed in directly.
     */
    public BelinkConsumer(Properties properties,
                         Deserializer<K> keyDeserializer,
                         Deserializer<V> valueDeserializer) {
        this(new ConsumerConfig(ConsumerConfig.addDeserializerToConfig(properties, keyDeserializer, valueDeserializer)),
                keyDeserializer,
                valueDeserializer);
    }

    @SuppressWarnings("unchecked")
    private BelinkConsumer(ConsumerConfig config,
                          Deserializer<K> keyDeserializer,
                          Deserializer<V> valueDeserializer) {
        try {
            log.debug("Starting the Kafka consumer");
            this.requestTimeoutMs = config.getInt(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG);
            int sessionTimeOutMs = config.getInt(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG);
            int fetchMaxWaitMs = config.getInt(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG);
            if (this.requestTimeoutMs <= sessionTimeOutMs || this.requestTimeoutMs <= fetchMaxWaitMs)
                throw new ConfigException(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG + " should be greater than " + ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG + " and " + ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG);
            this.time = Time.SYSTEM;

            String clientId = config.getString(ConsumerConfig.CLIENT_ID_CONFIG);
            if (clientId.length() <= 0)
                clientId = "consumer-" + CONSUMER_CLIENT_ID_SEQUENCE.getAndIncrement();
            this.clientId = clientId;
            Map<String, String> metricsTags = Collections.singletonMap("client-id", clientId);
            MetricConfig metricConfig = new MetricConfig().samples(config.getInt(ConsumerConfig.METRICS_NUM_SAMPLES_CONFIG))
                    .timeWindow(config.getLong(ConsumerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG), TimeUnit.MILLISECONDS)
                    .recordLevel(Sensor.RecordingLevel.forName(config.getString(ConsumerConfig.METRICS_RECORDING_LEVEL_CONFIG)))
                    .tags(metricsTags);
            List<MetricsReporter> reporters = config.getConfiguredInstances(ConsumerConfig.METRIC_REPORTER_CLASSES_CONFIG,
                    MetricsReporter.class);
            //reporters.add(new JmxReporter(JMX_PREFIX));
            this.metrics = new Metrics(metricConfig, reporters, time);
            this.retryBackoffMs = config.getLong(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG);

            // load interceptors and make sure they get clientId
            Map<String, Object> userProvidedConfigs = config.originals();
            userProvidedConfigs.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
            List<ConsumerInterceptor<K, V>> interceptorList = (List) (new ConsumerConfig(userProvidedConfigs, false)).getConfiguredInstances(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                    ConsumerInterceptor.class);
            this.interceptors = interceptorList.isEmpty() ? null : new ConsumerInterceptors<>(interceptorList);
            if (keyDeserializer == null) {
                this.keyDeserializer = config.getConfiguredInstance(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        Deserializer.class);
                this.keyDeserializer.configure(config.originals(), true);
            } else {
                config.ignore(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG);
                this.keyDeserializer = keyDeserializer;
            }
            if (valueDeserializer == null) {
                this.valueDeserializer = config.getConfiguredInstance(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        Deserializer.class);
                this.valueDeserializer.configure(config.originals(), false);
            } else {
                config.ignore(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
                this.valueDeserializer = valueDeserializer;
            }
            ClusterResourceListeners clusterResourceListeners = configureClusterResourceListeners(keyDeserializer, valueDeserializer, reporters, interceptorList);
            this.metadata = new Metadata(retryBackoffMs, config.getLong(ConsumerConfig.METADATA_MAX_AGE_CONFIG),
                    true, false, clusterResourceListeners);
            List<InetSocketAddress> addresses = ClientUtils.parseAndValidateAddresses(config.getList(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
            this.metadata.update(Cluster.bootstrap(addresses), Collections.<String>emptySet(), 0);
            String metricGrpPrefix = "consumer";
            ConsumerMetrics metricsRegistry = new ConsumerMetrics(metricsTags.keySet(), "consumer");
            ChannelBuilder channelBuilder = ClientUtils.createChannelBuilder(config);

            IsolationLevel isolationLevel = IsolationLevel.valueOf(
                    config.getString(ConsumerConfig.ISOLATION_LEVEL_CONFIG).toUpperCase(Locale.ROOT));
            Sensor throttleTimeSensor = Fetcher.throttleTimeSensor(metrics, metricsRegistry.fetcherMetrics);

            NetworkClient netClient = new NetworkClient(
                    new Selector(config.getLong(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG), metrics, time, metricGrpPrefix, channelBuilder),
                    this.metadata,
                    clientId,
                    100, // a fixed large enough value will suffice for max in-flight requests
                    config.getLong(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG),
                    config.getLong(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG),
                    config.getInt(ConsumerConfig.SEND_BUFFER_CONFIG),
                    config.getInt(ConsumerConfig.RECEIVE_BUFFER_CONFIG),
                    config.getInt(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG),
                    time,
                    true,
                    new ApiVersions(),
                    throttleTimeSensor);
            this.client = new ConsumerNetworkClient(netClient, metadata, time, retryBackoffMs,
                    config.getInt(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG));
            OffsetResetStrategy offsetResetStrategy = OffsetResetStrategy.valueOf(config.getString(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG).toUpperCase(Locale.ROOT));
            this.subscriptions = new SubscriptionState(offsetResetStrategy);
            List<PartitionAssignor> assignors = config.getConfiguredInstances(
                    ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                    PartitionAssignor.class);
            this.coordinator = new ConsumerCoordinator(this.client,
                    config.getString(ConsumerConfig.GROUP_ID_CONFIG),
                    config.getInt(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG),
                    config.getInt(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG),
                    config.getInt(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG),
                    assignors,
                    this.metadata,
                    this.subscriptions,
                    metrics,
                    metricGrpPrefix,
                    this.time,
                    retryBackoffMs,
                    config.getBoolean(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG),
                    config.getInt(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG),
                    this.interceptors,
                    config.getBoolean(ConsumerConfig.EXCLUDE_INTERNAL_TOPICS_CONFIG),
                    config.getBoolean(ConsumerConfig.LEAVE_GROUP_ON_CLOSE_CONFIG));
            this.fetcher = new Fetcher<>(this.client,
                    config.getInt(ConsumerConfig.FETCH_MIN_BYTES_CONFIG),
                    config.getInt(ConsumerConfig.FETCH_MAX_BYTES_CONFIG),
                    config.getInt(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG),
                    config.getInt(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG),
                    config.getInt(ConsumerConfig.MAX_POLL_RECORDS_CONFIG),
                    config.getBoolean(ConsumerConfig.CHECK_CRCS_CONFIG),
                    this.keyDeserializer,
                    this.valueDeserializer,
                    this.metadata,
                    this.subscriptions,
                    metrics,
                    metricsRegistry.fetcherMetrics,
                    this.time,
                    this.retryBackoffMs,
                    isolationLevel);

            config.logUnused();
            AppInfoParser.registerAppInfo(JMX_PREFIX, clientId);

            log.debug("Kafka consumer created");
        } catch (Throwable t) {
            // call close methods if internal objects are already constructed
            // this is to prevent resource leak. see KAFKA-2121
            close(0, true);
            // now propagate the exception
            throw new BelinkException("Failed to construct kafka consumer", t);
        }
    }

    // visible for testing
    BelinkConsumer(String clientId,
                   ConsumerCoordinator coordinator,
                  Deserializer<K> keyDeserializer,
                  Deserializer<V> valueDeserializer,
                  Fetcher<K, V> fetcher,
                  ConsumerInterceptors<K, V> interceptors,
                  Time time,
                  ConsumerNetworkClient client,
                  Metrics metrics,
                  SubscriptionState subscriptions,
                  Metadata metadata,
                  long retryBackoffMs,
                  long requestTimeoutMs) {
        this.clientId = clientId;
        this.coordinator = coordinator;
        this.keyDeserializer = keyDeserializer;
        this.valueDeserializer = valueDeserializer;
        this.fetcher = fetcher;
        this.interceptors = interceptors;
        this.time = time;
        this.client = client;
        this.metrics = metrics;
        this.subscriptions = subscriptions;
        this.metadata = metadata;
        this.retryBackoffMs = retryBackoffMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    private ClusterResourceListeners configureClusterResourceListeners(Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer, List<?>... candidateLists) {
        ClusterResourceListeners clusterResourceListeners = new ClusterResourceListeners();
        for (List<?> candidateList: candidateLists)
            clusterResourceListeners.maybeAddAll(candidateList);

        clusterResourceListeners.maybeAdd(keyDeserializer);
        clusterResourceListeners.maybeAdd(valueDeserializer);
        return clusterResourceListeners;
    }

    /**
     * Get the set of partitions currently assigned to this consumer. If subscription happened by directly assigning
     * partitions using {@link #assign(Collection)} then this will simply return the same partitions that
     * were assigned. If topic subscription was used, then this will give the set of topic partitions currently assigned
     * to the consumer (which may be none if the assignment hasn't happened yet, or the partitions are in the
     * process of getting reassigned).
     * @return The set of partitions currently assigned to this consumer
     */
    public Set<TopicPartition> assignment() {
        acquire();
        try {
            return Collections.unmodifiableSet(new HashSet<>(this.subscriptions.assignedPartitions()));
        } finally {
            release();
        }
    }

    /**
     * Get the current subscription. Will return the same topics used in the most recent call to
     * {@link #subscribe(Collection, ConsumerRebalanceListener)}, or an empty set if no such call has been made.
     * @return The set of topics currently subscribed to
     */
    public Set<String> subscription() {
        acquire();
        try {
            return Collections.unmodifiableSet(new HashSet<>(this.subscriptions.subscription()));
        } finally {
            release();
        }
    }

    /**
     * Subscribe to the given list of topics to get dynamically
     * assigned partitions. <b>Topic subscriptions are not incremental. This list will replace the current
     * assignment (if there is one).</b> Note that it is not possible to combine topic subscription with group management
     * with manual partition assignment through {@link #assign(Collection)}.
     *
     * If the given list of topics is empty, it is treated the same as {@link #unsubscribe()}.
     *
     * <p>
     * As part of group management, the consumer will keep track of the list of consumers that belong to a particular
     * group and will trigger a rebalance operation if one of the following events trigger -
     * <ul>
     * <li>Number of partitions change for any of the subscribed list of topics
     * <li>Topic is created or deleted
     * <li>An existing member of the consumer group dies
     * <li>A new member is added to an existing consumer group via the join API
     * </ul>
     * <p>
     * When any of these events are triggered, the provided listener will be invoked first to indicate that
     * the consumer's assignment has been revoked, and then again when the new assignment has been received.
     * Note that this listener will immediately override any listener set in a previous call to subscribe.
     * It is guaranteed, however, that the partitions revoked/assigned through this interface are from topics
     * subscribed in this call. See {@link ConsumerRebalanceListener} for more details.
     *
     * @param topics The list of topics to subscribe to
     * @param listener Non-null listener instance to get notifications on partition assignment/revocation for the
     *                 subscribed topics
     * @throws IllegalArgumentException If topics is null or contains null or empty elements
     */
    @Override
    public void subscribe(Collection<String> topics, ConsumerRebalanceListener listener) {
        acquire();
        try {
            if (topics == null) {
                throw new IllegalArgumentException("Topic collection to subscribe to cannot be null");
            } else if (topics.isEmpty()) {
                // treat subscribing to empty topic list as the same as unsubscribing
                this.unsubscribe();
            } else {
                for (String topic : topics) {
                    if (topic == null || topic.trim().isEmpty())
                        throw new IllegalArgumentException("Topic collection to subscribe to cannot contain null or empty topic");
                }
                log.debug("Subscribed to topic(s): {}", Utils.join(topics, ", "));
                this.subscriptions.subscribe(new HashSet<>(topics), listener);
                metadata.setTopics(subscriptions.groupSubscription());
            }
        } finally {
            release();
        }
    }

    /**
     * Subscribe to the given list of topics to get dynamically assigned partitions.
     * <b>Topic subscriptions are not incremental. This list will replace the current
     * assignment (if there is one).</b> It is not possible to combine topic subscription with group management
     * with manual partition assignment through {@link #assign(Collection)}.
     *
     * If the given list of topics is empty, it is treated the same as {@link #unsubscribe()}.
     *
     * <p>
     * This is a short-hand for {@link #subscribe(Collection, ConsumerRebalanceListener)}, which
     * uses a noop listener. If you need the ability to seek to particular offsets, you should prefer
     * {@link #subscribe(Collection, ConsumerRebalanceListener)}, since group rebalances will cause partition offsets
     * to be reset. You should also provide your own listener if you are doing your own offset
     * management since the listener gives you an opportunity to commit offsets before a rebalance finishes.
     *
     * @param topics The list of topics to subscribe to
     * @throws IllegalArgumentException If topics is null or contains null or empty elements
     */
    @Override
    public void subscribe(Collection<String> topics) {
        subscribe(topics, new NoOpConsumerRebalanceListener());
    }

    /**
     * Subscribe to all topics matching specified pattern to get dynamically assigned partitions. The pattern matching will be done periodically against topics
     * existing at the time of check.
     *
     * <p>
     * As part of group management, the consumer will keep track of the list of consumers that
     * belong to a particular group and will trigger a rebalance operation if one of the
     * following events trigger -
     * <ul>
     * <li>Number of partitions change for any of the subscribed list of topics
     * <li>Topic is created or deleted
     * <li>An existing member of the consumer group dies
     * <li>A new member is added to an existing consumer group via the join API
     * </ul>
     *
     * @param pattern Pattern to subscribe to
     * @param listener Non-null listener instance to get notifications on partition assignment/revocation for the
     *                 subscribed topics
     * @throws IllegalArgumentException If pattern is null
     */
    @Override
    public void subscribe(Pattern pattern, ConsumerRebalanceListener listener) {
        acquire();
        try {
            if (pattern == null)
                throw new IllegalArgumentException("Topic pattern to subscribe to cannot be null");
            log.debug("Subscribed to pattern: {}", pattern);
            this.subscriptions.subscribe(pattern, listener);
            this.metadata.needMetadataForAllTopics(true);
            this.metadata.requestUpdate();
        } finally {
            release();
        }
    }

    /**
     * Unsubscribe from topics currently subscribed with {@link #subscribe(Collection)}. This
     * also clears any partitions directly assigned through {@link #assign(Collection)}.
     */
    public void unsubscribe() {
        acquire();
        try {
            log.debug("Unsubscribed all topics or patterns and assigned partitions");
            this.subscriptions.unsubscribe();
            this.metadata.needMetadataForAllTopics(false);
        } finally {
            release();
        }
    }

    /**
     * Manually assign a list of partitions to this consumer. This interface does not allow for incremental assignment
     * and will replace the previous assignment (if there is one).
     *
     * If the given list of topic partitions is empty, it is treated the same as {@link #unsubscribe()}.
     *
     * <p>
     * Manual topic assignment through this method does not use the consumer's group management
     * functionality. As such, there will be no rebalance operation triggered when group membership or cluster and topic
     * metadata change. Note that it is not possible to use both manual partition assignment with {@link #assign(Collection)}
     * and group assignment with {@link #subscribe(Collection, ConsumerRebalanceListener)}.
     *
     * @param partitions The list of partitions to assign this consumer
     * @throws IllegalArgumentException If partitions is null or contains null or empty topics
     */
    @Override
    public void assign(Collection<TopicPartition> partitions) {
        acquire();
        try {
            if (partitions == null) {
                throw new IllegalArgumentException("Topic partition collection to assign to cannot be null");
            } else if (partitions.isEmpty()) {
                this.unsubscribe();
            } else {
                Set<String> topics = new HashSet<>();
                for (TopicPartition tp : partitions) {
                    String topic = (tp != null) ? tp.topic() : null;
                    if (topic == null || topic.trim().isEmpty())
                        throw new IllegalArgumentException("Topic partitions to assign to cannot have null or empty topic");
                    topics.add(topic);
                }

                log.debug("Subscribed to partition(s): {}", Utils.join(partitions, ", "));
                this.subscriptions.assignFromUser(new HashSet<>(partitions));
                metadata.setTopics(topics);
            }
        } finally {
            release();
        }
    }

    /**
     * Fetch data for the topics or partitions specified using one of the subscribe/assign APIs. It is an error to not have
     * subscribed to any topics or partitions before polling for data.
     * <p>
     * On each poll, consumer will try to use the last consumed offset as the starting offset and fetch sequentially. The last
     * consumed offset can be manually set through {@link #seek(TopicPartition, long)} or automatically set as the last committed
     * offset for the subscribed list of partitions
     *
     *
     * @param timeout The time, in milliseconds, spent waiting in poll if data is not available in the buffer.
     *            If 0, returns immediately with any records that are available currently in the buffer, else returns empty.
     *            Must not be negative.
     * @return map of topic to records since the last fetch for the subscribed list of topics and partitions
     *
     * @throws  com.ynet.belink.clients.consumer.InvalidOffsetException if the offset for a partition or set of
     *             partitions is undefined or out of range and no offset reset policy has been configured
     * @throws  com.ynet.belink.common.errors.WakeupException if {@link #wakeup()} is called before or while this
     *             function is called
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted before or while
     *             this function is called
     * @throws  com.ynet.belink.common.errors.AuthorizationException if caller lacks Read access to any of the subscribed
     *             topics or to the configured groupId
     * @throws  com.ynet.belink.common.BelinkException for any other unrecoverable errors (e.g. invalid groupId or
     *             session timeout, errors deserializing key/value pairs, or any new error cases in future versions)
     * @throws java.lang.IllegalArgumentException if the timeout value is negative
     * @throws java.lang.IllegalStateException if the consumer is not subscribed to any topics or manually assigned any
     *             partitions to consume from
     */
    @Override
    public ConsumerRecords<K, V> poll(long timeout) {
        acquire();
        try {
            if (timeout < 0)
                throw new IllegalArgumentException("Timeout must not be negative");

            if (this.subscriptions.hasNoSubscriptionOrUserAssignment())
                throw new IllegalStateException("Consumer is not subscribed to any topics or assigned any partitions");

            // poll for new data until the timeout expires
            long start = time.milliseconds();
            long remaining = timeout;
            do {
                Map<TopicPartition, List<ConsumerRecord<K, V>>> records = pollOnce(remaining);
                if (!records.isEmpty()) {
                    // before returning the fetched records, we can send off the next round of fetches
                    // and avoid block waiting for their responses to enable pipelining while the user
                    // is handling the fetched records.
                    //
                    // NOTE: since the consumed position has already been updated, we must not allow
                    // wakeups or any other errors to be triggered prior to returning the fetched records.
                    if (fetcher.sendFetches() > 0 || client.hasPendingRequests())
                        client.pollNoWakeup();

                    if (this.interceptors == null)
                        return new ConsumerRecords<>(records);
                    else
                        return this.interceptors.onConsume(new ConsumerRecords<>(records));
                }

                long elapsed = time.milliseconds() - start;
                remaining = timeout - elapsed;
            } while (remaining > 0);

            return ConsumerRecords.empty();
        } finally {
            release();
        }
    }

    /**
     * Do one round of polling. In addition to checking for new data, this does any needed offset commits
     * (if auto-commit is enabled), and offset resets (if an offset reset policy is defined).
     * @param timeout The maximum time to block in the underlying call to {@link ConsumerNetworkClient#poll(long)}.
     * @return The fetched records (may be empty)
     */
    private Map<TopicPartition, List<ConsumerRecord<K, V>>> pollOnce(long timeout) {
        client.maybeTriggerWakeup();

        // fetch positions if we have partitions we're subscribed to that we
        // don't know the offset for
        if (!subscriptions.hasAllFetchPositions())
            updateFetchPositions(this.subscriptions.missingFetchPositions());

        // if data is available already, return it immediately
        Map<TopicPartition, List<ConsumerRecord<K, V>>> records = fetcher.fetchedRecords();
        if (!records.isEmpty())
            return records;

        // send any new fetches (won't resend pending fetches)
        fetcher.sendFetches();

        long now = time.milliseconds();
        long pollTimeout = Math.min(coordinator.timeToNextPoll(now), timeout);

        client.poll(pollTimeout, now, new PollCondition() {
            @Override
            public boolean shouldBlock() {
                // since a fetch might be completed by the background thread, we need this poll condition
                // to ensure that we do not block unnecessarily in poll()
                return !fetcher.hasCompletedFetches();
            }
        });

        // after the long poll, we should check whether the group needs to rebalance
        // prior to returning data so that the group can stabilize faster
        if (coordinator.needRejoin())
            return Collections.emptyMap();

        return fetcher.fetchedRecords();
    }

    /**
     * Commit offsets returned on the last {@link #poll(long) poll()} for all the subscribed list of topics and partitions.
     * <p>
     * This commits offsets only to Kafka. The offsets committed using this API will be used on the first fetch after
     * every rebalance and also on startup. As such, if you need to store offsets in anything other than Kafka, this API
     * should not be used.
     * <p>
     * This is a synchronous commits and will block until either the commit succeeds or an unrecoverable error is
     * encountered (in which case it is thrown to the caller).
     *
     * @throws  com.ynet.belink.clients.consumer.CommitFailedException if the commit failed and cannot be retried.
     *             This can only occur if you are using automatic group management with {@link #subscribe(Collection)},
     *             or if there is an active group with the same groupId which is using group management.
     * @throws  com.ynet.belink.common.errors.WakeupException if {@link #wakeup()} is called before or while this
     *             function is called
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted before or while
     *             this function is called
     * @throws  com.ynet.belink.common.errors.AuthorizationException if not authorized to the topic or to the
     *             configured groupId
     * @throws  com.ynet.belink.common.BelinkException for any other unrecoverable errors (e.g. if offset metadata
     *             is too large or if the committed offset is invalid).
     */
    @Override
    public void commitSync() {
        acquire();
        try {
            coordinator.commitOffsetsSync(subscriptions.allConsumed(), Long.MAX_VALUE);
        } finally {
            release();
        }
    }

    /**
     * Commit the specified offsets for the specified list of topics and partitions.
     * <p>
     * This commits offsets to Kafka. The offsets committed using this API will be used on the first fetch after every
     * rebalance and also on startup. As such, if you need to store offsets in anything other than Kafka, this API
     * should not be used. The committed offset should be the next message your application will consume,
     * i.e. lastProcessedMessageOffset + 1.
     * <p>
     * This is a synchronous commits and will block until either the commit succeeds or an unrecoverable error is
     * encountered (in which case it is thrown to the caller).
     *
     * @param offsets A map of offsets by partition with associated metadata
     * @throws  com.ynet.belink.clients.consumer.CommitFailedException if the commit failed and cannot be retried.
     *             This can only occur if you are using automatic group management with {@link #subscribe(Collection)},
     *             or if there is an active group with the same groupId which is using group management.
     * @throws  com.ynet.belink.common.errors.WakeupException if {@link #wakeup()} is called before or while this
     *             function is called
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted before or while
     *             this function is called
     * @throws  com.ynet.belink.common.errors.AuthorizationException if not authorized to the topic or to the
     *             configured groupId
     * @throws  com.ynet.belink.common.BelinkException for any other unrecoverable errors (e.g. if offset metadata
     *             is too large or if the committed offset is invalid).
     */
    @Override
    public void commitSync(final Map<TopicPartition, OffsetAndMetadata> offsets) {
        acquire();
        try {
            coordinator.commitOffsetsSync(new HashMap<>(offsets), Long.MAX_VALUE);
        } finally {
            release();
        }
    }

    /**
     * Commit offsets returned on the last {@link #poll(long) poll()} for all the subscribed list of topics and partition.
     * Same as {@link #commitAsync(OffsetCommitCallback) commitAsync(null)}
     */
    @Override
    public void commitAsync() {
        commitAsync(null);
    }

    /**
     * Commit offsets returned on the last {@link #poll(long) poll()} for the subscribed list of topics and partitions.
     * <p>
     * This commits offsets only to Kafka. The offsets committed using this API will be used on the first fetch after
     * every rebalance and also on startup. As such, if you need to store offsets in anything other than Kafka, this API
     * should not be used.
     * <p>
     * This is an asynchronous call and will not block. Any errors encountered are either passed to the callback
     * (if provided) or discarded.
     *
     * @param callback Callback to invoke when the commit completes
     */
    @Override
    public void commitAsync(OffsetCommitCallback callback) {
        acquire();
        try {
            commitAsync(subscriptions.allConsumed(), callback);
        } finally {
            release();
        }
    }

    /**
     * Commit the specified offsets for the specified list of topics and partitions to Kafka.
     * <p>
     * This commits offsets to Kafka. The offsets committed using this API will be used on the first fetch after every
     * rebalance and also on startup. As such, if you need to store offsets in anything other than Kafka, this API
     * should not be used. The committed offset should be the next message your application will consume,
     * i.e. lastProcessedMessageOffset + 1.
     * <p>
     * This is an asynchronous call and will not block. Any errors encountered are either passed to the callback
     * (if provided) or discarded.
     *
     * @param offsets A map of offsets by partition with associate metadata. This map will be copied internally, so it
     *                is safe to mutate the map after returning.
     * @param callback Callback to invoke when the commit completes
     */
    @Override
    public void commitAsync(final Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
        acquire();
        try {
            log.debug("Committing offsets: {} ", offsets);
            coordinator.commitOffsetsAsync(new HashMap<>(offsets), callback);
        } finally {
            release();
        }
    }

    /**
     * Overrides the fetch offsets that the consumer will use on the next {@link #poll(long) poll(timeout)}. If this API
     * is invoked for the same partition more than once, the latest offset will be used on the next poll(). Note that
     * you may lose data if this API is arbitrarily used in the middle of consumption, to reset the fetch offsets
     */
    @Override
    public void seek(TopicPartition partition, long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("seek offset must not be a negative number");
        }
        acquire();
        try {
            log.debug("Seeking to offset {} for partition {}", offset, partition);
            this.subscriptions.seek(partition, offset);
        } finally {
            release();
        }
    }

    /**
     * Seek to the first offset for each of the given partitions. This function evaluates lazily, seeking to the
     * first offset in all partitions only when {@link #poll(long)} or {@link #position(TopicPartition)} are called.
     * If no partition is provided, seek to the first offset for all of the currently assigned partitions.
     */
    public void seekToBeginning(Collection<TopicPartition> partitions) {
        acquire();
        try {
            Collection<TopicPartition> parts = partitions.size() == 0 ? this.subscriptions.assignedPartitions() : partitions;
            for (TopicPartition tp : parts) {
                log.debug("Seeking to beginning of partition {}", tp);
                subscriptions.needOffsetReset(tp, OffsetResetStrategy.EARLIEST);
            }
        } finally {
            release();
        }
    }

    /**
     * Seek to the last offset for each of the given partitions. This function evaluates lazily, seeking to the
     * final offset in all partitions only when {@link #poll(long)} or {@link #position(TopicPartition)} are called.
     * If no partition is provided, seek to the final offset for all of the currently assigned partitions.
     */
    public void seekToEnd(Collection<TopicPartition> partitions) {
        acquire();
        try {
            Collection<TopicPartition> parts = partitions.size() == 0 ? this.subscriptions.assignedPartitions() : partitions;
            for (TopicPartition tp : parts) {
                log.debug("Seeking to end of partition {}", tp);
                subscriptions.needOffsetReset(tp, OffsetResetStrategy.LATEST);
            }
        } finally {
            release();
        }
    }

    /**
     * Get the offset of the <i>next record</i> that will be fetched (if a record with that offset exists).
     *
     * @param partition The partition to get the position for
     * @return The offset
     * @throws  com.ynet.belink.clients.consumer.InvalidOffsetException if no offset is currently defined for
     *             the partition
     * @throws  com.ynet.belink.common.errors.WakeupException if {@link #wakeup()} is called before or while this
     *             function is called
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted before or while
     *             this function is called
     * @throws  com.ynet.belink.common.errors.AuthorizationException if not authorized to the topic or to the
     *             configured groupId
     * @throws  com.ynet.belink.common.BelinkException for any other unrecoverable errors
     */
    public long position(TopicPartition partition) {
        acquire();
        try {
            if (!this.subscriptions.isAssigned(partition))
                throw new IllegalArgumentException("You can only check the position for partitions assigned to this consumer.");
            Long offset = this.subscriptions.position(partition);
            if (offset == null) {
                updateFetchPositions(Collections.singleton(partition));
                offset = this.subscriptions.position(partition);
            }
            return offset;
        } finally {
            release();
        }
    }

    /**
     * Get the last committed offset for the given partition (whether the commit happened by this process or
     * another). This offset will be used as the position for the consumer in the event of a failure.
     * <p>
     * This call may block to do a remote call if the partition in question isn't assigned to this consumer or if the
     * consumer hasn't yet initialized its cache of committed offsets.
     *
     * @param partition The partition to check
     * @return The last committed offset and metadata or null if there was no prior commit
     * @throws  com.ynet.belink.common.errors.WakeupException if {@link #wakeup()} is called before or while this
     *             function is called
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted before or while
     *             this function is called
     * @throws  com.ynet.belink.common.errors.AuthorizationException if not authorized to the topic or to the
     *             configured groupId
     * @throws  com.ynet.belink.common.BelinkException for any other unrecoverable errors
     */
    @Override
    public OffsetAndMetadata committed(TopicPartition partition) {
        acquire();
        try {
            OffsetAndMetadata committed;
            if (subscriptions.isAssigned(partition)) {
                committed = this.subscriptions.committed(partition);
                if (committed == null) {
                    coordinator.refreshCommittedOffsetsIfNeeded();
                    committed = this.subscriptions.committed(partition);
                }
            } else {
                Map<TopicPartition, OffsetAndMetadata> offsets = coordinator.fetchCommittedOffsets(Collections.singleton(partition));
                committed = offsets.get(partition);
            }

            return committed;
        } finally {
            release();
        }
    }

    /**
     * Get the metrics kept by the consumer
     */
    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return Collections.unmodifiableMap(this.metrics.metrics());
    }

    /**
     * Get metadata about the partitions for a given topic. This method will issue a remote call to the server if it
     * does not already have any metadata about the given topic.
     *
     * @param topic The topic to get partition metadata for
     * @return The list of partitions
     * @throws  com.ynet.belink.common.errors.WakeupException if {@link #wakeup()} is called before or while this
     *             function is called
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted before or while
     *             this function is called
     * @throws  com.ynet.belink.common.errors.AuthorizationException if not authorized to the specified topic
     * @throws  com.ynet.belink.common.errors.TimeoutException if the topic metadata could not be fetched before
     *             expiration of the configured request timeout
     * @throws  com.ynet.belink.common.BelinkException for any other unrecoverable errors
     */
    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
        acquire();
        try {
            Cluster cluster = this.metadata.fetch();
            List<PartitionInfo> parts = cluster.partitionsForTopic(topic);
            if (!parts.isEmpty())
                return parts;

            Map<String, List<PartitionInfo>> topicMetadata = fetcher.getTopicMetadata(
                    new MetadataRequest.Builder(Collections.singletonList(topic), true), requestTimeoutMs);
            return topicMetadata.get(topic);
        } finally {
            release();
        }
    }

    /**
     * Get metadata about partitions for all topics that the user is authorized to view. This method will issue a
     * remote call to the server.

     * @return The map of topics and its partitions
     * @throws  com.ynet.belink.common.errors.WakeupException if {@link #wakeup()} is called before or while this
     *             function is called
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted before or while
     *             this function is called
     * @throws  com.ynet.belink.common.errors.TimeoutException if the topic metadata could not be fetched before
     *             expiration of the configured request timeout
     * @throws  com.ynet.belink.common.BelinkException for any other unrecoverable errors
     */
    @Override
    public Map<String, List<PartitionInfo>> listTopics() {
        acquire();
        try {
            return fetcher.getAllTopicMetadata(requestTimeoutMs);
        } finally {
            release();
        }
    }

    /**
     * Suspend fetching from the requested partitions. Future calls to {@link #poll(long)} will not return
     * any records from these partitions until they have been resumed using {@link #resume(Collection)}.
     * Note that this method does not affect partition subscription. In particular, it does not cause a group
     * rebalance when automatic assignment is used.
     * @param partitions The partitions which should be paused
     */
    @Override
    public void pause(Collection<TopicPartition> partitions) {
        acquire();
        try {
            for (TopicPartition partition: partitions) {
                log.debug("Pausing partition {}", partition);
                subscriptions.pause(partition);
            }
        } finally {
            release();
        }
    }

    /**
     * Resume specified partitions which have been paused with {@link #pause(Collection)}. New calls to
     * {@link #poll(long)} will return records from these partitions if there are any to be fetched.
     * If the partitions were not previously paused, this method is a no-op.
     * @param partitions The partitions which should be resumed
     */
    @Override
    public void resume(Collection<TopicPartition> partitions) {
        acquire();
        try {
            for (TopicPartition partition: partitions) {
                log.debug("Resuming partition {}", partition);
                subscriptions.resume(partition);
            }
        } finally {
            release();
        }
    }

    /**
     * Get the set of partitions that were previously paused by a call to {@link #pause(Collection)}.
     *
     * @return The set of paused partitions
     */
    @Override
    public Set<TopicPartition> paused() {
        acquire();
        try {
            return Collections.unmodifiableSet(subscriptions.pausedPartitions());
        } finally {
            release();
        }
    }

    /**
     * Look up the offsets for the given partitions by timestamp. The returned offset for each partition is the
     * earliest offset whose timestamp is greater than or equal to the given timestamp in the corresponding partition.
     *
     * This is a blocking call. The consumer does not have to be assigned the partitions.
     * If the message format version in a partition is before 0.10.0, i.e. the messages do not have timestamps, null
     * will be returned for that partition.
     *
     * Notice that this method may block indefinitely if the partition does not exist.
     *
     * @param timestampsToSearch the mapping from partition to the timestamp to look up.
     * @return a mapping from partition to the timestamp and offset of the first message with timestamp greater
     *         than or equal to the target timestamp. {@code null} will be returned for the partition if there is no
     *         such message.
     * @throws IllegalArgumentException if the target timestamp is negative.
     * @throws  com.ynet.belink.common.errors.UnsupportedVersionException if the broker does not support looking up
     *         the offsets by timestamp.
     */
    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch) {
        for (Map.Entry<TopicPartition, Long> entry : timestampsToSearch.entrySet()) {
            // we explicitly exclude the earliest and latest offset here so the timestamp in the returned
            // OffsetAndTimestamp is always positive.
            if (entry.getValue() < 0)
                throw new IllegalArgumentException("The target time for partition " + entry.getKey() + " is " +
                        entry.getValue() + ". The target time cannot be negative.");
        }
        return fetcher.getOffsetsByTimes(timestampsToSearch, requestTimeoutMs);
    }

    /**
     * Get the first offset for the given partitions.
     * <p>
     * Notice that this method may block indefinitely if the partition does not exist.
     * This method does not change the current consumer position of the partitions.
     *
     * @see #seekToBeginning(Collection)
     *
     * @param partitions the partitions to get the earliest offsets.
     * @return The earliest available offsets for the given partitions
     */
    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions) {
        return fetcher.beginningOffsets(partitions, requestTimeoutMs);
    }

    /**
     * Get the last offset for the given partitions. The last offset of a partition is the offset of the upcoming
     * message, i.e. the offset of the last available message + 1.
     * <p>
     * Notice that this method may block indefinitely if the partition does not exist.
     * This method does not change the current consumer position of the partitions.
     *
     * @see #seekToEnd(Collection)
     *
     * @param partitions the partitions to get the end offsets.
     * @return The end offsets for the given partitions.
     */
    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) {
        return fetcher.endOffsets(partitions, requestTimeoutMs);
    }

    /**
     * Close the consumer, waiting for up to the default timeout of 30 seconds for any needed cleanup.
     * If auto-commit is enabled, this will commit the current offsets if possible within the default
     * timeout. See {@link #close(long, TimeUnit)} for details. Note that {@link #wakeup()}
     * cannot be used to interrupt close.
     *
     * @throws  com.ynet.belink.common.errors.InterruptException if the calling thread is interrupted
     * before or while this function is called
     */
    @Override
    public void close() {
        close(DEFAULT_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Tries to close the consumer cleanly within the specified timeout. This method waits up to
     * <code>timeout</code> for the consumer to complete pending commits and leave the group.
     * If auto-commit is enabled, this will commit the current offsets if possible within the
     * timeout. If the consumer is unable to complete offset commits and gracefully leave the group
     * before the timeout expires, the consumer is force closed. Note that {@link #wakeup()} cannot be
     * used to interrupt close.
     *
     * @param timeout The maximum time to wait for consumer to close gracefully. The value must be
     *                non-negative. Specifying a timeout of zero means do not wait for pending requests to complete.
     * @param timeUnit The time unit for the <code>timeout</code>
     * @throws InterruptException If the thread is interrupted before or while this function is called
     * @throws IllegalArgumentException If the <code>timeout</code> is negative.
     */
    public void close(long timeout, TimeUnit timeUnit) {
        if (timeout < 0)
            throw new IllegalArgumentException("The timeout cannot be negative.");
        acquire();
        try {
            close(timeUnit.toMillis(timeout), false);
        } finally {
            release();
        }
    }

    /**
     * Wakeup the consumer. This method is thread-safe and is useful in particular to abort a long poll.
     * The thread which is blocking in an operation will throw {@link  com.ynet.belink.common.errors.WakeupException}.
     * If no thread is blocking in a method which can throw {@link  com.ynet.belink.common.errors.WakeupException}, the next call to such a method will raise it instead.
     */
    @Override
    public void wakeup() {
        this.client.wakeup();
    }

    private void close(long timeoutMs, boolean swallowException) {
        log.trace("Closing the Kafka consumer.");
        AtomicReference<Throwable> firstException = new AtomicReference<>();
        this.closed = true;
        try {
            if (coordinator != null)
                coordinator.close(Math.min(timeoutMs, requestTimeoutMs));
        } catch (Throwable t) {
            firstException.compareAndSet(null, t);
            log.error("Failed to close coordinator", t);
        }
        ClientUtils.closeQuietly(fetcher, "fetcher", firstException);
        ClientUtils.closeQuietly(interceptors, "consumer interceptors", firstException);
        ClientUtils.closeQuietly(metrics, "consumer metrics", firstException);
        ClientUtils.closeQuietly(client, "consumer network client", firstException);
        ClientUtils.closeQuietly(keyDeserializer, "consumer key deserializer", firstException);
        ClientUtils.closeQuietly(valueDeserializer, "consumer value deserializer", firstException);
        //AppInfoParser.unregisterAppInfo(JMX_PREFIX, clientId);
        log.debug("The Kafka consumer has closed.");
        Throwable exception = firstException.get();
        if (exception != null && !swallowException) {
            if (exception instanceof InterruptException) {
                throw (InterruptException) exception;
            }
            throw new BelinkException("Failed to close kafka consumer", exception);
        }
    }

    /**
     * Set the fetch position to the committed position (if there is one)
     * or reset it using the offset reset policy the user has configured.
     *
     * @param partitions The partitions that needs updating fetch positions
     * @throws NoOffsetForPartitionException If no offset is stored for a given partition and no offset reset policy is
     *             defined
     */
    private void updateFetchPositions(Set<TopicPartition> partitions) {
        // lookup any positions for partitions which are awaiting reset (which may be the
        // case if the user called seekToBeginning or seekToEnd. We do this check first to
        // avoid an unnecessary lookup of committed offsets (which typically occurs when
        // the user is manually assigning partitions and managing their own offsets).
        fetcher.resetOffsetsIfNeeded(partitions);

        if (!subscriptions.hasAllFetchPositions(partitions)) {
            // if we still don't have offsets for the given partitions, then we should either
            // seek to the last committed position or reset using the auto reset policy

            // first refresh commits for all assigned partitions
            coordinator.refreshCommittedOffsetsIfNeeded();

            // then do any offset lookups in case some positions are not known
            fetcher.updateFetchPositions(partitions);
        }
    }

    /*
     * Check that the consumer hasn't been closed.
     */
    private void ensureNotClosed() {
        if (this.closed)
            throw new IllegalStateException("This consumer has already been closed.");
    }

    /**
     * Acquire the light lock protecting this consumer from multi-threaded access. Instead of blocking
     * when the lock is not available, however, we just throw an exception (since multi-threaded usage is not
     * supported).
     * @throws IllegalStateException if the consumer has been closed
     * @throws ConcurrentModificationException if another thread already has the lock
     */
    private void acquire() {
        ensureNotClosed();
        long threadId = Thread.currentThread().getId();
        if (threadId != currentThread.get() && !currentThread.compareAndSet(NO_CURRENT_THREAD, threadId))
            throw new ConcurrentModificationException("BelinkConsumer is not safe for multi-threaded access");
        refcount.incrementAndGet();
    }

    /**
     * Release the light lock protecting the consumer from multi-threaded access.
     */
    private void release() {
        if (refcount.decrementAndGet() == 0)
            currentThread.set(NO_CURRENT_THREAD);
    }
}
