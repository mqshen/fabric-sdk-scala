package belink.metrics

import java.util.concurrent.atomic.AtomicBoolean

import belink.utils.{CoreUtils, VerifiableProperties}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by goldratio on 27/06/2017.
  */
trait BelinkMetricsReporterMBean {
  def startReporter(pollingPeriodInSeconds: Long)
  def stopReporter()

  /**
    *
    * @return The name with which the MBean will be registered.
    */
  def getMBeanName: String
}

trait BelinkMetricsReporter {
  def init(props: VerifiableProperties)
}

object BelinkMetricsReporter {
  val ReporterStarted: AtomicBoolean = new AtomicBoolean(false)
  private var reporters: ArrayBuffer[BelinkMetricsReporter] = null

  def startReporters (verifiableProps: VerifiableProperties): Seq[BelinkMetricsReporter] = {
    ReporterStarted synchronized {
      if (!ReporterStarted.get()) {
        reporters = ArrayBuffer[BelinkMetricsReporter]()
        val metricsConfig = new BelinkMetricsConfig(verifiableProps)
        if(metricsConfig.reporters.nonEmpty) {
          metricsConfig.reporters.foreach(reporterType => {
            val reporter = CoreUtils.createObject[BelinkMetricsReporter](reporterType)
            reporter.init(verifiableProps)
            reporters += reporter
            reporter match {
              case bean: BelinkMetricsReporterMBean => CoreUtils.registerMBean(reporter, bean.getMBeanName)
              case _ =>
            }
          })
          ReporterStarted.set(true)
        }
      }
    }
    reporters
  }
}