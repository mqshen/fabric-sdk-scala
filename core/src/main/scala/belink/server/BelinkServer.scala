package belink.server

import java.util
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import belink.blockchain.BlockChainManager
import belink.log.{LogConfig, LogManager}
import belink.metrics.BelinkMetricsReporter
import belink.network.SocketServer
import belink.security.CredentialProvider
import belink.security.auth.Authorizer
import belink.utils.{BelinkScheduler, CoreUtils, Logging}
import com.ynet.belink.common.metrics.{MetricConfig, Metrics, MetricsReporter, Sensor}
import com.ynet.belink.common.utils.Time

import scala.collection.JavaConverters._
/**
  * Created by goldratio on 27/06/2017.
  */
object BelinkServer {

  private[belink] def copyBelinkConfigToLog(belinkConfig: BelinkConfig): java.util.Map[String, Object] = {
    val logProps = new util.HashMap[String, Object]()
    logProps.put(LogConfig.SegmentBytesProp, belinkConfig.logSegmentBytes)
    logProps.put(LogConfig.SegmentMsProp, belinkConfig.logRollTimeMillis)
//    logProps.put(LogConfig.SegmentJitterMsProp, belinkConfig.logRollTimeJitterMillis)
//    logProps.put(LogConfig.SegmentIndexBytesProp, belinkConfig.logIndexSizeMaxBytes)
//    logProps.put(LogConfig.FlushMessagesProp, belinkConfig.logFlushIntervalMessages)
//    logProps.put(LogConfig.FlushMsProp, belinkConfig.logFlushIntervalMs)
//    logProps.put(LogConfig.RetentionBytesProp, belinkConfig.logRetentionBytes)
//    logProps.put(LogConfig.RetentionMsProp, belinkConfig.logRetentionTimeMillis: java.lang.Long)
//    logProps.put(LogConfig.MaxMessageBytesProp, belinkConfig.messageMaxBytes)
//    logProps.put(LogConfig.IndexIntervalBytesProp, belinkConfig.logIndexIntervalBytes)
//    logProps.put(LogConfig.DeleteRetentionMsProp, belinkConfig.logCleanerDeleteRetentionMs)
//    logProps.put(LogConfig.MinCompactionLagMsProp, belinkConfig.logCleanerMinCompactionLagMs)
//    logProps.put(LogConfig.FileDeleteDelayMsProp, belinkConfig.logDeleteDelayMs)
//    logProps.put(LogConfig.MinCleanableDirtyRatioProp, belinkConfig.logCleanerMinCleanRatio)
//    logProps.put(LogConfig.CleanupPolicyProp, belinkConfig.logCleanupPolicy)
//    logProps.put(LogConfig.MinInSyncReplicasProp, belinkConfig.minInSyncReplicas)
//    logProps.put(LogConfig.CompressionTypeProp, belinkConfig.compressionType)
//    logProps.put(LogConfig.UncleanLeaderElectionEnableProp, belinkConfig.uncleanLeaderElectionEnable)
//    logProps.put(LogConfig.PreAllocateEnableProp, belinkConfig.logPreAllocateEnable)
//    logProps.put(LogConfig.MessageFormatVersionProp, belinkConfig.logMessageFormatVersion.version)
//    logProps.put(LogConfig.MessageTimestampTypeProp, belinkConfig.logMessageTimestampType.name)
//    logProps.put(LogConfig.MessageTimestampDifferenceMaxMsProp, belinkConfig.logMessageTimestampDifferenceMaxMs: java.lang.Long)
    logProps
  }

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

  var apis: BelinkApis = null

  val belinkScheduler = new BelinkScheduler(config.backgroundThreads)
  var socketServer: SocketServer = null
  var requestHandlerPool: BelinkRequestHandlerPool = null

  var logManager: LogManager = null
  var quotaManagers: QuotaFactory.QuotaManagers = null
  var replicaManager: ReplicaManager = null
  var blockChainManager: BlockChainManager = null
  var credentialProvider: CredentialProvider = null
  var authorizer: Option[Authorizer] = None


  var metadataCache: MetadataCache = null

  private var _brokerTopicStats: BrokerTopicStats = null
  private[belink] def brokerTopicStats = _brokerTopicStats

  private var _clusterId: String = null
  def clusterId: String = _clusterId

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

        /* register broker metrics */
        _brokerTopicStats = new BrokerTopicStats

        _clusterId = "test"

        metrics = new Metrics(metricConfig, reporters, time, true)
        quotaManagers = QuotaFactory.instantiate(config, metrics, time)

        credentialProvider = new CredentialProvider(config.saslEnabledMechanisms)

        belinkScheduler.startup()

        /* start log manager */
        logManager = LogManager(config, belinkScheduler, time, brokerTopicStats)
        logManager.startup()

        socketServer = new SocketServer(config, metrics, time, credentialProvider)
        socketServer.startup()

        /* start replica manager */
        replicaManager = createReplicaManager(isShuttingDown)
        replicaManager.startup()

        /* Get the authorizer and initialize it if one is specified.*/
        authorizer = Option(config.authorizerClassName).filter(_.nonEmpty).map { authorizerClassName =>
          val authZ = CoreUtils.createObject[Authorizer](authorizerClassName)
          authZ.configure(config.originals())
          authZ
        }
        metadataCache = new MetadataCache(config.brokerId)

        /* start processing requests */
        apis = new BelinkApis(socketServer.requestChannel, replicaManager, authorizer, config, metadataCache, quotaManagers, clusterId, time)
        requestHandlerPool = new BelinkRequestHandlerPool(socketServer.requestChannel, apis, time,
          config.numIoThreads)

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

  protected def createReplicaManager(isShuttingDown: AtomicBoolean): ReplicaManager =
    new ReplicaManager(config, metrics, time, belinkScheduler, logManager, isShuttingDown)


  protected def createBlockChainManager(isShuttingDown: AtomicBoolean): BlockChainManager =
    new BlockChainManager(config, time, belinkScheduler, socketServer.requestChannel, isShuttingDown)


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
