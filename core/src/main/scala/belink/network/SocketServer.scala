package belink.network

import java.net.InetAddress
import java.util.concurrent.CountDownLatch

import belink.cluster.EndPoint
import belink.security.CredentialProvider
import belink.server.BelinkConfig
import belink.utils.Logging
import com.ynet.belink.common.BelinkException
import com.ynet.belink.common.metrics.Metrics
import com.ynet.belink.common.network.ListenerName
import com.ynet.belink.common.protocol.SecurityProtocol
import com.ynet.belink.common.utils.{Time, Utils}

import scala.collection.{Map, mutable}

/**
  * Created by goldratio on 27/06/2017.
  */
class SocketServer(val config: BelinkConfig, val metrics: Metrics, val time: Time, val credentialProvider: CredentialProvider) extends Logging {
  private val endpoints = config.listeners.map(l => l.listenerName -> l).toMap
  private val numProcessorThreads = config.numNetworkThreads
  private val maxQueuedRequests = config.queuedMaxRequests
  private val totalProcessorThreads = numProcessorThreads * endpoints.size

  private val maxConnectionsPerIp = config.maxConnectionsPerIp
  private val maxConnectionsPerIpOverrides = config.maxConnectionsPerIpOverrides


  private[network] val acceptors = mutable.Map[EndPoint, Acceptor]()
  private var connectionQuotas: ConnectionQuotas = _

  val requestChannel = new RequestChannel(totalProcessorThreads, maxQueuedRequests)
  private val processors = new Array[Processor](totalProcessorThreads)


  def startup() {
    this.synchronized {

      connectionQuotas = new ConnectionQuotas(maxConnectionsPerIp, maxConnectionsPerIpOverrides)

      val sendBufferSize = config.socketSendBufferBytes
      val recvBufferSize = config.socketReceiveBufferBytes

      var processorBeginIndex = 0
      config.listeners.foreach { endpoint =>
        val listenerName = endpoint.listenerName
        val securityProtocol = endpoint.securityProtocol
        val processorEndIndex = processorBeginIndex + numProcessorThreads

        for (i <- processorBeginIndex until processorEndIndex)
          processors(i) = newProcessor(i, connectionQuotas, listenerName, securityProtocol)

        val acceptor = new Acceptor(endpoint, sendBufferSize, recvBufferSize,
          processors.slice(processorBeginIndex, processorEndIndex), connectionQuotas)
        acceptors.put(endpoint, acceptor)
        Utils.newThread(s"belink-socket-acceptor-$listenerName-$securityProtocol-${endpoint.port}", acceptor, false).start()
        acceptor.awaitStartup()

        processorBeginIndex = processorEndIndex
      }
    }

    info("Started " + acceptors.size + " acceptor threads")
  }

  def shutdown() = {
    info("Shutting down")
    this.synchronized {
      acceptors.values.foreach(_.shutdown)
      processors.foreach(_.shutdown)
    }
    info("Shutdown completed")
  }

  protected[network] def newProcessor(id: Int, connectionQuotas: ConnectionQuotas, listenerName: ListenerName,
                                      securityProtocol: SecurityProtocol): Processor = {
    new Processor(id,
      time,
      config.socketRequestMaxBytes,
      requestChannel,
      connectionQuotas,
      config.connectionsMaxIdleMs,
      listenerName,
      securityProtocol,
      config,
      metrics,
      credentialProvider
    )
  }

}


class ConnectionQuotas(val defaultMax: Int, overrideQuotas: Map[String, Int]) {
  private val overrides = overrideQuotas.map { case (host, count) => (InetAddress.getByName(host), count) }
  private val counts = mutable.Map[InetAddress, Int]()

  def inc(address: InetAddress) {
    counts.synchronized {
      val count = counts.getOrElseUpdate(address, 0)
      counts.put(address, count + 1)
      val max = overrides.getOrElse(address, defaultMax)
      if (count >= max)
        throw new TooManyConnectionsException(address, max)
    }
  }

  def dec(address: InetAddress) {
    counts.synchronized {
      val count = counts.getOrElse(address,
        throw new IllegalArgumentException(s"Attempted to decrease connection count for address with no connections, address: $address"))
      if (count == 1)
        counts.remove(address)
      else
        counts.put(address, count - 1)
    }
  }

  def get(address: InetAddress): Int = counts.synchronized {
    counts.getOrElse(address, 0)
  }
}


class TooManyConnectionsException(val ip: InetAddress, val count: Int) extends BelinkException("Too many connections from %s (maximum = %d)".format(ip, count))
