package belink.network

import java.nio.channels.SocketChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

import com.ynet.belink.common.network.{ListenerName, Selector => KSelector}
import belink.utils.Logging

/**
  * Created by goldratio on 27/06/2017.
  */
private[belink] abstract class AbstractServerThread(connectionQuotas: ConnectionQuotas) extends Runnable with Logging {


  private val startupLatch = new CountDownLatch(1)

  // `shutdown()` is invoked before `startupComplete` and `shutdownComplete` if an exception is thrown in the constructor
  // (e.g. if the address is already in use). We want `shutdown` to proceed in such cases, so we first assign an open
  // latch and then replace it in `startupComplete()`.
  @volatile private var shutdownLatch = new CountDownLatch(0)

  private val alive = new AtomicBoolean(true)

  def wakeup(): Unit

  /**
    * Initiates a graceful shutdown by signaling to stop and waiting for the shutdown to complete
    */
  def shutdown(): Unit = {
    alive.set(false)
    wakeup()
    shutdownLatch.await()
  }

  /**
    * Wait for the thread to completely start up
    */
  def awaitStartup(): Unit = startupLatch.await

  /**
    * Record that the thread startup is complete
    */
  protected def startupComplete(): Unit = {
    // Replace the open latch with a closed one
    shutdownLatch = new CountDownLatch(1)
    startupLatch.countDown()
  }

  /**
    * Record that the thread shutdown is complete
    */
  protected def shutdownComplete(): Unit = shutdownLatch.countDown()

  /**
    * Is the server still running?
    */
  protected def isRunning: Boolean = alive.get

  /**
    * Close the connection identified by `connectionId` and decrement the connection count.
    */
  def close(selector: KSelector, connectionId: String): Unit = {
    val channel = selector.channel(connectionId)
    if (channel != null) {
      debug(s"Closing selector connection $connectionId")
      val address = channel.socketAddress
      if (address != null)
        connectionQuotas.dec(address)
      selector.close(connectionId)
    }
  }

  /**
    * Close `channel` and decrement the connection count.
    */
  def close(channel: SocketChannel): Unit = {
    if (channel != null) {
      debug("Closing connection from " + channel.socket.getRemoteSocketAddress())
      connectionQuotas.dec(channel.socket.getInetAddress)
      swallowError(channel.socket().close())
      swallowError(channel.close())
    }
  }
}
