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
package belink.cluster

import java.io.IOException
import java.util.Properties
import java.util.concurrent.locks.ReentrantReadWriteLock

import belink.log.LogConfig
import belink.server.{ReplicaManager, TopicPartitionOperationKey}
import belink.utils.Logging
import com.ynet.belink.common.TopicPartition
import com.ynet.belink.common.record.MemoryRecords
import com.ynet.belink.common.utils.Time
import belink.utils.CoreUtils._
import com.ynet.belink.common.protocol.Errors

/**
 * Data structure that represents a topic partition. The leader maintains the AR, ISR, CUR, RAR
 */
class Partition(val topic: String,
                val partitionId: Int,
                time: Time,
                replicaManager: ReplicaManager) extends Logging {
  val topicPartition = new TopicPartition(topic, partitionId)
  private val logManager = replicaManager.logManager

  //private val logManager = recordManager.logManager
  // The read lock is only required when multiple reads are executed and needs to be in a consistent manner
  private val leaderIsrUpdateLock = new ReentrantReadWriteLock
  @volatile var leaderReplicaIdOpt: Option[Int] = None
  var localReplica: Replica = {
    val config = LogConfig.fromProps(logManager.defaultConfig.originals, new Properties)
    val log = logManager.createLog(topicPartition, config)
    new Replica(0, this, time, log = Some(log))
  }

  /* Epoch of the controller that last changed the leader. This needs to be initialized correctly upon broker startup.
   * One way of doing that is through the controller's start replica state change command. When a new broker starts up
   * the controller sends it a start replica command containing the leader for each partition that the broker hosts.
   * In addition to the leader, the controller can also send the epoch of the controller that elected the leader for
   * each partition. */
  this.logIdent = "Partition [%s,%d]".format(topic, partitionId)

  private def isReplicaLocal(replicaId: Int) : Boolean = true
  val tags = Map("topic" -> topic, "partition" -> partitionId.toString)

  private def isLeaderReplicaLocal: Boolean = true//leaderReplicaIfLocal.isDefined


  def appendRecordsToLeader(records: MemoryRecords, requiredAcks: Int = 0) = {
    val (info, leaderHWIncremented) = inReadLock(leaderIsrUpdateLock) {
      val log = localReplica.log.get
      val info = log.appendAsLeader(records, leaderEpoch = 0)
      // probably unblock some follower fetch requests since log end offset has been updated
      replicaManager.tryCompleteDelayedFetch(TopicPartitionOperationKey(this.topic, this.partitionId))
      // we may need to increment high watermark since ISR could be down to 1
      (info, maybeIncrementLeaderHW(localReplica))
    }
    // some delayed operations may be unblocked after HW changed
    if (leaderHWIncremented)
      tryCompleteDelayedRequests()

    info
  }

  private def tryCompleteDelayedRequests() {
    val requestKey = new TopicPartitionOperationKey(topicPartition)
    replicaManager.tryCompleteDelayedFetch(requestKey)
    replicaManager.tryCompleteDelayedProduce(requestKey)
    replicaManager.tryCompleteDelayedDeleteRecords(requestKey)
  }

  def checkEnoughReplicasReachOffset(requiredOffset: Long): (Boolean, Errors) = {
    (true, Errors.NONE)
  }

  private def maybeIncrementLeaderHW(leaderReplica: Replica, curTime: Long = time.milliseconds): Boolean = {
    val allLogEndOffsets = localReplica.logEndOffset
//    assignedReplicas.filter { replica =>
//      curTime - replica.lastCaughtUpTimeMs <= replicaManager.config.replicaLagTimeMaxMs || inSyncReplicas.contains(replica)
//    }.map(_.logEndOffset)
    //val newHighWatermark = allLogEndOffsets.min(new LogOffsetMetadata.OffsetOrdering)
    val newHighWatermark = allLogEndOffsets//allLogEndOffsets.min(new LogOffsetMetadata.OffsetOrdering)
    val oldHighWatermark = leaderReplica.highWatermark
    if (oldHighWatermark.messageOffset < newHighWatermark.messageOffset || oldHighWatermark.onOlderSegment(newHighWatermark)) {
      leaderReplica.highWatermark = newHighWatermark
      debug("High watermark for partition [%s,%d] updated to %s".format(topic, partitionId, newHighWatermark))
      true
    } else {
      debug("Skipping update high watermark since Old hw %s is larger than new hw %s for partition [%s,%d]. All leo's are %s"
        .format(oldHighWatermark, newHighWatermark, topic, partitionId, allLogEndOffsets))
      false
    }
  }

  override def equals(that: Any): Boolean = that match {
    case other: Partition => partitionId == other.partitionId && topic == other.topic
    case _ => false
  }

  override def hashCode: Int =
    31 + topic.hashCode + 17 * partitionId

  override def toString: String = {
    val partitionString = new StringBuilder
    partitionString.append("Topic: " + topic)
    partitionString.append("; Partition: " + partitionId)
    partitionString.append("; Leader: " + leaderReplicaIdOpt)
    partitionString.toString
  }

  def getReplica(replicaId: Int = 0): Option[Replica] = Option(localReplica)
}
