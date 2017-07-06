package belink.cluster


import java.nio.ByteBuffer
import belink.api.ApiUtils._

import belink.common.BelinkException
import com.ynet.belink.common.utils.Utils._

object BrokerEndPoint {

  private val uriParseExp = """\[?([0-9a-zA-Z\-%._:]*)\]?:([0-9]+)""".r

  /**
    * BrokerEndPoint URI is host:port or [ipv6_host]:port
    * Note that unlike EndPoint (or listener) this URI has no security information.
    */
  def parseHostPort(connectionString: String): Option[(String, Int)] = {
    connectionString match {
      case uriParseExp(host, port) => try Some(host, port.toInt) catch { case _: NumberFormatException => None }
      case _ => None
    }
  }

  /**
    * BrokerEndPoint URI is host:port or [ipv6_host]:port
    * Note that unlike EndPoint (or listener) this URI has no security information.
    */
  def createBrokerEndPoint(brokerId: Int, connectionString: String): BrokerEndPoint = {
    parseHostPort(connectionString).map { case (host, port) => new BrokerEndPoint(brokerId, host, port) }.getOrElse {
      throw new BelinkException("Unable to parse " + connectionString + " to a broker endpoint")
    }
  }

  def readFrom(buffer: ByteBuffer): BrokerEndPoint = {
    val brokerId = buffer.getInt()
    val host = readShortString(buffer)
    val port = buffer.getInt()
    BrokerEndPoint(brokerId, host, port)
  }
}

/**
  * BrokerEndpoint is used to connect to specific host:port pair.
  * It is typically used by clients (or brokers when connecting to other brokers)
  * and contains no information about the security protocol used on the connection.
  * Clients should know which security protocol to use from configuration.
  * This allows us to keep the wire protocol with the clients unchanged where the protocol is not needed.
  */
case class BrokerEndPoint(id: Int, host: String, port: Int) {

  def connectionString(): String = formatAddress(host, port)

  def writeTo(buffer: ByteBuffer): Unit = {
    buffer.putInt(id)
    writeShortString(buffer, host)
    buffer.putInt(port)
  }

  def sizeInBytes: Int =
    4 + /* broker Id */
      4 + /* port */
      shortStringLength(host)
}
