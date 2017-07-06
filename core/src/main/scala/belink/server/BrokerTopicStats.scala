package belink.server

import belink.utils.{Logging, Pool}

/**
  * Created by goldratio on 29/06/2017.
  */

object BrokerTopicStats {
  val MessagesInPerSec = "MessagesInPerSec"
  val BytesInPerSec = "BytesInPerSec"
  val BytesOutPerSec = "BytesOutPerSec"
  val BytesRejectedPerSec = "BytesRejectedPerSec"
  val ReplicationBytesInPerSec = "ReplicationBytesInPerSec"
  val ReplicationBytesOutPerSec = "ReplicationBytesOutPerSec"
  val FailedProduceRequestsPerSec = "FailedProduceRequestsPerSec"
  val FailedFetchRequestsPerSec = "FailedFetchRequestsPerSec"
  val TotalProduceRequestsPerSec = "TotalProduceRequestsPerSec"
  val TotalFetchRequestsPerSec = "TotalFetchRequestsPerSec"
  private val valueFactory = (k: String) => new BrokerTopicMetrics(Some(k))
}

class BrokerTopicStats {
  import BrokerTopicStats._

  private val stats = new Pool[String, BrokerTopicMetrics](Some(valueFactory))
  val allTopicsStats = new BrokerTopicMetrics(None)

  def topicStats(topic: String): BrokerTopicMetrics =
    stats.getAndMaybePut(topic)

  def updateReplicationBytesIn(value: Long) {
    //    allTopicsStats.replicationBytesInRate.foreach { metric =>
    //      metric.mark(value)
    //    }
  }

  private def updateReplicationBytesOut(value: Long) {
    //    allTopicsStats.replicationBytesOutRate.foreach { metric =>
    //      metric.mark(value)
    //    }
  }

  def removeMetrics(topic: String) {
    val metrics = stats.remove(topic)
    if (metrics != null)
      metrics.close()
  }

  def updateBytesOut(topic: String, isFollower: Boolean, value: Long) {
    //    if (isFollower) {
    //      updateReplicationBytesOut(value)
    //    } else {
    //      topicStats(topic).bytesOutRate.mark(value)
    //      allTopicsStats.bytesOutRate.mark(value)
    //    }
  }


  def close(): Unit = {
    allTopicsStats.close()
    stats.values.foreach(_.close())
  }

}
