package belink.server

import belink.log.LogAppendInfo
import belink.utils.{Logging, Scheduler}
import com.ynet.belink.common.Topic
import com.ynet.belink.common.metrics.Metrics
import com.ynet.belink.common.protocol.Errors
import com.ynet.belink.common.record.MemoryRecords
import com.ynet.belink.common.requests.ProduceResponse.PartitionResponse
import com.ynet.belink.common.utils.Time

import scala.collection.Map

/**
  * Created by goldratio on 29/06/2017.
  */
case class LogAppendResult(info: LogAppendInfo, exception: Option[Throwable] = None) {
  def error: Errors = exception match {
    case None => Errors.NONE
    case Some(e) => Errors.forException(e)
  }
}

class RecordManager(val config: BelinkConfig,
                    metrics: Metrics,
                    time: Time,
                    scheduler: Scheduler) extends Logging {

  private def isValidRequiredAcks(requiredAcks: Short): Boolean = {
    requiredAcks == -1 || requiredAcks == 1 || requiredAcks == 0
  }

  private def appendToLocalLog(internalTopicsAllowed: Boolean,
                               entriesPerPartition: Map[Topic, MemoryRecords],
                               requiredAcks: Short): Map[Topic, LogAppendResult] = {

  }

  def appendRecords(timeout: Long,
                    requiredAcks: Short,
                    internalTopicsAllowed: Boolean,
                    entriesPerPartition: Map[Topic, MemoryRecords],
                    responseCallback: Map[Topic, PartitionResponse] => Unit): Unit = {
    if (isValidRequiredAcks(requiredAcks)) {
      val sTime = time.milliseconds
      val localProduceResults = appendToLocalLog(internalTopicsAllowed, entriesPerPartition, requiredAcks)
      debug("Produce to local log in %d ms".format(time.milliseconds - sTime))

    }
  }
}
