package belink.network

import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue

import belink.security.CredentialProvider
import belink.server.BelinkConfig
import com.ynet.belink.common.metrics.Metrics
import com.ynet.belink.common.protocol.SecurityProtocol
import com.ynet.belink.common.utils.Time
import com.ynet.belink.common.network.{ChannelBuilders, ListenerName, Selector => KSelector}

import scala.collection._
import JavaConverters._

/**
  * Created by goldratio on 27/06/2017.
  */


private[belink] class Processor(val id: Int,
                                time: Time,
                                maxRequestSize: Int,
                                requestChannel: RequestChannel,
                                connectionQuotas: ConnectionQuotas,
                                connectionsMaxIdleMs: Long,
                                listenerName: ListenerName,
                                securityProtocol: SecurityProtocol,
                                config: BelinkConfig,
                                metrics: Metrics,
                                credentialProvider: CredentialProvider) extends AbstractServerThread(connectionQuotas) {
  private val newConnections = new ConcurrentLinkedQueue[SocketChannel]()

  private val metricTags = Map("networkProcessor" -> id.toString).asJava

  private val selector = new KSelector(
    maxRequestSize,
    connectionsMaxIdleMs,
    metrics,
    time,
    "socket-server",
    metricTags,
    false,
    ChannelBuilders.serverChannelBuilder(listenerName, securityProtocol, config, credentialProvider.credentialCache))

  override def run(): Unit = {

  }

  def accept(socketChannel: SocketChannel) {
    newConnections.add(socketChannel)
    wakeup()
  }

  override def wakeup = selector.wakeup()
}
