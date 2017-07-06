package belink.server

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import belink.cluster.{Partition, Replica}
import belink.common.{BelinkStorageException, Topic}
import belink.log.{LogAppendInfo, LogManager}
import belink.utils.{Exit, Logging, Pool, Scheduler}
import com.ynet.belink.common.TopicPartition
import com.ynet.belink.common.errors._
import com.ynet.belink.common.metrics.Metrics
import com.ynet.belink.common.protocol.Errors
import com.ynet.belink.common.record.{MemoryRecords, Records}
import com.ynet.belink.common.requests.FetchRequest.PartitionData
import com.ynet.belink.common.requests.IsolationLevel
import com.ynet.belink.common.requests.ProduceResponse.PartitionResponse
import com.ynet.belink.common.utils.Time

import scala.collection.{Map, Seq, mutable}

/**
  * Created by goldratio on 29/06/2017.
  */
case class LogAppendResult(info: LogAppendInfo, exception: Option[Throwable] = None) {
  def error: Errors = exception match {
    case None => Errors.NONE
    case Some(e) => Errors.forException(e)
  }
}

case class LogReadResult(info: FetchDataInfo,
                         hw: Long,
                         leaderLogStartOffset: Long,
                         leaderLogEndOffset: Long,
                         followerLogStartOffset: Long,
                         fetchTimeMs: Long,
                         readSize: Int,
                         exception: Option[Throwable] = None) {

  def error: Errors = exception match {
    case None => Errors.NONE
    case Some(e) => Errors.forException(e)
  }

  override def toString =
    s"Fetch Data: [$info], HW: [$hw], leaderLogStartOffset: [$leaderLogStartOffset], leaderLogEndOffset: [$leaderLogEndOffset], " +
      s"followerLogStartOffset: [$followerLogStartOffset], fetchTimeMs: [$fetchTimeMs], readSize: [$readSize], error: [$error]"

}


case class FetchPartitionData(error: Errors = Errors.NONE, hw: Long = -1L, logStartOffset: Long, records: Records)

class ReplicaManager(val config: BelinkConfig,
                     metrics: Metrics,
                     time: Time,
                     scheduler: Scheduler,
                     val logManager: LogManager,
                     val isShuttingDown: AtomicBoolean) extends Logging {

  val delayedFetchPurgatory = DelayedOperationPurgatory[DelayedFetch](
    purgatoryName = "Fetch", 0, config.fetchPurgatoryPurgeIntervalRequests)

  private val allPartitions = new Pool[TopicPartition, Partition](valueFactory = Some(tp =>
    new Partition(tp.topic, tp.partition, time, this)))

  def startup() {
    // start ISR expiration thread
    // A follower can lag behind leader for up to config.replicaLagTimeMaxMs x 1.5 before it is removed from ISR
    //scheduler.schedule("isr-expiration", maybeShrinkIsr, period = config.replicaLagTimeMaxMs / 2, unit = TimeUnit.MILLISECONDS)
    //scheduler.schedule("isr-change-propagation", maybePropagateIsrChanges, period = 2500L, unit = TimeUnit.MILLISECONDS)
  }

  private def isValidRequiredAcks(requiredAcks: Short): Boolean = {
    requiredAcks == -1 || requiredAcks == 1 || requiredAcks == 0
  }

  private def appendToLocalLog(internalTopicsAllowed: Boolean,
                               entriesPerPartition: Map[TopicPartition, MemoryRecords],
                               requiredAcks: Short): Map[TopicPartition, LogAppendResult] = {
    trace("Append [%s] to local log ".format(entriesPerPartition))
    entriesPerPartition.map { case (topicPartition, records) =>
      if (Topic.isInternal(topicPartition.topic) && !internalTopicsAllowed) {
        (topicPartition, LogAppendResult(
          LogAppendInfo.UnknownLogAppendInfo,
          Some(new InvalidTopicException(s"Cannot append to internal topic ${topicPartition.topic}"))))
      } else {
        try {
          val partition = getOrCreatePartition(topicPartition)
          val info = partition.appendRecordsToLeader(records, requiredAcks)

          val numAppendedMessages =
            if (info.firstOffset == -1L || info.lastOffset == -1L)
              0
            else
              info.lastOffset - info.firstOffset + 1

          trace("%d bytes written to log %s-%d beginning at offset %d and ending at offset %d"
            .format(records.sizeInBytes, topicPartition.topic, topicPartition.partition, info.firstOffset, info.lastOffset))
          (topicPartition, LogAppendResult(info))
        } catch {
          // NOTE: Failed produce requests metric is not incremented for known exceptions
          // it is supposed to indicate un-expected failures of a broker in handling a produce request
          case e: BelinkStorageException =>
            fatal("Halting due to unrecoverable I/O error while handling produce request: ", e)
            Exit.halt(1)
            (topicPartition, null)
          case e@ (_: UnknownTopicOrPartitionException |
                   _: NotLeaderForPartitionException |
                   _: RecordTooLargeException |
                   _: RecordBatchTooLargeException |
                   _: CorruptRecordException |
                   _: InvalidTimestampException) =>
            e.printStackTrace()
            (topicPartition, LogAppendResult(LogAppendInfo.UnknownLogAppendInfo, Some(e)))
          case t: Throwable =>
            error("Error processing append operation on partition %s".format(topicPartition), t)
            (topicPartition, LogAppendResult(LogAppendInfo.UnknownLogAppendInfo, Some(t)))
        }
      }
    }
  }

  def appendRecords(timeout: Long,
                    requiredAcks: Short,
                    internalTopicsAllowed: Boolean,
                    entriesPerPartition: Map[TopicPartition, MemoryRecords],
                    responseCallback: Map[TopicPartition, PartitionResponse] => Unit): Unit = {
    if (isValidRequiredAcks(requiredAcks)) {
      val sTime = time.milliseconds
      val localProduceResults = appendToLocalLog(internalTopicsAllowed, entriesPerPartition, requiredAcks)
      debug("Produce to local log in %d ms".format(time.milliseconds - sTime))

      val produceStatus = localProduceResults.map { case (topicPartition, result) =>
        topicPartition ->
          ProducePartitionStatus(
            result.info.lastOffset + 1, // required offset
            new PartitionResponse(result.error, result.info.firstOffset, result.info.logAppendTime)) // response status
      }
      if (delayedProduceRequestRequired(requiredAcks, entriesPerPartition, localProduceResults)) {
        // create delayed produce operation
        val produceMetadata = ProduceMetadata(requiredAcks, produceStatus)
        val delayedProduce = new DelayedProduce(timeout, produceMetadata, this, responseCallback)

        // create a list of (topic, partition) pairs to use as keys for this delayed produce operation
        val producerRequestKeys = entriesPerPartition.keys.map(new TopicPartitionOperationKey(_)).toSeq

        // try to complete the request immediately, otherwise put it into the purgatory
        // this is because while the delayed produce operation is being created, new
        // requests may arrive and hence make this operation completable.
        // delayedProducePurgatory.tryCompleteElseWatch(delayedProduce, producerRequestKeys)

      } else {
        // we can respond immediately
        val produceResponseStatus = produceStatus.mapValues(status => status.responseStatus)
        responseCallback(produceResponseStatus)
      }
    }
  }

  def readFromLocalLog(replicaId: Int,
                       fetchOnlyFromLeader: Boolean,
                       readOnlyCommitted: Boolean,
                       fetchMaxBytes: Int,
                       hardMaxBytesLimit: Boolean,
                       readPartitionInfo: Seq[(TopicPartition, PartitionData)],
                       quota: ReplicaQuota,
                       isolationLevel: IsolationLevel): Seq[(TopicPartition, LogReadResult)] = {

    def read(tp: TopicPartition, fetchInfo: PartitionData, limitBytes: Int, minOneMessage: Boolean): LogReadResult = {
      val offset = fetchInfo.fetchOffset
      val partitionFetchSize = fetchInfo.maxBytes
      val followerLogStartOffset = fetchInfo.logStartOffset

      //BrokerTopicStats.getBrokerTopicStats(tp.topic).totalFetchRequestRate.mark()
      //BrokerTopicStats.getBrokerAllTopicsStats().totalFetchRequestRate.mark()

      try {
        trace(s"Fetching log segment for partition $tp, offset $offset, partition fetch size $partitionFetchSize, " +
          s"remaining response limit $limitBytes" +
          (if (minOneMessage) s", ignoring response/partition size limits" else ""))

        // decide whether to only fetch from leader
        val localReplica = getLeaderReplicaIfLocal(tp)

        // decide whether to only fetch committed data (i.e. messages below high watermark)
        val maxOffsetOpt = if (readOnlyCommitted)
          Some(localReplica.highWatermark.messageOffset)
        else
          None

        /* Read the LogOffsetMetadata prior to performing the read from the log.
         * We use the LogOffsetMetadata to determine if a particular replica is in-sync or not.
         * Using the log end offset after performing the read can lead to a race condition
         * where data gets appended to the log immediately after the replica has consumed from it
         * This can cause a replica to always be out of sync.
         */
        val initialLogEndOffset = localReplica.logEndOffset.messageOffset
        val initialHighWatermark = localReplica.highWatermark.messageOffset
        val initialLogStartOffset = localReplica.logStartOffset
        val fetchTimeMs = time.milliseconds
        val logReadInfo = localReplica.log match {
          case Some(log) =>
            val adjustedFetchSize = math.min(partitionFetchSize, limitBytes)

            // Try the read first, this tells us whether we need all of adjustedFetchSize for this partition
            val fetch = log.read(offset, adjustedFetchSize, maxOffsetOpt, minOneMessage, isolationLevel)

            // If the partition is being throttled, simply return an empty set.
            if (shouldLeaderThrottle(quota, tp, replicaId))
              FetchDataInfo(fetch.fetchOffsetMetadata, MemoryRecords.EMPTY)
            // For FetchRequest version 3, we replace incomplete message sets with an empty one as consumers can make
            // progress in such cases and don't need to report a `RecordTooLargeException`
            else if (!hardMaxBytesLimit && fetch.firstEntryIncomplete)
              FetchDataInfo(fetch.fetchOffsetMetadata, MemoryRecords.EMPTY)
            else fetch

          case None =>
            error(s"Leader for partition $tp does not have a local log")
            FetchDataInfo(LogOffsetMetadata.UnknownOffsetMetadata, MemoryRecords.EMPTY)
        }

        LogReadResult(info = logReadInfo,
          hw = initialHighWatermark,
          leaderLogStartOffset = initialLogStartOffset,
          leaderLogEndOffset = initialLogEndOffset,
          followerLogStartOffset = followerLogStartOffset,
          fetchTimeMs = fetchTimeMs,
          readSize = partitionFetchSize,
          exception = None)
      } catch {
        // NOTE: Failed fetch requests metric is not incremented for known exceptions since it
        // is supposed to indicate un-expected failure of a broker in handling a fetch request
        case e@ (_: UnknownTopicOrPartitionException |
                 _: NotLeaderForPartitionException |
                 _: ReplicaNotAvailableException |
                 _: OffsetOutOfRangeException) =>
          LogReadResult(info = FetchDataInfo(LogOffsetMetadata.UnknownOffsetMetadata, MemoryRecords.EMPTY),
            hw = -1L,
            leaderLogStartOffset = -1L,
            leaderLogEndOffset = -1L,
            followerLogStartOffset = -1L,
            fetchTimeMs = -1L,
            readSize = partitionFetchSize,
            exception = Some(e))
        case e: Throwable =>
          error(s"Error processing fetch operation on partition $tp, offset $offset", e)
          LogReadResult(info = FetchDataInfo(LogOffsetMetadata.UnknownOffsetMetadata, MemoryRecords.EMPTY),
            hw = -1L,
            leaderLogStartOffset = -1L,
            leaderLogEndOffset = -1L,
            followerLogStartOffset = -1L,
            fetchTimeMs = -1L,
            readSize = partitionFetchSize,
            exception = Some(e))
      }
    }

    var limitBytes = fetchMaxBytes
    val result = new mutable.ArrayBuffer[(TopicPartition, LogReadResult)]
    var minOneMessage = !hardMaxBytesLimit
    readPartitionInfo.foreach { case (tp, fetchInfo) =>
      val readResult = read(tp, fetchInfo, limitBytes, minOneMessage)
      val messageSetSize = readResult.info.records.sizeInBytes
      // Once we read from a non-empty partition, we stop ignoring request and partition level size limits
      if (messageSetSize > 0)
        minOneMessage = false
      limitBytes = math.max(0, limitBytes - messageSetSize)
      result += (tp -> readResult)
    }
    result
  }

  def tryCompleteDelayedFetch(key: DelayedOperationKey) {
    val completed = delayedFetchPurgatory.checkAndComplete(key)
    debug("Request key %s unblocked %d fetch requests.".format(key.keyLabel, completed))
  }

  def tryCompleteDelayedProduce(key: DelayedOperationKey) {
    //val completed = delayedProducePurgatory.checkAndComplete(key)
    //debug("Request key %s unblocked %d producer requests.".format(key.keyLabel, completed))
  }

  def tryCompleteDelayedDeleteRecords(key: DelayedOperationKey) {
    //val completed = delayedDeleteRecordsPurgatory.checkAndComplete(key)
    //debug("Request key %s unblocked %d DeleteRecordsRequest.".format(key.keyLabel, completed))
  }

  private def delayedProduceRequestRequired(requiredAcks: Short,
                                            entriesPerPartition: Map[TopicPartition, MemoryRecords],
                                            localProduceResults: Map[TopicPartition, LogAppendResult]): Boolean = {
    requiredAcks == -1 &&
      entriesPerPartition.nonEmpty &&
      localProduceResults.values.count(_.exception.isDefined) < entriesPerPartition.size
  }

  def getLeaderReplicaIfLocal(topicPartition: TopicPartition): Replica =  {
    val partitionOpt = getPartition(topicPartition)
    partitionOpt match {
      case None =>
        throw new UnknownTopicOrPartitionException(s"Partition $topicPartition doesn't exist ")
      case Some(partition) =>
        partition.localReplica
    }
  }

  def shouldLeaderThrottle(quota: ReplicaQuota, topicPartition: TopicPartition, replicaId: Int): Boolean = {
    val isReplicaInSync = true
    //getPartition(topicPartition).flatMap{ partition =>
    //  partition.getReplica(replicaId).map(partition.inSyncReplicas.contains)
    //}.getOrElse(false)
    quota.isThrottled(topicPartition) && quota.isQuotaExceeded && !isReplicaInSync
  }

  def getOrCreatePartition(topicPartition: TopicPartition): Partition =
    allPartitions.getAndMaybePut(topicPartition)

  def getPartition(topicPartition: TopicPartition): Option[Partition] =
    Option(allPartitions.get(topicPartition))
}
