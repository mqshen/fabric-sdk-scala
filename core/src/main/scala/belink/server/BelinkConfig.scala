package belink.server

import java.util.Properties

import belink.api.ApiVersion
import belink.cluster.EndPoint
import belink.coordinator.group.OffsetConfig
import belink.message.{CompressionCodec, Message, MessageSet}
import belink.utils.CoreUtils
import com.ynet.belink.clients.CommonClientConfigs
import com.ynet.belink.common.config.{AbstractConfig, ConfigDef, ConfigException, SaslConfigs}
import com.ynet.belink.common.metrics.Sensor
import com.ynet.belink.common.network.ListenerName
import com.ynet.belink.common.protocol.SecurityProtocol

import scala.collection.Map

/**
  * Created by goldratio on 27/06/2017.
  */
object Defaults {
  val BrokerId = -1

  val SocketSendBufferBytes: Int = 100 * 1024
  val SocketReceiveBufferBytes: Int = 100 * 1024
  val SocketRequestMaxBytes: Int = 100 * 1024 * 1024

  val MaxConnectionsPerIp: Int = Int.MaxValue
  val MaxConnectionsPerIpOverrides: String = ""
  val ConnectionsMaxIdleMs = 10 * 60 * 1000L
  val RequestTimeoutMs = 30000
  val QueuedMaxRequests = 500

  val NumIoThreads = 8

  val FetchPurgatoryPurgeIntervalRequests = 1000

  val NumNetworkThreads = 3
  val BackgroundThreads = 10
  /** ********* Kafka Metrics Configuration ***********/
  val MetricNumSamples = 2
  val MetricSampleWindowMs = 30000
  val MetricReporterClasses = ""
  val MetricRecordingLevel = Sensor.RecordingLevel.INFO.toString()

  val MessageMaxBytes = 1000000 + MessageSet.LogOverhead

  val BlockchainSyncIntervalMs = 5 * 1000L
  val NumRecoveryThreadsPerDataDir = 1

  val LogPreAllocateEnable = false
  /************* Authorizer Configuration ***********/
  val AuthorizerClassName = ""

  /** ********* Socket Server Configuration ***********/
  val Port = 9092
  val HostName: String = new String("")
  /** ********* Log Configuration ***********/
  val NumPartitions = 1
  val LogDir = "/tmp/kafka-logs"
  val LogRetentionBytes = -1L
  val LogRetentionHours = 24 * 7
  val LogSegmentBytes = 1 * 1024 * 1024 * 1024
  val LogRollJitterHours = 0
  val LogIndexSizeMaxBytes = 10 * 1024 * 1024
  val LogIndexIntervalBytes = 4096
  val LogRollHours = 24 * 7
  val LogFlushSchedulerIntervalMs = Long.MaxValue
  val LogFlushStartOffsetCheckpointIntervalMs = 60000
  val LogFlushOffsetCheckpointIntervalMs = 60000
  val LogCleanupIntervalMs = 5 * 60 * 1000L
  val LogDeleteDelayMs = 60000
  val LogCleanerDeleteRetentionMs = 24 * 60 * 60 * 1000L
  val LogCleanerMinCompactionLagMs = 0L
  val LogCleanerMinCleanRatio = 0.5d
  val LogMessageTimestampDifferenceMaxMs = LogRetentionHours * 60 * 60 * 1000L

  val Delete = "delete"
  val Compact = "compact"
  val LogCleanupPolicy = Delete

  val CompressionType = "producer"

  val InterBrokerProtocolVersion = ApiVersion.latestVersion.toString
  lazy val LogMessageFormatVersion = InterBrokerProtocolVersion

  val LogMessageTimestampType = "CreateTime"

  val LogFlushIntervalMessages = Long.MaxValue

  /** ********* Transaction Configuration ***********/
  val TransactionalIdExpirationMsDefault = 604800000

  /** ********* Sasl configuration ***********/
  val SaslMechanismInterBrokerProtocol = SaslConfigs.DEFAULT_SASL_MECHANISM
  val SaslEnabledMechanisms = SaslConfigs.DEFAULT_SASL_ENABLED_MECHANISMS
  val SaslKerberosKinitCmd = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD
  val SaslKerberosTicketRenewWindowFactor = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_WINDOW_FACTOR
  val SaslKerberosTicketRenewJitter = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_JITTER
  val SaslKerberosMinTimeBeforeRelogin = SaslConfigs.DEFAULT_KERBEROS_MIN_TIME_BEFORE_RELOGIN
  val SaslKerberosPrincipalToLocalRules = SaslConfigs.DEFAULT_SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES


  /** ********* Offset management configuration ***********/
  val OffsetMetadataMaxSize = OffsetConfig.DefaultMaxMetadataSize
  val OffsetsLoadBufferSize = OffsetConfig.DefaultLoadBufferSize
  val OffsetsTopicReplicationFactor = OffsetConfig.DefaultOffsetsTopicReplicationFactor
  val OffsetsTopicPartitions: Int = OffsetConfig.DefaultOffsetsTopicNumPartitions
  val OffsetsTopicSegmentBytes: Int = OffsetConfig.DefaultOffsetsTopicSegmentBytes
  val OffsetsTopicCompressionCodec: Int = OffsetConfig.DefaultOffsetsTopicCompressionCodec.codec
  val OffsetsRetentionMinutes: Int = 24 * 60
  val OffsetsRetentionCheckIntervalMs: Long = OffsetConfig.DefaultOffsetsRetentionCheckIntervalMs
  val OffsetCommitTimeoutMs = OffsetConfig.DefaultOffsetCommitTimeoutMs
  val OffsetCommitRequiredAcks = OffsetConfig.DefaultOffsetCommitRequiredAcks

  /** ********* Quota Configuration ***********/
  val ProducerQuotaBytesPerSecondDefault = ClientQuotaManagerConfig.QuotaBytesPerSecondDefault
  val ConsumerQuotaBytesPerSecondDefault = ClientQuotaManagerConfig.QuotaBytesPerSecondDefault
  val NumQuotaSamples: Int = ClientQuotaManagerConfig.DefaultNumQuotaSamples
  val QuotaWindowSizeSeconds: Int = ClientQuotaManagerConfig.DefaultQuotaWindowSizeSeconds
  val NumReplicationQuotaSamples: Int = ReplicationQuotaManagerConfig.DefaultNumQuotaSamples
  val ReplicationQuotaWindowSizeSeconds: Int = ReplicationQuotaManagerConfig.DefaultQuotaWindowSizeSeconds

  /** ********* Replication configuration ***********/
  val ControllerSocketTimeoutMs = RequestTimeoutMs
  val ControllerMessageQueueSize = Int.MaxValue
  val DefaultReplicationFactor = 1

  val AutoCreateTopicsEnable = true

  val ListenerSecurityProtocolMap: String = EndPoint.DefaultSecurityProtocolMap.map { case (listenerName, securityProtocol) =>
    s"${listenerName.value}:${securityProtocol.name}"
  }.mkString(",")
}

object BelinkConfig {
  private val LogConfigPrefix = "log."

  val MaxConnectionsPerIpProp = "max.connections.per.ip"
  val MaxConnectionsPerIpOverridesProp = "max.connections.per.ip.overrides"

  val BrokerIdProp = "broker.id"

  /** ********* Kafka Metrics Configuration ***********/
  val MetricSampleWindowMsProp = CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_CONFIG
  val MetricNumSamplesProp: String = CommonClientConfigs.METRICS_NUM_SAMPLES_CONFIG
  val MetricReporterClassesProp: String = CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG
  val MetricRecordingLevelProp: String = CommonClientConfigs.METRICS_RECORDING_LEVEL_CONFIG


  val FetchPurgatoryPurgeIntervalRequestsProp = "fetch.purgatory.purge.interval.requests"

  val ConnectionsMaxIdleMsProp = "connections.max.idle.ms"

  val NumNetworkThreadsProp = "num.network.threads"

  val BackgroundThreadsProp = "background.threads"

  val SocketSendBufferBytesProp = "socket.send.buffer.bytes"
  val SocketReceiveBufferBytesProp = "socket.receive.buffer.bytes"
  val SocketRequestMaxBytesProp = "socket.request.max.bytes"

  val SaslEnabledMechanismsProp = SaslConfigs.SASL_ENABLED_MECHANISMS

  val BlockchainSyncIntervalProp = "blockchain.time.interval.ms"

  val ListenersProp = "listeners"

  val ListenerSecurityProtocolMapProp = "listener.security.protocol.map"

  val QueuedMaxRequestsProp = "queued.max.requests"

  val NumIoThreadsProp = "num.io.threads"

  val AutoCreateTopicsEnableProp = "auto.create.topics.enable"


  val InterBrokerProtocolVersionProp = "inter.broker.protocol.version"
  /** ********* Log Configuration ***********/
  val NumPartitionsProp = "num.partitions"
  val LogDirsProp = "log.dirs"
  val LogDirProp = "log.dir"
  val LogSegmentBytesProp = "log.segment.bytes"
  val LogRollTimeJitterMillisProp = "log.roll.jitter.ms"
  val LogRollTimeJitterHoursProp = "log.roll.jitter.hours"
  val LogPreAllocateProp = "log.preallocate"
  val LogRollTimeMillisProp = "log.roll.ms"
  val LogRollTimeHoursProp = "log.roll.hours"
  val LogIndexIntervalBytesProp = "log.index.interval.bytes"
  val LogRetentionTimeMillisProp = "log.retention.ms"
  val LogRetentionBytesProp = "log.retention.bytes"

  val LogMessageFormatVersionProp = LogConfigPrefix + "message.format.version"
  val LogMessageTimestampTypeProp = LogConfigPrefix + "message.timestamp.type"
  val LogMessageTimestampDifferenceMaxMsProp = LogConfigPrefix + "message.timestamp.difference.max.ms"

  val LogFlushIntervalMessagesProp = "log.flush.interval.messages"
  val LogFlushIntervalMsProp = "log.flush.interval.ms"
  val LogIndexSizeMaxBytesProp = "log.index.size.max.bytes"
  val LogDeleteDelayMsProp = "log.segment.delete.delay.ms"

  val LogCleanerMinCompactionLagMsProp = "log.cleaner.min.compaction.lag.ms"
  val LogCleanerDeleteRetentionMsProp = "log.cleaner.delete.retention.ms"
  val LogCleanerMinCleanRatioProp = "log.cleaner.min.cleanable.ratio"
  val LogCleanupPolicyProp = "log.cleanup.policy"

  val MessageMaxBytesProp = "message.max.bytes"

  val NumRecoveryThreadsPerDataDirProp = "num.recovery.threads.per.data.dir"
  val LogFlushSchedulerIntervalMsProp = "log.flush.scheduler.interval.ms"
  val LogFlushOffsetCheckpointIntervalMsProp = "log.flush.offset.checkpoint.interval.ms"
  val LogFlushStartOffsetCheckpointIntervalMsProp = "log.flush.start.offset.checkpoint.interval.ms"
  val LogCleanupIntervalMsProp = "log.retention.check.interval.ms"

  val AutoCreateTopicsEnableDoc = "Enable auto creation of topic on the server"

  /** ********* Transaction Configuration **********/
  val TransactionalIdExpirationMsProp = "transactional.id.expiration.ms"

  val DeleteTopicEnableProp = "delete.topic.enable"
  val CompressionTypeProp = "compression.type"

  /** ********* Socket Server Configuration ***********/
  val PortProp = "port"
  val HostNameProp = "host.name"


  /** ********* Quota Configuration ***********/
  val ProducerQuotaBytesPerSecondDefaultProp = "quota.producer.default"
  val ConsumerQuotaBytesPerSecondDefaultProp = "quota.consumer.default"
  val NumQuotaSamplesProp = "quota.window.num"
  val NumReplicationQuotaSamplesProp = "replication.quota.window.num"
  val QuotaWindowSizeSecondsProp = "quota.window.size.seconds"
  val ReplicationQuotaWindowSizeSecondsProp = "replication.quota.window.size.seconds"

  /** ********* Offset management configuration ***********/
  val OffsetMetadataMaxSizeProp = "offset.metadata.max.bytes"
  val OffsetsLoadBufferSizeProp = "offsets.load.buffer.size"
  val OffsetsTopicReplicationFactorProp = "offsets.topic.replication.factor"
  val OffsetsTopicPartitionsProp = "offsets.topic.num.partitions"
  val OffsetsTopicSegmentBytesProp = "offsets.topic.segment.bytes"
  val OffsetsTopicCompressionCodecProp = "offsets.topic.compression.codec"
  val OffsetsRetentionMinutesProp = "offsets.retention.minutes"
  val OffsetsRetentionCheckIntervalMsProp = "offsets.retention.check.interval.ms"
  val OffsetCommitTimeoutMsProp = "offsets.commit.timeout.ms"
  val OffsetCommitRequiredAcksProp = "offsets.commit.required.acks"

  /************* Authorizer Configuration ***********/
  val AuthorizerClassNameProp = "authorizer.class.name"
  /** ********* Replication configuration ***********/
  val ControllerSocketTimeoutMsProp = "controller.socket.timeout.ms"
  val DefaultReplicationFactorProp = "default.replication.factor"


  /** ********* General Configuration ***********/
  val BrokerIdDoc = "The broker id for this server. If unset, a unique broker id will be generated." +
    "To avoid conflicts between zookeeper generated broker id's and user configured broker id's, generated broker ids " +
    "start from "

  val MaxConnectionsPerIpDoc = "The maximum number of connections we allow from each ip address"
  val MaxConnectionsPerIpOverridesDoc = "Per-ip or hostname overrides to the default maximum number of connections"
  val ConnectionsMaxIdleMsDoc = "Idle connections timeout: the server socket processor threads close the connections that idle more than this"
  val QueuedMaxRequestsDoc = "The number of queued requests allowed before blocking the network threads"

  val NumNetworkThreadsDoc = "the number of network threads that the server uses for handling network requests"

  val SocketSendBufferBytesDoc = "The SO_SNDBUF buffer of the socket sever sockets. If the value is -1, the OS default will be used."
  val SocketReceiveBufferBytesDoc = "The SO_RCVBUF buffer of the socket sever sockets. If the value is -1, the OS default will be used."
  val SocketRequestMaxBytesDoc = "The maximum number of bytes in a socket request"

  val BackgroundThreadsDoc = "The number of threads to use for various background processing tasks"
  val ListenersDoc = "Listener List - Comma-separated list of URIs we will listen on and the listener names." +
    s" If the listener name is not a security protocol, $ListenerSecurityProtocolMapProp must also be set.\n" +
    " Specify hostname as 0.0.0.0 to bind to all interfaces.\n" +
    " Leave hostname empty to bind to default interface.\n" +
    " Examples of legal listener lists:\n" +
    " PLAINTEXT://myhost:9092,SSL://:9091\n" +
    " CLIENT://0.0.0.0:9092,REPLICATION://localhost:9093\n"

  val NumIoThreadsDoc = "The number of io threads that the server uses for carrying out network requests"
  val LogFlushSchedulerIntervalMsDoc = "The frequency in ms that the log flusher checks whether any log needs to be flushed to disk"
  val LogFlushStartOffsetCheckpointIntervalMsDoc = "The frequency with which we update the persistent record of log start offset"

  /** ********* Kafka Metrics Configuration ***********/
  val MetricSampleWindowMsDoc = CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_DOC
  val MetricNumSamplesDoc = CommonClientConfigs.METRICS_NUM_SAMPLES_DOC
  val MetricReporterClassesDoc = CommonClientConfigs.METRIC_REPORTER_CLASSES_DOC
  val MetricRecordingLevelDoc = CommonClientConfigs.METRICS_RECORDING_LEVEL_DOC

  val PortDoc = "DEPRECATED: only used when `listeners` is not set. " +
    "Use `listeners` instead. \n" +
    "the port to listen and accept connections on"
  val HostNameDoc = "DEPRECATED: only used when `listeners` is not set. " +
    "Use `listeners` instead. \n" +
    "hostname of broker. If this is set, it will only bind to this address. If this is not set, it will bind to all interfaces"

  /************* Authorizer Configuration ***********/
  val AuthorizerClassNameDoc = "The authorizer class that should be used for authorization"

  val FetchPurgatoryPurgeIntervalRequestsDoc = "The purge interval (in number of requests) of the fetch request purgatory"

  val BlockchainSyncIntervalDoc = "sync block chain info interval in ms"
  val ListenerSecurityProtocolMapDoc = "Map between listener names and security protocols. This must be defined for " +
    "the same security protocol to be usable in more than one port or IP. For example, we can separate internal and " +
    "external traffic even if SSL is required for both. Concretely, we could define listeners with names INTERNAL " +
    "and EXTERNAL and this property as: `INTERNAL:SSL,EXTERNAL:SSL`. As shown, key and value are separated by a colon " +
    "and map entries are separated by commas. Each listener name should only appear once in the map."
  /** ********* Log Configuration ***********/
  val NumPartitionsDoc = "The default number of log partitions per topic"
  val LogDirDoc = "The directory in which the log data is kept (supplemental for " + LogDirsProp + " property)"
  val LogDirsDoc = "The directories in which the log data is kept. If not set, the value in " + LogDirProp + " is used"
  val LogFlushOffsetCheckpointIntervalMsDoc = "The frequency with which we update the persistent record of the last flush which acts as the log recovery point"
  val LogCleanupIntervalMsDoc = "The frequency in milliseconds that the log cleaner checks whether any log is eligible for deletion"

  val LogRollTimeJitterMillisDoc = "The maximum jitter to subtract from logRollTimeMillis (in milliseconds). If not set, the value in " + LogRollTimeJitterHoursProp + " is used"

  val LogMessageFormatVersionDoc = "Specify the message format version the broker will use to append messages to the logs. The value should be a valid ApiVersion. " +
    "Some examples are: 0.8.2, 0.9.0.0, 0.10.0, check ApiVersion for more details. By setting a particular message format version, the " +
    "user is certifying that all the existing messages on disk are smaller or equal than the specified version. Setting this value incorrectly " +
    "will cause consumers with older versions to break as they will receive messages with a format that they don't understand."

  val NumRecoveryThreadsPerDataDirDoc = "The number of threads per data directory to be used for log recovery at startup and flushing at shutdown"

  val LogSegmentBytesDoc = "The maximum size of a single log file"
  val LogRollTimeMillisDoc = "The maximum time before a new log segment is rolled out (in milliseconds). If not set, the value in " + LogRollTimeHoursProp + " is used"

  val LogMessageTimestampTypeDoc = "Define whether the timestamp in the message is message create time or log append time. The value should be either " +
    "`CreateTime` or `LogAppendTime`"

  /** ********* Transaction Configuration ***********/
  val TransactionIdExpirationMsDoc = "The maximum time of inactivity before a transactional id is expired by the " +
    "transaction coordinator. Note that this also influences PID expiration: PIDs are guaranteed to expire " +
    "after expiration of this timeout from the last write by the PID (they may expire sooner if the last write " +
    "from the PID is deleted due to the topic's retention settings)."
  val LogRollTimeHoursDoc = "The maximum time before a new log segment is rolled out (in hours), secondary to " + LogRollTimeMillisProp + " property"

  val InterBrokerProtocolVersionDoc = "Specify which version of the inter-broker protocol will be used.\n" +
    " This is typically bumped after all brokers were upgraded to a new version.\n" +
    " Example of some valid values are: 0.8.0, 0.8.1, 0.8.1.1, 0.8.2, 0.8.2.0, 0.8.2.1, 0.9.0.0, 0.9.0.1 Check ApiVersion for the full list."

  val ControllerSocketTimeoutMsDoc = "The socket timeout for controller-to-broker channels"
  val DefaultReplicationFactorDoc = "default replication factors for automatically created topics"

  /** ********* Offset management configuration ***********/
  val OffsetMetadataMaxSizeDoc = "The maximum size for a metadata entry associated with an offset commit"
  val OffsetsLoadBufferSizeDoc = "Batch size for reading from the offsets segments when loading offsets into the cache."
  val OffsetsTopicReplicationFactorDoc = "The replication factor for the offsets topic (set higher to ensure availability). " +
    "Internal topic creation will fail until the cluster size meets this replication factor requirement."
  val OffsetsTopicPartitionsDoc = "The number of partitions for the offset commit topic (should not change after deployment)"
  val OffsetsTopicSegmentBytesDoc = "The offsets topic segment bytes should be kept relatively small in order to facilitate faster log compaction and cache loads"
  val OffsetsTopicCompressionCodecDoc = "Compression codec for the offsets topic - compression may be used to achieve \"atomic\" commits"
  val OffsetsRetentionMinutesDoc = "Log retention window in minutes for offsets topic"
  val OffsetsRetentionCheckIntervalMsDoc = "Frequency at which to check for stale offsets"
  val OffsetCommitTimeoutMsDoc = "Offset commit will be delayed until all replicas for the offsets topic receive the commit " +
    "or this timeout is reached. This is similar to the producer request timeout."
  val OffsetCommitRequiredAcksDoc = "The required acks before the commit can be accepted. In general, the default (-1) should not be overridden"


  /** ********* Sasl Configuration ****************/
  val SaslMechanismInterBrokerProtocolDoc = "SASL mechanism used for inter-broker communication. Default is GSSAPI."
  val SaslEnabledMechanismsDoc = SaslConfigs.SASL_ENABLED_MECHANISMS_DOC
  val SaslKerberosServiceNameDoc = SaslConfigs.SASL_KERBEROS_SERVICE_NAME_DOC
  val SaslKerberosKinitCmdDoc = SaslConfigs.SASL_KERBEROS_KINIT_CMD_DOC
  val SaslKerberosTicketRenewWindowFactorDoc = SaslConfigs.SASL_KERBEROS_TICKET_RENEW_WINDOW_FACTOR_DOC
  val SaslKerberosTicketRenewJitterDoc = SaslConfigs.SASL_KERBEROS_TICKET_RENEW_JITTER_DOC
  val SaslKerberosMinTimeBeforeReloginDoc = SaslConfigs.SASL_KERBEROS_MIN_TIME_BEFORE_RELOGIN_DOC
  val SaslKerberosPrincipalToLocalRulesDoc = SaslConfigs.SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES_DOC
  /** ********* Quota Configuration ***********/
  val ProducerQuotaBytesPerSecondDefaultDoc = "DEPRECATED: Used only when dynamic default quotas are not configured for <user>, <client-id> or <user, client-id> in Zookeeper. " +
    "Any producer distinguished by clientId will get throttled if it produces more bytes than this value per-second"
  val ConsumerQuotaBytesPerSecondDefaultDoc = "DEPRECATED: Used only when dynamic default quotas are not configured for <user, <client-id> or <user, client-id> in Zookeeper. " +
    "Any consumer distinguished by clientId/consumer group will get throttled if it fetches more bytes than this value per-second"
  val NumQuotaSamplesDoc = "The number of samples to retain in memory for client quotas"
  val NumReplicationQuotaSamplesDoc = "The number of samples to retain in memory for replication quotas"
  val QuotaWindowSizeSecondsDoc = "The time span of each sample for client quotas"
  val ReplicationQuotaWindowSizeSecondsDoc = "The time span of each sample for replication quotas"

  private val configDef = {
    import ConfigDef.Importance._
    import ConfigDef.Range._
    import ConfigDef.Type._
    import ConfigDef.ValidString._

    new ConfigDef()
      .define(BrokerIdProp, INT, Defaults.BrokerId, HIGH, BrokerIdDoc)
      .define(MaxConnectionsPerIpProp, INT, Defaults.MaxConnectionsPerIp, atLeast(1), MEDIUM, MaxConnectionsPerIpDoc)
      .define(MaxConnectionsPerIpOverridesProp, STRING, Defaults.MaxConnectionsPerIpOverrides, MEDIUM, MaxConnectionsPerIpOverridesDoc)
      .define(ConnectionsMaxIdleMsProp, LONG, Defaults.ConnectionsMaxIdleMs, MEDIUM, ConnectionsMaxIdleMsDoc)
      .define(BackgroundThreadsProp, INT, Defaults.BackgroundThreads, atLeast(1), HIGH, BackgroundThreadsDoc)
      .define(QueuedMaxRequestsProp, INT, Defaults.QueuedMaxRequests, atLeast(1), HIGH, QueuedMaxRequestsDoc)
      .define(NumNetworkThreadsProp, INT, Defaults.NumNetworkThreads, atLeast(1), HIGH, NumNetworkThreadsDoc)
      .define(MetricNumSamplesProp, INT, Defaults.MetricNumSamples, atLeast(1), LOW, MetricNumSamplesDoc)
      .define(MetricSampleWindowMsProp, LONG, Defaults.MetricSampleWindowMs, atLeast(1), LOW, MetricSampleWindowMsDoc)
      .define(MetricReporterClassesProp, LIST, Defaults.MetricReporterClasses, LOW, MetricReporterClassesDoc)
      .define(MetricRecordingLevelProp, STRING, Defaults.MetricRecordingLevel, LOW, MetricRecordingLevelDoc)

      .define(PortProp, INT, Defaults.Port, HIGH, PortDoc)
      .define(HostNameProp, STRING, Defaults.HostName, HIGH, HostNameDoc)
      .define(SocketSendBufferBytesProp, INT, Defaults.SocketSendBufferBytes, HIGH, SocketSendBufferBytesDoc)
      .define(SocketReceiveBufferBytesProp, INT, Defaults.SocketReceiveBufferBytes, HIGH, SocketReceiveBufferBytesDoc)
      .define(SocketRequestMaxBytesProp, INT, Defaults.SocketRequestMaxBytes, atLeast(1), HIGH, SocketRequestMaxBytesDoc)
      .define(BlockchainSyncIntervalProp, LONG, Defaults.BlockchainSyncIntervalMs, MEDIUM, BlockchainSyncIntervalDoc)

      .define(ListenerSecurityProtocolMapProp, STRING, Defaults.ListenerSecurityProtocolMap, LOW, ListenerSecurityProtocolMapDoc)
      .define(ListenersProp, STRING, null, HIGH, ListenersDoc)

      .define(SaslEnabledMechanismsProp, LIST, Defaults.SaslEnabledMechanisms, MEDIUM, SaslEnabledMechanismsDoc)

      .define(NumIoThreadsProp, INT, Defaults.NumIoThreads, atLeast(1), HIGH, NumIoThreadsDoc)
      .define(FetchPurgatoryPurgeIntervalRequestsProp, INT, Defaults.FetchPurgatoryPurgeIntervalRequests, MEDIUM, FetchPurgatoryPurgeIntervalRequestsDoc)

      /************* Authorizer Configuration ***********/
      .define(AuthorizerClassNameProp, STRING, Defaults.AuthorizerClassName, LOW, AuthorizerClassNameDoc)

      .define(AutoCreateTopicsEnableProp, BOOLEAN, Defaults.AutoCreateTopicsEnable, HIGH, AutoCreateTopicsEnableDoc)
      /** ********* Log Configuration ***********/
      .define(NumPartitionsProp, INT, Defaults.NumPartitions, atLeast(1), MEDIUM, NumPartitionsDoc)
      .define(LogDirProp, STRING, Defaults.LogDir, HIGH, LogDirDoc)
      .define(LogDirsProp, STRING, null, HIGH, LogDirsDoc)
      .define(LogFlushSchedulerIntervalMsProp, LONG, Defaults.LogFlushSchedulerIntervalMs, HIGH, LogFlushSchedulerIntervalMsDoc)
      .define(LogFlushStartOffsetCheckpointIntervalMsProp, INT, Defaults.LogFlushStartOffsetCheckpointIntervalMs, atLeast(0), HIGH, LogFlushStartOffsetCheckpointIntervalMsDoc)
      .define(LogFlushOffsetCheckpointIntervalMsProp, INT, Defaults.LogFlushOffsetCheckpointIntervalMs, atLeast(0), HIGH, LogFlushOffsetCheckpointIntervalMsDoc)

      .define(LogCleanupIntervalMsProp, LONG, Defaults.LogCleanupIntervalMs, atLeast(1), MEDIUM, LogCleanupIntervalMsDoc)

      .define(TransactionalIdExpirationMsProp, INT, Defaults.TransactionalIdExpirationMsDefault, atLeast(1), LOW, TransactionIdExpirationMsDoc)

      .define(NumRecoveryThreadsPerDataDirProp, INT, Defaults.NumRecoveryThreadsPerDataDir, atLeast(1), HIGH, NumRecoveryThreadsPerDataDirDoc)
      .define(LogSegmentBytesProp, INT, Defaults.LogSegmentBytes, atLeast(Message.MinMessageOverhead), HIGH, LogSegmentBytesDoc)

      .define(LogRollTimeMillisProp, LONG, null, HIGH, LogRollTimeMillisDoc)

      .define(LogRollTimeHoursProp, INT, Defaults.LogRollHours, atLeast(1), HIGH, LogRollTimeHoursDoc)
      .define(LogRollTimeJitterMillisProp, LONG, null, HIGH, LogRollTimeJitterMillisDoc)

      .define(InterBrokerProtocolVersionProp, STRING, Defaults.InterBrokerProtocolVersion, MEDIUM, InterBrokerProtocolVersionDoc)

      /** ********* Quota configuration ***********/
      .define(ProducerQuotaBytesPerSecondDefaultProp, LONG, Defaults.ProducerQuotaBytesPerSecondDefault, atLeast(1), HIGH, ProducerQuotaBytesPerSecondDefaultDoc)
      .define(ConsumerQuotaBytesPerSecondDefaultProp, LONG, Defaults.ConsumerQuotaBytesPerSecondDefault, atLeast(1), HIGH, ConsumerQuotaBytesPerSecondDefaultDoc)
      .define(NumQuotaSamplesProp, INT, Defaults.NumQuotaSamples, atLeast(1), LOW, NumQuotaSamplesDoc)
      .define(NumReplicationQuotaSamplesProp, INT, Defaults.NumReplicationQuotaSamples, atLeast(1), LOW, NumReplicationQuotaSamplesDoc)
      .define(QuotaWindowSizeSecondsProp, INT, Defaults.QuotaWindowSizeSeconds, atLeast(1), LOW, QuotaWindowSizeSecondsDoc)
      .define(ReplicationQuotaWindowSizeSecondsProp, INT, Defaults.ReplicationQuotaWindowSizeSeconds, atLeast(1), LOW, ReplicationQuotaWindowSizeSecondsDoc)


      .define(ControllerSocketTimeoutMsProp, INT, Defaults.ControllerSocketTimeoutMs, MEDIUM, ControllerSocketTimeoutMsDoc)
      .define(DefaultReplicationFactorProp, INT, Defaults.DefaultReplicationFactor, MEDIUM, DefaultReplicationFactorDoc)

      /** ********* Offset management configuration ***********/
      .define(OffsetMetadataMaxSizeProp, INT, Defaults.OffsetMetadataMaxSize, HIGH, OffsetMetadataMaxSizeDoc)
      .define(OffsetsLoadBufferSizeProp, INT, Defaults.OffsetsLoadBufferSize, atLeast(1), HIGH, OffsetsLoadBufferSizeDoc)
      .define(OffsetsTopicReplicationFactorProp, SHORT, Defaults.OffsetsTopicReplicationFactor, atLeast(1), HIGH, OffsetsTopicReplicationFactorDoc)
      .define(OffsetsTopicPartitionsProp, INT, Defaults.OffsetsTopicPartitions, atLeast(1), HIGH, OffsetsTopicPartitionsDoc)
      .define(OffsetsTopicSegmentBytesProp, INT, Defaults.OffsetsTopicSegmentBytes, atLeast(1), HIGH, OffsetsTopicSegmentBytesDoc)
      .define(OffsetsTopicCompressionCodecProp, INT, Defaults.OffsetsTopicCompressionCodec, HIGH, OffsetsTopicCompressionCodecDoc)
      .define(OffsetsRetentionMinutesProp, INT, Defaults.OffsetsRetentionMinutes, atLeast(1), HIGH, OffsetsRetentionMinutesDoc)
      .define(OffsetsRetentionCheckIntervalMsProp, LONG, Defaults.OffsetsRetentionCheckIntervalMs, atLeast(1), HIGH, OffsetsRetentionCheckIntervalMsDoc)
      .define(OffsetCommitTimeoutMsProp, INT, Defaults.OffsetCommitTimeoutMs, atLeast(1), HIGH, OffsetCommitTimeoutMsDoc)
      .define(OffsetCommitRequiredAcksProp, SHORT, Defaults.OffsetCommitRequiredAcks, HIGH, OffsetCommitRequiredAcksDoc)
  }

  def fromProps(props: Properties): BelinkConfig =
    fromProps(props, true)

  def fromProps(props: Properties, doLog: Boolean): BelinkConfig =
    new BelinkConfig(props, doLog)

  def fromProps(defaults: Properties, overrides: Properties): BelinkConfig =
    fromProps(defaults, overrides, true)

  def fromProps(defaults: Properties, overrides: Properties, doLog: Boolean): BelinkConfig = {
    val props = new Properties()
    props.putAll(defaults)
    props.putAll(overrides)
    fromProps(props, doLog)
  }

}

class BelinkConfig(val props: java.util.Map[_, _], doLog: Boolean) extends AbstractConfig(BelinkConfig.configDef, props, doLog) {

  val maxConnectionsPerIp = getInt(BelinkConfig.MaxConnectionsPerIpProp)
  val maxConnectionsPerIpOverrides: Map[String, Int] =
    getMap(BelinkConfig.MaxConnectionsPerIpOverridesProp, getString(BelinkConfig.MaxConnectionsPerIpOverridesProp)).map { case (k, v) => (k, v.toInt)}
  val connectionsMaxIdleMs = getLong(BelinkConfig.ConnectionsMaxIdleMsProp)
  val queuedMaxRequests = getInt(BelinkConfig.QueuedMaxRequestsProp)
  val numNetworkThreads = getInt(BelinkConfig.NumNetworkThreadsProp)

  var brokerId: Int = getInt(BelinkConfig.BrokerIdProp)


  /************* Authorizer Configuration ***********/
  val authorizerClassName: String = getString(BelinkConfig.AuthorizerClassNameProp)

  /** ********* Metric Configuration **************/
  val metricNumSamples = getInt(BelinkConfig.MetricNumSamplesProp)
  val metricSampleWindowMs = getLong(BelinkConfig.MetricSampleWindowMsProp)
  val metricRecordingLevel = getString(BelinkConfig.MetricRecordingLevelProp)

  /** ********* Socket Server Configuration ***********/
  val hostName = getString(BelinkConfig.HostNameProp)
  val port = getInt(BelinkConfig.PortProp)

  val socketSendBufferBytes = getInt(BelinkConfig.SocketSendBufferBytesProp)
  val socketReceiveBufferBytes = getInt(BelinkConfig.SocketReceiveBufferBytesProp)

  val socketRequestMaxBytes = getInt(BelinkConfig.SocketRequestMaxBytesProp)

  val backgroundThreads = getInt(BelinkConfig.BackgroundThreadsProp)

  val numIoThreads = getInt(BelinkConfig.NumIoThreadsProp)

  /** ********* Log Configuration ***********/
  val autoCreateTopicsEnable = getBoolean(BelinkConfig.AutoCreateTopicsEnableProp)
  val numPartitions = getInt(BelinkConfig.NumPartitionsProp)
  val logDirs = CoreUtils.parseCsvList(Option(getString(BelinkConfig.LogDirsProp)).getOrElse(getString(BelinkConfig.LogDirProp)))

  val logFlushSchedulerIntervalMs = getLong(BelinkConfig.LogFlushSchedulerIntervalMsProp)
  val logFlushOffsetCheckpointIntervalMs = getInt(BelinkConfig.LogFlushOffsetCheckpointIntervalMsProp).toLong
  val logFlushStartOffsetCheckpointIntervalMs = getInt(BelinkConfig.LogFlushStartOffsetCheckpointIntervalMsProp).toLong
  val logCleanupIntervalMs = getLong(BelinkConfig.LogCleanupIntervalMsProp)
  val transactionIdExpirationMs = getInt(BelinkConfig.TransactionalIdExpirationMsProp)
  val numRecoveryThreadsPerDataDir = getInt(BelinkConfig.NumRecoveryThreadsPerDataDirProp)

  val logSegmentBytes = getInt(BelinkConfig.LogSegmentBytesProp)
  val logRollTimeMillis: java.lang.Long = Option(getLong(BelinkConfig.LogRollTimeMillisProp)).getOrElse(60 * 60 * 1000L * getInt(BelinkConfig.LogRollTimeHoursProp))

  val interBrokerProtocolVersionString = getString(BelinkConfig.InterBrokerProtocolVersionProp)
  val interBrokerProtocolVersion = ApiVersion(interBrokerProtocolVersionString)

  /** ********* Quota Configuration **************/
  val producerQuotaBytesPerSecondDefault = getLong(BelinkConfig.ProducerQuotaBytesPerSecondDefaultProp)
  val consumerQuotaBytesPerSecondDefault = getLong(BelinkConfig.ConsumerQuotaBytesPerSecondDefaultProp)
  val numQuotaSamples = getInt(BelinkConfig.NumQuotaSamplesProp)
  val quotaWindowSizeSeconds = getInt(BelinkConfig.QuotaWindowSizeSecondsProp)
  val numReplicationQuotaSamples = getInt(BelinkConfig.NumReplicationQuotaSamplesProp)
  val replicationQuotaWindowSizeSeconds = getInt(BelinkConfig.ReplicationQuotaWindowSizeSecondsProp)
  
  /** ***** block chain configuration ****** **/
  val blockchainSyncInterval = getLong(BelinkConfig.BlockchainSyncIntervalProp)

  /** ********* Replication configuration ***********/
  val controllerSocketTimeoutMs: Int = getInt(BelinkConfig.ControllerSocketTimeoutMsProp)
  val defaultReplicationFactor: Int = getInt(BelinkConfig.DefaultReplicationFactorProp)

  val listeners: Seq[EndPoint] = getListeners

  val saslEnabledMechanisms = getList(BelinkConfig.SaslEnabledMechanismsProp)

  val fetchPurgatoryPurgeIntervalRequests = getInt(BelinkConfig.FetchPurgatoryPurgeIntervalRequestsProp)

  /** ********* Offset management configuration ***********/
  val offsetMetadataMaxSize = getInt(BelinkConfig.OffsetMetadataMaxSizeProp)
  val offsetsLoadBufferSize = getInt(BelinkConfig.OffsetsLoadBufferSizeProp)
  val offsetsTopicReplicationFactor = getShort(BelinkConfig.OffsetsTopicReplicationFactorProp)
  val offsetsTopicPartitions = getInt(BelinkConfig.OffsetsTopicPartitionsProp)
  val offsetCommitTimeoutMs = getInt(BelinkConfig.OffsetCommitTimeoutMsProp)
  val offsetCommitRequiredAcks = getShort(BelinkConfig.OffsetCommitRequiredAcksProp)
  val offsetsTopicSegmentBytes = getInt(BelinkConfig.OffsetsTopicSegmentBytesProp)
  val offsetsTopicCompressionCodec = Option(getInt(BelinkConfig.OffsetsTopicCompressionCodecProp)).map(value => CompressionCodec.getCompressionCodec(value)).orNull

  private[belink] lazy val listenerSecurityProtocolMap = getListenerSecurityProtocolMap

  private def getListenerSecurityProtocolMap: Map[ListenerName, SecurityProtocol] = {
    getMap(BelinkConfig.ListenerSecurityProtocolMapProp, getString(BelinkConfig.ListenerSecurityProtocolMapProp))
      .map { case (listenerName, protocolName) =>
        ListenerName.normalised(listenerName) -> getSecurityProtocol(protocolName, BelinkConfig.ListenerSecurityProtocolMapProp)
      }
  }

  private def getListeners: Seq[EndPoint] = {
    Option(getString(BelinkConfig.ListenersProp)).map { listenerProp =>
      CoreUtils.listenerListToEndPoints(listenerProp, listenerSecurityProtocolMap)
    }.getOrElse(CoreUtils.listenerListToEndPoints("PLAINTEXT://" + hostName + ":" + port, listenerSecurityProtocolMap))
  }

  private def getSecurityProtocol(protocolName: String, configName: String): SecurityProtocol = {
    try SecurityProtocol.forName(protocolName)
    catch {
      case e: IllegalArgumentException =>
        throw new ConfigException(s"Invalid security protocol `$protocolName` defined in $configName")
    }
  }

  private def getMap(propName: String, propValue: String): Map[String, String] = {
    try {
      CoreUtils.parseCsvMap(propValue)
    } catch {
      case e: Exception => throw new IllegalArgumentException("Error parsing configuration property '%s': %s".format(propName, e.getMessage))
    }
  }
}