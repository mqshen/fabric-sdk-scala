package belink.coordinator.group

import belink.message.{CompressionCodec, NoCompressionCodec}

/**
  * Created by goldratio on 07/07/2017.
  */
case class OffsetConfig(maxMetadataSize: Int = OffsetConfig.DefaultMaxMetadataSize,
                        loadBufferSize: Int = OffsetConfig.DefaultLoadBufferSize,
                        offsetsRetentionMs: Long = OffsetConfig.DefaultOffsetRetentionMs,
                        offsetsRetentionCheckIntervalMs: Long = OffsetConfig.DefaultOffsetsRetentionCheckIntervalMs,
                        offsetsTopicNumPartitions: Int = OffsetConfig.DefaultOffsetsTopicNumPartitions,
                        offsetsTopicSegmentBytes: Int = OffsetConfig.DefaultOffsetsTopicSegmentBytes,
                        offsetsTopicReplicationFactor: Short = OffsetConfig.DefaultOffsetsTopicReplicationFactor,
                        offsetsTopicCompressionCodec: CompressionCodec = OffsetConfig.DefaultOffsetsTopicCompressionCodec,
                        offsetCommitTimeoutMs: Int = OffsetConfig.DefaultOffsetCommitTimeoutMs,
                        offsetCommitRequiredAcks: Short = OffsetConfig.DefaultOffsetCommitRequiredAcks)

object OffsetConfig {
  val DefaultMaxMetadataSize = 4096
  val DefaultLoadBufferSize = 5*1024*1024
  val DefaultOffsetRetentionMs = 24*60*60*1000L
  val DefaultOffsetsRetentionCheckIntervalMs = 600000L
  val DefaultOffsetsTopicNumPartitions = 50
  val DefaultOffsetsTopicSegmentBytes = 100*1024*1024
  val DefaultOffsetsTopicReplicationFactor = 3.toShort
  val DefaultOffsetsTopicCompressionCodec = NoCompressionCodec
  val DefaultOffsetCommitTimeoutMs = 5000
  val DefaultOffsetCommitRequiredAcks = (-1).toShort
}
