package belink.server

import java.util.Properties

import belink.cluster.EndPoint
import belink.utils.CoreUtils
import com.ynet.belink.clients.CommonClientConfigs
import com.ynet.belink.common.config.{AbstractConfig, ConfigDef, ConfigException, SaslConfigs}
import com.ynet.belink.common.metrics.Sensor
import com.ynet.belink.common.network.ListenerName
import com.ynet.belink.common.protocol.SecurityProtocol

import scala.collection.Map

/**
  * Created by goldratio on 27/06/2017.
  */
object Defaults {
  val SocketSendBufferBytes: Int = 100 * 1024
  val SocketReceiveBufferBytes: Int = 100 * 1024
  val SocketRequestMaxBytes: Int = 100 * 1024 * 1024


  val MaxConnectionsPerIp: Int = Int.MaxValue
  val MaxConnectionsPerIpOverrides: String = ""
  val ConnectionsMaxIdleMs = 10 * 60 * 1000L
  val QueuedMaxRequests = 500


  val NumNetworkThreads = 3
  val BackgroundThreads = 10
  /** ********* Kafka Metrics Configuration ***********/
  val MetricNumSamples = 2
  val MetricSampleWindowMs = 30000
  val MetricReporterClasses = ""
  val MetricRecordingLevel = Sensor.RecordingLevel.INFO.toString()


  val BlockchainSyncIntervalMs = 5 * 1000L

  /** ********* Socket Server Configuration ***********/
  val Port = 9092
  val HostName: String = new String("")


  /** ********* Sasl configuration ***********/
  val SaslMechanismInterBrokerProtocol = SaslConfigs.DEFAULT_SASL_MECHANISM
  val SaslEnabledMechanisms = SaslConfigs.DEFAULT_SASL_ENABLED_MECHANISMS
  val SaslKerberosKinitCmd = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD
  val SaslKerberosTicketRenewWindowFactor = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_WINDOW_FACTOR
  val SaslKerberosTicketRenewJitter = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_JITTER
  val SaslKerberosMinTimeBeforeRelogin = SaslConfigs.DEFAULT_KERBEROS_MIN_TIME_BEFORE_RELOGIN
  val SaslKerberosPrincipalToLocalRules = SaslConfigs.DEFAULT_SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES

  val ListenerSecurityProtocolMap: String = EndPoint.DefaultSecurityProtocolMap.map { case (listenerName, securityProtocol) =>
    s"${listenerName.value}:${securityProtocol.name}"
  }.mkString(",")
}

object BelinkConfig {
  val MaxConnectionsPerIpProp = "max.connections.per.ip"
  val MaxConnectionsPerIpOverridesProp = "max.connections.per.ip.overrides"

  /** ********* Kafka Metrics Configuration ***********/
  val MetricSampleWindowMsProp = CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_CONFIG
  val MetricNumSamplesProp: String = CommonClientConfigs.METRICS_NUM_SAMPLES_CONFIG
  val MetricReporterClassesProp: String = CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG
  val MetricRecordingLevelProp: String = CommonClientConfigs.METRICS_RECORDING_LEVEL_CONFIG

  val ConnectionsMaxIdleMsProp = "connections.max.idle.ms"

  val NumNetworkThreadsProp = "num.network.threads"

  val BackgroundThreadsProp = "background.threads"

  val SocketSendBufferBytesProp = "socket.send.buffer.bytes"
  val SocketReceiveBufferBytesProp = "socket.receive.buffer.bytes"
  val SocketRequestMaxBytesProp = "socket.request.max.bytes"

  val SaslEnabledMechanismsProp = SaslConfigs.SASL_ENABLED_MECHANISMS

  val BlockchainSyncIntervalProp = "blockchain.time.interval.ms"

  val ListenersProp = "listeners"

  val ListenerSecurityProtocolMapProp = "listener.security.protocol.map"

  val QueuedMaxRequestsProp = "queued.max.requests"

  /** ********* Socket Server Configuration ***********/
  val PortProp = "port"
  val HostNameProp = "host.name"


  val MaxConnectionsPerIpDoc = "The maximum number of connections we allow from each ip address"
  val MaxConnectionsPerIpOverridesDoc = "Per-ip or hostname overrides to the default maximum number of connections"
  val ConnectionsMaxIdleMsDoc = "Idle connections timeout: the server socket processor threads close the connections that idle more than this"
  val QueuedMaxRequestsDoc = "The number of queued requests allowed before blocking the network threads"

  val NumNetworkThreadsDoc = "the number of network threads that the server uses for handling network requests"

  val SocketSendBufferBytesDoc = "The SO_SNDBUF buffer of the socket sever sockets. If the value is -1, the OS default will be used."
  val SocketReceiveBufferBytesDoc = "The SO_RCVBUF buffer of the socket sever sockets. If the value is -1, the OS default will be used."
  val SocketRequestMaxBytesDoc = "The maximum number of bytes in a socket request"

  val BackgroundThreadsDoc = "The number of threads to use for various background processing tasks"
  val ListenersDoc = "Listener List - Comma-separated list of URIs we will listen on and the listener names." +
    s" If the listener name is not a security protocol, $ListenerSecurityProtocolMapProp must also be set.\n" +
    " Specify hostname as 0.0.0.0 to bind to all interfaces.\n" +
    " Leave hostname empty to bind to default interface.\n" +
    " Examples of legal listener lists:\n" +
    " PLAINTEXT://myhost:9092,SSL://:9091\n" +
    " CLIENT://0.0.0.0:9092,REPLICATION://localhost:9093\n"
  /** ********* Kafka Metrics Configuration ***********/
  val MetricSampleWindowMsDoc = CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_DOC
  val MetricNumSamplesDoc = CommonClientConfigs.METRICS_NUM_SAMPLES_DOC
  val MetricReporterClassesDoc = CommonClientConfigs.METRIC_REPORTER_CLASSES_DOC
  val MetricRecordingLevelDoc = CommonClientConfigs.METRICS_RECORDING_LEVEL_DOC

  val PortDoc = "DEPRECATED: only used when `listeners` is not set. " +
    "Use `listeners` instead. \n" +
    "the port to listen and accept connections on"
  val HostNameDoc = "DEPRECATED: only used when `listeners` is not set. " +
    "Use `listeners` instead. \n" +
    "hostname of broker. If this is set, it will only bind to this address. If this is not set, it will bind to all interfaces"

  val BlockchainSyncIntervalDoc = "sync block chain info interval in ms"
  val ListenerSecurityProtocolMapDoc = "Map between listener names and security protocols. This must be defined for " +
    "the same security protocol to be usable in more than one port or IP. For example, we can separate internal and " +
    "external traffic even if SSL is required for both. Concretely, we could define listeners with names INTERNAL " +
    "and EXTERNAL and this property as: `INTERNAL:SSL,EXTERNAL:SSL`. As shown, key and value are separated by a colon " +
    "and map entries are separated by commas. Each listener name should only appear once in the map."
  /** ********* Sasl Configuration ****************/
  val SaslMechanismInterBrokerProtocolDoc = "SASL mechanism used for inter-broker communication. Default is GSSAPI."
  val SaslEnabledMechanismsDoc = SaslConfigs.SASL_ENABLED_MECHANISMS_DOC
  val SaslKerberosServiceNameDoc = SaslConfigs.SASL_KERBEROS_SERVICE_NAME_DOC
  val SaslKerberosKinitCmdDoc = SaslConfigs.SASL_KERBEROS_KINIT_CMD_DOC
  val SaslKerberosTicketRenewWindowFactorDoc = SaslConfigs.SASL_KERBEROS_TICKET_RENEW_WINDOW_FACTOR_DOC
  val SaslKerberosTicketRenewJitterDoc = SaslConfigs.SASL_KERBEROS_TICKET_RENEW_JITTER_DOC
  val SaslKerberosMinTimeBeforeReloginDoc = SaslConfigs.SASL_KERBEROS_MIN_TIME_BEFORE_RELOGIN_DOC
  val SaslKerberosPrincipalToLocalRulesDoc = SaslConfigs.SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES_DOC


  private val configDef = {
    import ConfigDef.Importance._
    import ConfigDef.Range._
    import ConfigDef.Type._
    import ConfigDef.ValidString._

    new ConfigDef()
      .define(MaxConnectionsPerIpProp, INT, Defaults.MaxConnectionsPerIp, atLeast(1), MEDIUM, MaxConnectionsPerIpDoc)
      .define(MaxConnectionsPerIpOverridesProp, STRING, Defaults.MaxConnectionsPerIpOverrides, MEDIUM, MaxConnectionsPerIpOverridesDoc)
      .define(ConnectionsMaxIdleMsProp, LONG, Defaults.ConnectionsMaxIdleMs, MEDIUM, ConnectionsMaxIdleMsDoc)
      .define(BackgroundThreadsProp, INT, Defaults.BackgroundThreads, atLeast(1), HIGH, BackgroundThreadsDoc)
      .define(QueuedMaxRequestsProp, INT, Defaults.QueuedMaxRequests, atLeast(1), HIGH, QueuedMaxRequestsDoc)
      .define(NumNetworkThreadsProp, INT, Defaults.NumNetworkThreads, atLeast(1), HIGH, NumNetworkThreadsDoc)
      .define(MetricNumSamplesProp, INT, Defaults.MetricNumSamples, atLeast(1), LOW, MetricNumSamplesDoc)
      .define(MetricSampleWindowMsProp, LONG, Defaults.MetricSampleWindowMs, atLeast(1), LOW, MetricSampleWindowMsDoc)
      .define(MetricReporterClassesProp, LIST, Defaults.MetricReporterClasses, LOW, MetricReporterClassesDoc)
      .define(MetricRecordingLevelProp, STRING, Defaults.MetricRecordingLevel, LOW, MetricRecordingLevelDoc)

      .define(PortProp, INT, Defaults.Port, HIGH, PortDoc)
      .define(HostNameProp, STRING, Defaults.HostName, HIGH, HostNameDoc)
      .define(SocketSendBufferBytesProp, INT, Defaults.SocketSendBufferBytes, HIGH, SocketSendBufferBytesDoc)
      .define(SocketReceiveBufferBytesProp, INT, Defaults.SocketReceiveBufferBytes, HIGH, SocketReceiveBufferBytesDoc)
      .define(SocketRequestMaxBytesProp, INT, Defaults.SocketRequestMaxBytes, atLeast(1), HIGH, SocketRequestMaxBytesDoc)
      .define(BlockchainSyncIntervalProp, LONG, Defaults.BlockchainSyncIntervalMs, MEDIUM, BlockchainSyncIntervalDoc)

      .define(ListenerSecurityProtocolMapProp, STRING, Defaults.ListenerSecurityProtocolMap, LOW, ListenerSecurityProtocolMapDoc)
      .define(ListenersProp, STRING, null, HIGH, ListenersDoc)

      .define(SaslEnabledMechanismsProp, LIST, Defaults.SaslEnabledMechanisms, MEDIUM, SaslEnabledMechanismsDoc)

  }

  def fromProps(props: Properties): BelinkConfig =
    fromProps(props, true)

  def fromProps(props: Properties, doLog: Boolean): BelinkConfig =
    new BelinkConfig(props, doLog)

  def fromProps(defaults: Properties, overrides: Properties): BelinkConfig =
    fromProps(defaults, overrides, true)

  def fromProps(defaults: Properties, overrides: Properties, doLog: Boolean): BelinkConfig = {
    val props = new Properties()
    props.putAll(defaults)
    props.putAll(overrides)
    fromProps(props, doLog)
  }

}

class BelinkConfig(val props: java.util.Map[_, _], doLog: Boolean) extends AbstractConfig(BelinkConfig.configDef, props, doLog) {

  val maxConnectionsPerIp = getInt(BelinkConfig.MaxConnectionsPerIpProp)
  val maxConnectionsPerIpOverrides: Map[String, Int] =
    getMap(BelinkConfig.MaxConnectionsPerIpOverridesProp, getString(BelinkConfig.MaxConnectionsPerIpOverridesProp)).map { case (k, v) => (k, v.toInt)}

  val connectionsMaxIdleMs = getLong(BelinkConfig.ConnectionsMaxIdleMsProp)

  val queuedMaxRequests = getInt(BelinkConfig.QueuedMaxRequestsProp)

  val numNetworkThreads = getInt(BelinkConfig.NumNetworkThreadsProp)

  /** ********* Metric Configuration **************/
  val metricNumSamples = getInt(BelinkConfig.MetricNumSamplesProp)
  val metricSampleWindowMs = getLong(BelinkConfig.MetricSampleWindowMsProp)
  val metricRecordingLevel = getString(BelinkConfig.MetricRecordingLevelProp)

  /** ********* Socket Server Configuration ***********/
  val hostName = getString(BelinkConfig.HostNameProp)
  val port = getInt(BelinkConfig.PortProp)

  val socketSendBufferBytes = getInt(BelinkConfig.SocketSendBufferBytesProp)
  val socketReceiveBufferBytes = getInt(BelinkConfig.SocketReceiveBufferBytesProp)

  val socketRequestMaxBytes = getInt(BelinkConfig.SocketRequestMaxBytesProp)


  val backgroundThreads = getInt(BelinkConfig.BackgroundThreadsProp)

  /** ***** block chain configuration ****** **/
  val blockchainSyncInterval = getLong(BelinkConfig.BlockchainSyncIntervalProp)

  val listeners: Seq[EndPoint] = getListeners

  val saslEnabledMechanisms = getList(BelinkConfig.SaslEnabledMechanismsProp)

  private[belink] lazy val listenerSecurityProtocolMap = getListenerSecurityProtocolMap

  private def getListenerSecurityProtocolMap: Map[ListenerName, SecurityProtocol] = {
    getMap(BelinkConfig.ListenerSecurityProtocolMapProp, getString(BelinkConfig.ListenerSecurityProtocolMapProp))
      .map { case (listenerName, protocolName) =>
        ListenerName.normalised(listenerName) -> getSecurityProtocol(protocolName, BelinkConfig.ListenerSecurityProtocolMapProp)
      }
  }

  private def getListeners: Seq[EndPoint] = {
    Option(getString(BelinkConfig.ListenersProp)).map { listenerProp =>
      CoreUtils.listenerListToEndPoints(listenerProp, listenerSecurityProtocolMap)
    }.getOrElse(CoreUtils.listenerListToEndPoints("PLAINTEXT://" + hostName + ":" + port, listenerSecurityProtocolMap))
  }

  private def getSecurityProtocol(protocolName: String, configName: String): SecurityProtocol = {
    try SecurityProtocol.forName(protocolName)
    catch {
      case e: IllegalArgumentException =>
        throw new ConfigException(s"Invalid security protocol `$protocolName` defined in $configName")
    }
  }

  private def getMap(propName: String, propValue: String): Map[String, String] = {
    try {
      CoreUtils.parseCsvMap(propValue)
    } catch {
      case e: Exception => throw new IllegalArgumentException("Error parsing configuration property '%s': %s".format(propName, e.getMessage))
    }
  }
}