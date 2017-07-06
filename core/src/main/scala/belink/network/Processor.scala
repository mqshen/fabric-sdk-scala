package belink.network

import java.io.IOException
import java.net.InetAddress
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue

import belink.cluster.BrokerEndPoint
import belink.security.CredentialProvider
import belink.server.BelinkConfig
import com.ynet.belink.common.errors.InvalidRequestException
import com.ynet.belink.common.metrics.Metrics
import com.ynet.belink.common.protocol.SecurityProtocol
import com.ynet.belink.common.utils.Time
import com.ynet.belink.common.network.{ChannelBuilders, ListenerName, Send, Selector => KSelector}
import com.ynet.belink.common.protocol.types.SchemaException
import com.ynet.belink.common.security.auth.BelinkPrincipal

import scala.collection._
import JavaConverters._
import scala.util.control.{ControlThrowable, NonFatal}

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
  private object ConnectionId {
    def fromString(s: String): Option[ConnectionId] = s.split("-") match {
      case Array(local, remote) => BrokerEndPoint.parseHostPort(local).flatMap { case (localHost, localPort) =>
        BrokerEndPoint.parseHostPort(remote).map { case (remoteHost, remotePort) =>
          ConnectionId(localHost, localPort, remoteHost, remotePort)
        }
      }
      case _ => None
    }
  }

  private case class ConnectionId(localHost: String, localPort: Int, remoteHost: String, remotePort: Int) {
    override def toString: String = s"$localHost:$localPort-$remoteHost:$remotePort"
  }

  private val newConnections = new ConcurrentLinkedQueue[SocketChannel]()
  private val inflightResponses = mutable.Map[String, RequestChannel.Response]()

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
    startupComplete()
    while (isRunning) {
      try {
        // setup any new connections that have been queued up
        configureNewConnections()
        // register any new responses for writing
        processNewResponses()
        poll()
        processCompletedReceives()
        processCompletedSends()
        processDisconnected()
      } catch {
        // We catch all the throwables here to prevent the processor thread from exiting. We do this because
        // letting a processor exit might cause a bigger impact on the broker. Usually the exceptions thrown would
        // be either associated with a specific socket channel or a bad request. We just ignore the bad socket channel
        // or request. This behavior might need to be reviewed if we see an exception that need the entire broker to stop.
        case e: ControlThrowable => throw e
        case e: Throwable =>
          error("Processor got uncaught exception.", e)
      }
    }

    debug("Closing selector - processor " + id)
    swallowError(closeAll())
    shutdownComplete()
  }

  private def processCompletedReceives() {
    selector.completedReceives.asScala.foreach { receive =>
      try {
        val openChannel = selector.channel(receive.source)
        val session = {
          // Only methods that are safe to call on a disconnected channel should be invoked on 'channel'.
          val channel = if (openChannel != null) openChannel else selector.closingChannel(receive.source)
          RequestChannel.Session(new BelinkPrincipal(BelinkPrincipal.USER_TYPE, channel.principal.getName), channel.socketAddress)
        }
        val req = RequestChannel.Request(processor = id, connectionId = receive.source, session = session,
          buffer = receive.payload, startTimeMs = time.milliseconds, listenerName = listenerName,
          securityProtocol = securityProtocol)
        requestChannel.sendRequest(req)
        selector.mute(receive.source)
      } catch {
        case e @ (_: InvalidRequestException | _: SchemaException) =>
          // note that even though we got an exception, we can assume that receive.source is valid. Issues with constructing a valid receive object were handled earlier
          error(s"Closing socket for ${receive.source} because of error", e)
          close(selector, receive.source)
      }
    }
  }

  private def processCompletedSends() {
    selector.completedSends.asScala.foreach { send =>
      val resp = inflightResponses.remove(send.destination).getOrElse {
        throw new IllegalStateException(s"Send for ${send.destination} completed, but not in `inflightResponses`")
      }
      //TODO
      //resp.request.updateRequestMetrics()
      selector.unmute(send.destination)
    }
  }

  private def closeAll() {
    selector.channels.asScala.foreach { channel =>
      close(selector, channel.id)
    }
    selector.close()
  }

  private def processDisconnected() {
    selector.disconnected.keySet().asScala.foreach { connectionId =>
      val remoteHost = ConnectionId.fromString(connectionId).getOrElse {
        throw new IllegalStateException(s"connectionId has unexpected format: $connectionId")
      }.remoteHost
      inflightResponses.remove(connectionId)//TODO .foreach(_.request.updateRequestMetrics())
      // the channel has been closed by the selector but the quotas still need to be updated
      connectionQuotas.dec(InetAddress.getByName(remoteHost))
    }
  }

  protected[network] def sendResponse(response: RequestChannel.Response, responseSend: Send) {
    trace(s"Socket server received response to send, registering for write and sending data: $response")
    val channel = selector.channel(responseSend.destination)
    // `channel` can be null if the selector closed the connection because it was idle for too long
    if (channel == null) {
      warn(s"Attempting to send response via channel for which there is no open connection, connection id $id")
      //response.request.updateRequestMetrics(0L)
    }
    else {
      selector.send(responseSend)
      inflightResponses += (response.request.connectionId -> response)
    }
  }

  private def poll() {
    try selector.poll(300)
    catch {
      case e @ (_: IllegalStateException | _: IOException) =>
        error(s"Closing processor $id due to illegal state or IO exception")
        swallow(closeAll())
        shutdownComplete()
        throw e
    }
  }

  private def processNewResponses() {
    var curr = requestChannel.receiveResponse(id)
    while (curr != null) {
      try {
        curr.responseAction match {
          case RequestChannel.NoOpAction =>
            // There is no response to send to the client, we need to read more pipelined requests
            // that are sitting in the server's socket buffer
            //TODO
            //curr.request.updateRequestMetrics
            trace("Socket server received empty response to send, registering for read: " + curr)
            val channelId = curr.request.connectionId
            if (selector.channel(channelId) != null || selector.closingChannel(channelId) != null)
              selector.unmute(channelId)
          case RequestChannel.SendAction =>
            val responseSend = curr.responseSend.getOrElse(
              throw new IllegalStateException(s"responseSend must be defined for SendAction, response: $curr"))
            sendResponse(curr, responseSend)
          case RequestChannel.CloseConnectionAction =>
            //TODO
            //curr.request.updateRequestMetrics
            trace("Closing socket connection actively according to the response code.")
            close(selector, curr.request.connectionId)
        }
      } finally {
        curr = requestChannel.receiveResponse(id)
      }
    }
  }

  def accept(socketChannel: SocketChannel) {
    newConnections.add(socketChannel)
    wakeup()
  }

  private def configureNewConnections() {
    while (!newConnections.isEmpty) {
      val channel = newConnections.poll()
      try {
        debug(s"Processor $id listening to new connection from ${channel.socket.getRemoteSocketAddress}")
        val localHost = channel.socket().getLocalAddress.getHostAddress
        val localPort = channel.socket().getLocalPort
        val remoteHost = channel.socket().getInetAddress.getHostAddress
        val remotePort = channel.socket().getPort
        val connectionId = ConnectionId(localHost, localPort, remoteHost, remotePort).toString
        selector.register(connectionId, channel)
      } catch {
        // We explicitly catch all non fatal exceptions and close the socket to avoid a socket leak. The other
        // throwables will be caught in processor and logged as uncaught exceptions.
        case NonFatal(e) =>
          val remoteAddress = channel.getRemoteAddress
          // need to close the channel here to avoid a socket leak.
          close(channel)
          error(s"Processor $id closed connection from $remoteAddress", e)
      }
    }
  }

  override def wakeup = selector.wakeup()
}
