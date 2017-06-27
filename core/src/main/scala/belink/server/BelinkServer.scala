package belink.server

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import belink.blockchain.BlockChainManager
import belink.metrics.BelinkMetricsReporter
import belink.network.SocketServer
import belink.security.CredentialProvider
import belink.utils.{BelinkScheduler, CoreUtils, Logging}
import com.ynet.belink.common.metrics.{MetricConfig, Metrics, MetricsReporter, Sensor}
import com.ynet.belink.common.utils.Time

import scala.collection.JavaConverters._
/**
  * Created by goldratio on 27/06/2017.
  */
object BelinkServer {
  private[server] def metricConfig(belinkConfig: BelinkConfig): MetricConfig = {
    new MetricConfig()
      .samples(belinkConfig.metricNumSamples)
      .recordLevel(Sensor.RecordingLevel.forName(belinkConfig.metricRecordingLevel))
      .timeWindow(belinkConfig.metricSampleWindowMs, TimeUnit.MILLISECONDS)
  }
}
class BelinkServer(val config: BelinkConfig, time: Time = Time.SYSTEM,
                   threadNamePrefix: Option[String] = None,
                   belinkMetricsReporters: Seq[BelinkMetricsReporter] = List()) extends Logging {

  private val startupComplete = new AtomicBoolean(false)
  private val isShuttingDown = new AtomicBoolean(false)
  private val isStartingUp = new AtomicBoolean(false)

  private var shutdownLatch = new CountDownLatch(1)

  var metrics: Metrics = null

  val belinkScheduler = new BelinkScheduler(config.backgroundThreads)
  var socketServer: SocketServer = null

  var blockChainManager: BlockChainManager = null

  var credentialProvider: CredentialProvider = null

  def startup(): Unit = {
    try {
      info("starting")

      if (isShuttingDown.get)
        throw new IllegalStateException("Kafka server is still shutting down, cannot re-start!")

      if(startupComplete.get)
        return

      val canStartup = isStartingUp.compareAndSet(false, true)
      if (canStartup) {

        val metricConfig = BelinkServer.metricConfig(config)

        val reporters = config.getConfiguredInstances(BelinkConfig.MetricReporterClassesProp, classOf[MetricsReporter],
          Map.empty[String, AnyRef].asJava)

        metrics = new Metrics(metricConfig, reporters, time, true)

        credentialProvider = new CredentialProvider(config.saslEnabledMechanisms)

        belinkScheduler.startup()

        socketServer = new SocketServer(config, metrics, time, credentialProvider)
        socketServer.startup()

        blockChainManager = createBlockChainManager(isShuttingDown)
        blockChainManager.startup()

        shutdownLatch = new CountDownLatch(1)
        startupComplete.set(true)
        isStartingUp.set(false)
        info("started")
      }
    } catch {
      case e: Throwable =>
        fatal("Fatal error during KafkaServer startup. Prepare to shutdown", e)
        isStartingUp.set(false)
        shutdown()
        throw e
    }
  }


  protected def createBlockChainManager(isShuttingDown: AtomicBoolean): BlockChainManager =
    new BlockChainManager(config, time, belinkScheduler, isShuttingDown)


  def shutdown() {
    try {
      info("shutting down")

      if (isStartingUp.get)
        throw new IllegalStateException("Kafka server is still starting up, cannot shut down!")

      if (shutdownLatch.getCount > 0 && isShuttingDown.compareAndSet(false, true)) {
        if(socketServer != null)
          CoreUtils.swallow(socketServer.shutdown())
        CoreUtils.swallow(belinkScheduler.shutdown())
        if(blockChainManager != null)
          CoreUtils.swallow(blockChainManager.shutdown())

        startupComplete.set(false)
        isShuttingDown.set(false)
        shutdownLatch.countDown()
        info("shut down completed")
      }
    } catch {
      case e: Throwable =>
        fatal("Fatal error during KafkaServer shutdown.", e)
        isShuttingDown.set(false)
        throw e
    }
  }

  /**
    * After calling shutdown(), use this API to wait until the shutdown is complete
    */
  def awaitShutdown(): Unit = shutdownLatch.await()
}
