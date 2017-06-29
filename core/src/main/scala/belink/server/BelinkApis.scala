package belink.server

import belink.network.RequestChannel
import belink.utils.Logging
import com.ynet.belink.common.protocol.ApiKeys
import com.ynet.belink.common.requests.ProduceRequest

/**
  * Created by goldratio on 28/06/2017.
  */
class BelinkApis(val requestChannel: RequestChannel) extends Logging {

  def handle(request: RequestChannel.Request): Unit = {
    try {
      trace("Handling request:%s from connection %s;securityProtocol:%s,principal:%s".
        format(request.requestDesc(true), request.connectionId, request.securityProtocol, request.session.principal))
      ApiKeys.forId(request.requestId) match {
        case ApiKeys.PRODUCE => handleProducerRequest(request)
      }
    }
  }

  def handleProducerRequest(request: RequestChannel.Request): Unit = {
    val produceRequest = request.body[ProduceRequest]
    val numBytesAppended = request.header.toStruct.sizeOf + request.bodyAndSize.size


  }

}
