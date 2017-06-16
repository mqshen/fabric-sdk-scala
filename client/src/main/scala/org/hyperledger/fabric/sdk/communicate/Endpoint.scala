package org.hyperledger.fabric.sdk.communicate

import java.io.File
import javax.net.ssl.SSLException

import io.grpc.ManagedChannelBuilder
import io.grpc.netty.{ GrpcSslContexts, NettyChannelBuilder }
import org.hyperledger.fabric.sdk.helper.SDKUtil._

/**
 * Created by goldratio on 20/02/2017.
 */
class Endpoint(url: String, pem: Option[String] = None) {
  val (addr, port, channelBuilder) = {
    val purl = parseGrpcUrl(url)
    val protocol: String = purl.getProperty("protocol")
    val addr = purl.getProperty("host")
    val port = Integer.parseInt(purl.getProperty("port"))

    val channelBuilder: ManagedChannelBuilder[_] = if (protocol.equalsIgnoreCase("grpc")) {
      val channelBuilder = ManagedChannelBuilder.forAddress(addr, port)
      channelBuilder.usePlaintext(true)
      channelBuilder
    } else if (protocol.equalsIgnoreCase("grpcs")) {
      pem.map { pem =>
        try {
          val sslContext = GrpcSslContexts.forClient.trustManager(new File(pem)).build
          NettyChannelBuilder.forAddress(addr, port).sslContext(sslContext)
        } catch {
          case sslex: SSLException => {
            throw new RuntimeException(sslex)
          }
        }
      }.getOrElse {
        ManagedChannelBuilder.forAddress(addr, port)
      }
    } else throw new RuntimeException("invalid protocol: " + protocol)
    channelBuilder.maxInboundMessageSize(20971520)
    //    channelBuilder match {
    //      case nettyBuilder: NettyChannelBuilder =>
    //        nettyBuilder.maxHeaderListSize(Int.MaxValue)
    //      case _ =>
    //    }
    (addr, port, channelBuilder)
  }

}
