/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package belink.log

import java.util.{Collections, Locale, Properties}

import belink.api.ApiVersion
import belink.message.{BrokerCompressionCodec, Message}

import scala.collection.JavaConverters._
import belink.server.BelinkConfig
import com.ynet.belink.common.errors.InvalidConfigurationException
import com.ynet.belink.common.config.{AbstractConfig, ConfigDef}
import com.ynet.belink.common.record.TimestampType
import com.ynet.belink.common.utils.Utils

import scala.collection.mutable
import com.ynet.belink.common.config.ConfigDef.{ConfigKey, ValidList, Validator}

object Defaults {
  val SegmentSize = belink.server.Defaults.LogSegmentBytes
  val SegmentMs = belink.server.Defaults.LogRollHours * 60 * 60 * 1000L
  val SegmentJitterMs = belink.server.Defaults.LogRollJitterHours * 60 * 60 * 1000L
  val FlushInterval = belink.server.Defaults.LogFlushIntervalMessages
  val FlushMs = belink.server.Defaults.LogFlushSchedulerIntervalMs
  val RetentionSize = belink.server.Defaults.LogRetentionBytes
  val RetentionMs = belink.server.Defaults.LogRetentionHours * 60 * 60 * 1000L
  val MaxMessageSize = belink.server.Defaults.MessageMaxBytes
  val MaxIndexSize = belink.server.Defaults.LogIndexSizeMaxBytes
  val IndexInterval = belink.server.Defaults.LogIndexIntervalBytes
  val FileDeleteDelayMs = belink.server.Defaults.LogDeleteDelayMs
  val DeleteRetentionMs = belink.server.Defaults.LogCleanerDeleteRetentionMs
  val MinCompactionLagMs = belink.server.Defaults.LogCleanerMinCompactionLagMs
  val MinCleanableDirtyRatio = belink.server.Defaults.LogCleanerMinCleanRatio
  val Compact = belink.server.Defaults.LogCleanupPolicy
//  val UncleanLeaderElectionEnable = belink.server.Defaults.UncleanLeaderElectionEnable
//  val MinInSyncReplicas = belink.server.Defaults.MinInSyncReplicas
  val CompressionType = belink.server.Defaults.CompressionType
  val PreAllocateEnable = belink.server.Defaults.LogPreAllocateEnable
  val MessageFormatVersion = belink.server.Defaults.LogMessageFormatVersion
  val MessageTimestampType = belink.server.Defaults.LogMessageTimestampType
  val MessageTimestampDifferenceMaxMs = belink.server.Defaults.LogMessageTimestampDifferenceMaxMs
//  val LeaderReplicationThrottledReplicas = Collections.emptyList[String]()
//  val FollowerReplicationThrottledReplicas = Collections.emptyList[String]()
//  val MaxIdMapSnapshots = belink.server.Defaults.MaxIdMapSnapshots
}

case class LogConfig(props: java.util.Map[_, _]) extends AbstractConfig(LogConfig.configDef, props, false) {
  /**
   * Important note: Any configuration parameter that is passed along from belinkConfig to LogConfig
   * should also go in [[belink.server.BelinkServer.copyBelinkConfigToLog()]].
   */
  val segmentSize = getInt(LogConfig.SegmentBytesProp)
  val segmentMs = getLong(LogConfig.SegmentMsProp)
  val segmentJitterMs = getLong(LogConfig.SegmentJitterMsProp)
  val maxIndexSize = getInt(LogConfig.SegmentIndexBytesProp)
  val flushInterval = getLong(LogConfig.FlushMessagesProp)
  val flushMs = getLong(LogConfig.FlushMsProp)
  val retentionSize = getLong(LogConfig.RetentionBytesProp)
  val retentionMs = getLong(LogConfig.RetentionMsProp)
  val maxMessageSize = getInt(LogConfig.MaxMessageBytesProp)
  val indexInterval = getInt(LogConfig.IndexIntervalBytesProp)
  val fileDeleteDelayMs = getLong(LogConfig.FileDeleteDelayMsProp)
  val deleteRetentionMs = getLong(LogConfig.DeleteRetentionMsProp)
  val compactionLagMs = getLong(LogConfig.MinCompactionLagMsProp)
  val minCleanableRatio = getDouble(LogConfig.MinCleanableDirtyRatioProp)
  val compact = getList(LogConfig.CleanupPolicyProp).asScala.map(_.toLowerCase(Locale.ROOT)).contains(LogConfig.Compact)
  val delete = getList(LogConfig.CleanupPolicyProp).asScala.map(_.toLowerCase(Locale.ROOT)).contains(LogConfig.Delete)
//  val uncleanLeaderElectionEnable = getBoolean(LogConfig.UncleanLeaderElectionEnableProp)
//  val minInSyncReplicas = getInt(LogConfig.MinInSyncReplicasProp)
  val compressionType = getString(LogConfig.CompressionTypeProp).toLowerCase(Locale.ROOT)
  val preallocate = getBoolean(LogConfig.PreAllocateEnableProp)
  val messageFormatVersion = ApiVersion(getString(LogConfig.MessageFormatVersionProp))
  val messageTimestampType = TimestampType.forName(getString(LogConfig.MessageTimestampTypeProp))
  val messageTimestampDifferenceMaxMs = getLong(LogConfig.MessageTimestampDifferenceMaxMsProp).longValue
//  val LeaderReplicationThrottledReplicas = getList(LogConfig.LeaderReplicationThrottledReplicasProp)
//  val FollowerReplicationThrottledReplicas = getList(LogConfig.FollowerReplicationThrottledReplicasProp)

  def randomSegmentJitter: Long =
    if (segmentJitterMs == 0) 0 else Utils.abs(scala.util.Random.nextInt()) % math.min(segmentJitterMs, segmentMs)
}

object LogConfig {

  def main(args: Array[String]) {
    println(configDef.toHtmlTable)
  }

  val Delete = "delete"
  val Compact = "compact"

  val SegmentBytesProp = "segment.bytes"
  val SegmentMsProp = "segment.ms"
  val SegmentJitterMsProp = "segment.jitter.ms"
  val SegmentIndexBytesProp = "segment.index.bytes"
  val FlushMessagesProp = "flush.messages"
  val FlushMsProp = "flush.ms"
  val RetentionBytesProp = "retention.bytes"
  val RetentionMsProp = "retention.ms"
  val MaxMessageBytesProp = "max.message.bytes"
  val IndexIntervalBytesProp = "index.interval.bytes"
  val DeleteRetentionMsProp = "delete.retention.ms"
  val MinCompactionLagMsProp = "min.compaction.lag.ms"
  val FileDeleteDelayMsProp = "file.delete.delay.ms"
  val MinCleanableDirtyRatioProp = "min.cleanable.dirty.ratio"
  val CleanupPolicyProp = "cleanup.policy"
  val UncleanLeaderElectionEnableProp = "unclean.leader.election.enable"
  val MinInSyncReplicasProp = "min.insync.replicas"
  val CompressionTypeProp = "compression.type"
  val PreAllocateEnableProp = "preallocate"
  val MessageFormatVersionProp = "message.format.version"
  val MessageTimestampTypeProp = "message.timestamp.type"
  val MessageTimestampDifferenceMaxMsProp = "message.timestamp.difference.max.ms"
  val LeaderReplicationThrottledReplicasProp = "leader.replication.throttled.replicas"
  val FollowerReplicationThrottledReplicasProp = "follower.replication.throttled.replicas"

  val SegmentSizeDoc = "This configuration controls the segment file size for " +
    "the log. Retention and cleaning is always done a file at a time so a larger " +
    "segment size means fewer files but less granular control over retention."
  val SegmentMsDoc = "This configuration controls the period of time after " +
    "which belink will force the log to roll even if the segment file isn't full " +
    "to ensure that retention can delete or compact old data."
  val SegmentJitterMsDoc = "The maximum random jitter subtracted from the scheduled segment roll time to avoid" +
    " thundering herds of segment rolling"
  val FlushIntervalDoc = "This setting allows specifying an interval at which we " +
    "will force an fsync of data written to the log. For example if this was set to 1 " +
    "we would fsync after every message; if it were 5 we would fsync after every five " +
    "messages. In general we recommend you not set this and use replication for " +
    "durability and allow the operating system's background flush capabilities as it " +
    "is more efficient. This setting can be overridden on a per-topic basis (see <a " +
    "href=\"#topic-config\">the per-topic configuration section</a>)."
  val FlushMsDoc = "This setting allows specifying a time interval at which we will " +
    "force an fsync of data written to the log. For example if this was set to 1000 " +
    "we would fsync after 1000 ms had passed. In general we recommend you not set " +
    "this and use replication for durability and allow the operating system's background " +
    "flush capabilities as it is more efficient."
  val RetentionSizeDoc = "This configuration controls the maximum size a log can grow " +
    "to before we will discard old log segments to free up space if we are using the " +
    "\"delete\" retention policy. By default there is no size limit only a time limit."
  val RetentionMsDoc = "This configuration controls the maximum time we will retain a " +
    "log before we will discard old log segments to free up space if we are using the " +
    "\"delete\" retention policy. This represents an SLA on how soon consumers must read " +
    "their data."
  val MaxIndexSizeDoc = "This configuration controls the size of the index that maps " +
    "offsets to file positions. We preallocate this index file and shrink it only after log " +
    "rolls. You generally should not need to change this setting."
  val MaxMessageSizeDoc = "This is largest message size belink will allow to be appended. Note that if you increase" +
    " this size you must also increase your consumer's fetch size so they can fetch messages this large."
  val IndexIntervalDoc = "This setting controls how frequently belink adds an index " +
    "entry to it's offset index. The default setting ensures that we index a message " +
    "roughly every 4096 bytes. More indexing allows reads to jump closer to the exact " +
    "position in the log but makes the index larger. You probably don't need to change " +
    "this."
  val FileDeleteDelayMsDoc = "The time to wait before deleting a file from the filesystem"
  val DeleteRetentionMsDoc = "The amount of time to retain delete tombstone markers " +
    "for <a href=\"#compaction\">log compacted</a> topics. This setting also gives a bound " +
    "on the time in which a consumer must complete a read if they begin from offset 0 " +
    "to ensure that they get a valid snapshot of the final stage (otherwise delete " +
    "tombstones may be collected before they complete their scan)."
  val MinCompactionLagMsDoc = "The minimum time a message will remain uncompacted in the log. " +
    "Only applicable for logs that are being compacted."
  val MinCleanableRatioDoc = "This configuration controls how frequently the log " +
    "compactor will attempt to clean the log (assuming <a href=\"#compaction\">log " +
    "compaction</a> is enabled). By default we will avoid cleaning a log where more than " +
    "50% of the log has been compacted. This ratio bounds the maximum space wasted in " +
    "the log by duplicates (at 50% at most 50% of the log could be duplicates). A " +
    "higher ratio will mean fewer, more efficient cleanings but will mean more wasted " +
    "space in the log."
  val CompactDoc = "A string that is either \"delete\" or \"compact\". This string " +
    "designates the retention policy to use on old log segments. The default policy " +
    "(\"delete\") will discard old segments when their retention time or size limit has " +
    "been reached. The \"compact\" setting will enable <a href=\"#compaction\">log " +
    "compaction</a> on the topic."
  val UncleanLeaderElectionEnableDoc = "Indicates whether to enable replicas not in the ISR set to be elected as" +
    " leader as a last resort, even though doing so may result in data loss"
//  val MinInSyncReplicasDoc = BelinkConfig.MinInSyncReplicasDoc
  val CompressionTypeDoc = "Specify the final compression type for a given topic. This configuration accepts the " +
    "standard compression codecs ('gzip', 'snappy', lz4). It additionally accepts 'uncompressed' which is equivalent to " +
    "no compression; and 'producer' which means retain the original compression codec set by the producer."
  val PreAllocateEnableDoc ="Should pre allocate file when create new segment?"
  val MessageFormatVersionDoc = BelinkConfig.LogMessageFormatVersionDoc
  val MessageTimestampTypeDoc = BelinkConfig.LogMessageTimestampTypeDoc
  val MessageTimestampDifferenceMaxMsDoc = "The maximum difference allowed between the timestamp when a broker receives " +
    "a message and the timestamp specified in the message. If message.timestamp.type=CreateTime, a message will be rejected " +
    "if the difference in timestamp exceeds this threshold. This configuration is ignored if message.timestamp.type=LogAppendTime."
  val LeaderReplicationThrottledReplicasDoc = "A list of replicas for which log replication should be throttled on the leader side. The list should describe a set of " +
    "replicas in the form [PartitionId]:[BrokerId],[PartitionId]:[BrokerId]:... or alternatively the wildcard '*' can be used to throttle all replicas for this topic."
  val FollowerReplicationThrottledReplicasDoc = "A list of replicas for which log replication should be throttled on the follower side. The list should describe a set of " +
    "replicas in the form [PartitionId]:[BrokerId],[PartitionId]:[BrokerId]:... or alternatively the wildcard '*' can be used to throttle all replicas for this topic."

  private class LogConfigDef extends ConfigDef {

    private final val serverDefaultConfigNames = mutable.Map[String, String]()

    def define(name: String, defType: ConfigDef.Type, defaultValue: Any, validator: Validator,
               importance: ConfigDef.Importance, doc: String, serverDefaultConfigName: String): LogConfigDef = {
      super.define(name, defType, defaultValue, validator, importance, doc)
      serverDefaultConfigNames.put(name, serverDefaultConfigName)
      this
    }

    def define(name: String, defType: ConfigDef.Type, defaultValue: Any, importance: ConfigDef.Importance,
               documentation: String, serverDefaultConfigName: String): LogConfigDef = {
      super.define(name, defType, defaultValue, importance, documentation)
      serverDefaultConfigNames.put(name, serverDefaultConfigName)
      this
    }

    def define(name: String, defType: ConfigDef.Type, importance: ConfigDef.Importance, documentation: String,
               serverDefaultConfigName: String): LogConfigDef = {
      super.define(name, defType, importance, documentation)
      serverDefaultConfigNames.put(name, serverDefaultConfigName)
      this
    }

    override def headers = List("Name", "Description", "Type", "Default", "Valid Values", "Server Default Property", "Importance").asJava

    override def getConfigValue(key: ConfigKey, headerName: String): String = {
      headerName match {
        case "Server Default Property" => serverDefaultConfigNames.get(key.name).get
        case _ => super.getConfigValue(key, headerName)
      }
    }

    def serverConfigName(configName: String): Option[String] = serverDefaultConfigNames.get(configName)
  }

  private val configDef: LogConfigDef = {
    import com.ynet.belink.common.config.ConfigDef.Importance._
    import com.ynet.belink.common.config.ConfigDef.Range._
    import com.ynet.belink.common.config.ConfigDef.Type._
    import com.ynet.belink.common.config.ConfigDef.ValidString._

    new LogConfigDef()
      .define(SegmentBytesProp, INT, Defaults.SegmentSize, atLeast(Message.MinMessageOverhead), MEDIUM,
        SegmentSizeDoc, BelinkConfig.LogSegmentBytesProp)
      .define(SegmentMsProp, LONG, Defaults.SegmentMs, atLeast(0), MEDIUM, SegmentMsDoc,
        BelinkConfig.LogRollTimeMillisProp)
      .define(SegmentJitterMsProp, LONG, Defaults.SegmentJitterMs, atLeast(0), MEDIUM, SegmentJitterMsDoc,
        BelinkConfig.LogRollTimeJitterMillisProp)
      .define(SegmentIndexBytesProp, INT, Defaults.MaxIndexSize, atLeast(0), MEDIUM, MaxIndexSizeDoc,
        BelinkConfig.LogIndexSizeMaxBytesProp)
      .define(FlushMessagesProp, LONG, Defaults.FlushInterval, atLeast(0), MEDIUM, FlushIntervalDoc,
        BelinkConfig.LogFlushIntervalMessagesProp)
      .define(FlushMsProp, LONG, Defaults.FlushMs, atLeast(0), MEDIUM, FlushMsDoc,
        BelinkConfig.LogFlushIntervalMsProp)
//      // can be negative. See belink.log.LogManager.cleanupSegmentsToMaintainSize
      .define(RetentionBytesProp, LONG, Defaults.RetentionSize, MEDIUM, RetentionSizeDoc,
        BelinkConfig.LogRetentionBytesProp)
//      // can be negative. See belink.log.LogManager.cleanupExpiredSegments
      .define(RetentionMsProp, LONG, Defaults.RetentionMs, MEDIUM, RetentionMsDoc,
        BelinkConfig.LogRetentionTimeMillisProp)
      .define(MaxMessageBytesProp, INT, Defaults.MaxMessageSize, atLeast(0), MEDIUM, MaxMessageSizeDoc,
        BelinkConfig.MessageMaxBytesProp)
      .define(IndexIntervalBytesProp, INT, Defaults.IndexInterval, atLeast(0), MEDIUM, IndexIntervalDoc,
        BelinkConfig.LogIndexIntervalBytesProp)
      .define(DeleteRetentionMsProp, LONG, Defaults.DeleteRetentionMs, atLeast(0), MEDIUM,
        DeleteRetentionMsDoc, BelinkConfig.LogCleanerDeleteRetentionMsProp)
      .define(MinCompactionLagMsProp, LONG, Defaults.MinCompactionLagMs, atLeast(0), MEDIUM, MinCompactionLagMsDoc,
        BelinkConfig.LogCleanerMinCompactionLagMsProp)
      .define(FileDeleteDelayMsProp, LONG, Defaults.FileDeleteDelayMs, atLeast(0), MEDIUM, FileDeleteDelayMsDoc,
        BelinkConfig.LogDeleteDelayMsProp)
      .define(MinCleanableDirtyRatioProp, DOUBLE, Defaults.MinCleanableDirtyRatio, between(0, 1), MEDIUM,
        MinCleanableRatioDoc, BelinkConfig.LogCleanerMinCleanRatioProp)
      .define(CleanupPolicyProp, LIST, Defaults.Compact, ValidList.in(LogConfig.Compact, LogConfig.Delete), MEDIUM, CompactDoc,
        BelinkConfig.LogCleanupPolicyProp)
//      .define(UncleanLeaderElectionEnableProp, BOOLEAN, Defaults.UncleanLeaderElectionEnable,
//        MEDIUM, UncleanLeaderElectionEnableDoc, BelinkConfig.UncleanLeaderElectionEnableProp)
//      .define(MinInSyncReplicasProp, INT, Defaults.MinInSyncReplicas, atLeast(1), MEDIUM, MinInSyncReplicasDoc,
//        BelinkConfig.MinInSyncReplicasProp)
      .define(CompressionTypeProp, STRING, Defaults.CompressionType, in(BrokerCompressionCodec.brokerCompressionOptions:_*),
        MEDIUM, CompressionTypeDoc, BelinkConfig.CompressionTypeProp)
      .define(PreAllocateEnableProp, BOOLEAN, Defaults.PreAllocateEnable, MEDIUM, PreAllocateEnableDoc,
        BelinkConfig.LogPreAllocateProp)
      .define(MessageFormatVersionProp, STRING, Defaults.MessageFormatVersion, MEDIUM, MessageFormatVersionDoc,
        BelinkConfig.LogMessageFormatVersionProp)
      .define(MessageTimestampTypeProp, STRING, Defaults.MessageTimestampType, MEDIUM, MessageTimestampTypeDoc,
        BelinkConfig.LogMessageTimestampTypeProp)
      .define(MessageTimestampDifferenceMaxMsProp, LONG, Defaults.MessageTimestampDifferenceMaxMs,
        atLeast(0), MEDIUM, MessageTimestampDifferenceMaxMsDoc, BelinkConfig.LogMessageTimestampDifferenceMaxMsProp)
//      .define(LeaderReplicationThrottledReplicasProp, LIST, Defaults.LeaderReplicationThrottledReplicas, ThrottledReplicaListValidator, MEDIUM,
//        LeaderReplicationThrottledReplicasDoc, LeaderReplicationThrottledReplicasProp)
//      .define(FollowerReplicationThrottledReplicasProp, LIST, Defaults.FollowerReplicationThrottledReplicas, ThrottledReplicaListValidator, MEDIUM,
//        FollowerReplicationThrottledReplicasDoc, FollowerReplicationThrottledReplicasProp)
  }

  def apply(): LogConfig = LogConfig(new Properties())

  def configNames: Seq[String] = configDef.names.asScala.toSeq.sorted

  def serverConfigName(configName: String): Option[String] = configDef.serverConfigName(configName)

  /**
   * Create a log config instance using the given properties and defaults
   */
  def fromProps(defaults: java.util.Map[_ <: Object, _ <: Object], overrides: Properties): LogConfig = {
    val props = new Properties()
    props.putAll(defaults)
    props.putAll(overrides)
    LogConfig(props)
  }

  /**
   * Check that property names are valid
   */
  def validateNames(props: Properties) {
    val names = configNames
    for(name <- props.asScala.keys)
      if (!names.contains(name))
        throw new InvalidConfigurationException(s"Unknown Log configuration $name.")
  }

  /**
   * Check that the given properties contain only valid log config names and that all values can be parsed and are valid
   */
  def validate(props: Properties) {
    validateNames(props)
    configDef.parse(props)
  }

}
