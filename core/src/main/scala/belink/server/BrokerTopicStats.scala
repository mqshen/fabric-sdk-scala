package belink.server

import belink.utils.Logging

/**
  * Created by goldratio on 29/06/2017.
  */
object BrokerTopicStats extends Logging {
  val MessagesInPerSec = "MessagesInPerSec"
  val BytesInPerSec = "BytesInPerSec"
  val BytesOutPerSec = "BytesOutPerSec"
  val BytesRejectedPerSec = "BytesRejectedPerSec"
  val FailedProduceRequestsPerSec = "FailedProduceRequestsPerSec"
  val FailedFetchRequestsPerSec = "FailedFetchRequestsPerSec"
  val TotalProduceRequestsPerSec = "TotalProduceRequestsPerSec"
  val TotalFetchRequestsPerSec = "TotalFetchRequestsPerSec"

//  private val valueFactory = (k: String) => new BrokerTopicMetrics(Some(k))
//  private val stats = new Pool[String, BrokerTopicMetrics](Some(valueFactory))
//  private val allTopicsStats = new BrokerTopicMetrics(None)
//
//  def getBrokerAllTopicsStats(): BrokerTopicMetrics = allTopicsStats
//
//  def getBrokerTopicStats(topic: String): BrokerTopicMetrics = {
//    stats.getAndMaybePut(topic)
//  }
//
//  def removeMetrics(topic: String) {
//    val metrics = stats.remove(topic)
//    if (metrics != null)
//      metrics.close()
//  }
}
