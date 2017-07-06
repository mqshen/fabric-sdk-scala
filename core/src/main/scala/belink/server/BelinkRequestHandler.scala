package belink.server

import java.util.concurrent.{CountDownLatch, TimeUnit}

import belink.metrics.BelinkMetricsGroup
import belink.network.RequestChannel
import belink.utils.{Exit, Logging, Pool}
import com.ynet.belink.common.internals.FatalExitError
import com.ynet.belink.common.utils.Time

/**
  * Created by goldratio on 28/06/2017.
  */
case class BelinkRequestHandler(id: Int,
                                val totalHandlerThreads: Int,
                                val requestChannel: RequestChannel,
                                apis: BelinkApis,
                                time: Time) extends Runnable with Logging {
  this.logIdent = "[Kafka Request Handler " + id + " on ], "
  private val latch = new CountDownLatch(1)

  def run() {
    while (true) {
      try {
        var req : RequestChannel.Request = null
        while (req == null) {
          // We use a single meter for aggregate idle percentage for the thread pool.
          // Since meter is calculated as total_recorded_value / time_window and
          // time_window is independent of the number of threads, each recorded idle
          // time should be discounted by # threads.
          val startSelectTime = time.nanoseconds
          req = requestChannel.receiveRequest(300)
          //TODO
          //val idleTime = time.nanoseconds - startSelectTime
        }

        if (req eq RequestChannel.AllDone) {
          debug("Kafka request handler %d received shut down command".format(id))
          latch.countDown()
          return
        }
        //req.requestDequeueTimeMs = time.milliseconds
        trace("Kafka request handler %d handling request %s".format(id, req))
        apis.handle(req)
      } catch {
        case e: FatalExitError =>
          latch.countDown()
          Exit.exit(e.statusCode)
        case e: Throwable => error("Exception when handling request", e)
      }
    }
  }

  def initiateShutdown(): Unit = {
    //TODO
    //requestChannel.sendRequest(RequestChannel.AllDone)
  }

  def awaitShutdown(): Unit = latch.await()

}

class BrokerTopicMetrics(name: Option[String]) extends BelinkMetricsGroup {
  val tags: scala.collection.Map[String, String] = name match {
    case None => Map.empty
    case Some(topic) => Map("topic" -> topic)
  }

  val messagesInRate = newMeter(BrokerTopicStats.MessagesInPerSec, "messages", TimeUnit.SECONDS, tags)
  val bytesInRate = newMeter(BrokerTopicStats.BytesInPerSec, "bytes", TimeUnit.SECONDS, tags)
  val bytesOutRate = newMeter(BrokerTopicStats.BytesOutPerSec, "bytes", TimeUnit.SECONDS, tags)
  val bytesRejectedRate = newMeter(BrokerTopicStats.BytesRejectedPerSec, "bytes", TimeUnit.SECONDS, tags)
  private[server] val replicationBytesInRate =
    if (name.isEmpty) Some(newMeter(BrokerTopicStats.ReplicationBytesInPerSec, "bytes", TimeUnit.SECONDS, tags))
    else None
  private[server] val replicationBytesOutRate =
    if (name.isEmpty) Some(newMeter(BrokerTopicStats.ReplicationBytesOutPerSec, "bytes", TimeUnit.SECONDS, tags))
    else None
  val failedProduceRequestRate = newMeter(BrokerTopicStats.FailedProduceRequestsPerSec, "requests", TimeUnit.SECONDS, tags)
  val failedFetchRequestRate = newMeter(BrokerTopicStats.FailedFetchRequestsPerSec, "requests", TimeUnit.SECONDS, tags)
  val totalProduceRequestRate = newMeter(BrokerTopicStats.TotalProduceRequestsPerSec, "requests", TimeUnit.SECONDS, tags)
  val totalFetchRequestRate = newMeter(BrokerTopicStats.TotalFetchRequestsPerSec, "requests", TimeUnit.SECONDS, tags)

  def close() {
    removeMetric(BrokerTopicStats.MessagesInPerSec, tags)
    removeMetric(BrokerTopicStats.BytesInPerSec, tags)
    removeMetric(BrokerTopicStats.BytesOutPerSec, tags)
    removeMetric(BrokerTopicStats.BytesRejectedPerSec, tags)
    if (replicationBytesInRate.isDefined)
      removeMetric(BrokerTopicStats.ReplicationBytesInPerSec, tags)
    if (replicationBytesOutRate.isDefined)
      removeMetric(BrokerTopicStats.ReplicationBytesOutPerSec, tags)
    removeMetric(BrokerTopicStats.FailedProduceRequestsPerSec, tags)
    removeMetric(BrokerTopicStats.FailedFetchRequestsPerSec, tags)
    removeMetric(BrokerTopicStats.TotalProduceRequestsPerSec, tags)
    removeMetric(BrokerTopicStats.TotalFetchRequestsPerSec, tags)
  }
}

