package belink.network

import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}

import belink.api.{ControlledShutdownRequest, RequestOrResponse}
import belink.server.QuotaId
import belink.utils.{Logging, NotNothing}
import com.ynet.belink.common.errors.InvalidRequestException
import com.ynet.belink.common.network.ListenerName
import com.ynet.belink.common.protocol.{ApiKeys, Protocol, SecurityProtocol}
import com.ynet.belink.common.requests.{AbstractRequest, ApiVersionsRequest, RequestAndSize, RequestHeader}
import com.ynet.belink.common.security.auth.BelinkPrincipal

import scala.reflect.ClassTag

/**
  * Created by goldratio on 27/06/2017.
  */
object RequestChannel extends Logging {

  val AllDone = ""
//  Request(processor = 1, connectionId = "2", Session(BelinkPrincipal.ANONYMOUS, InetAddress.getLocalHost),
//    buffer = shutdownReceive, startTimeMs = 0, listenerName = new ListenerName(""),
//    securityProtocol = SecurityProtocol.PLAINTEXT)

  private def shutdownReceive: ByteBuffer = {
    //TODO
//    val emptyProduceRequest = new ProduceRequest.Builder(RecordBatch.CURRENT_MAGIC_VALUE, 0, 0,
//      Collections.emptyMap[TopicPartition, MemoryRecords]).build()
//    val emptyRequestHeader = new RequestHeader(ApiKeys.PRODUCE.id, emptyProduceRequest.version, "", 0)
//    emptyProduceRequest.serialize(emptyRequestHeader)
    ByteBuffer.wrap("".getBytes())
  }

  case class Session(principal: BelinkPrincipal, clientAddress: InetAddress) {
    val sanitizedUser = QuotaId.sanitize(principal.getName)
  }

  object EmptySession extends Session(BelinkPrincipal.ANONYMOUS, InetAddress.getLocalHost())

  case class Request(processor: Int, connectionId: String, session: Session, private var buffer: ByteBuffer,
                     startTimeMs: Long, listenerName: ListenerName, securityProtocol: SecurityProtocol) {

    @volatile var requestDequeueTimeMs = -1L
    @volatile var apiLocalCompleteTimeMs = -1L
    @volatile var responseCompleteTimeMs = -1L
    @volatile var responseDequeueTimeMs = -1L
    @volatile var apiRemoteCompleteTimeMs = -1L

    val requestId = buffer.getShort()

    val requestObj: RequestOrResponse = if (requestId == ApiKeys.CONTROLLED_SHUTDOWN_KEY.id)
      ControlledShutdownRequest.readFrom(buffer)
    else
      null

    val header: RequestHeader =
      if (requestObj == null) {
        buffer.rewind
        try RequestHeader.parse(buffer)
        catch {
          case ex: Throwable =>
            throw new InvalidRequestException(s"Error parsing request header. Our best guess of the apiKey is: $requestId", ex)
        }
      } else
        null
    val bodyAndSize: RequestAndSize =
      if (requestObj == null)
        try {
          // For unsupported version of ApiVersionsRequest, create a dummy request to enable an error response to be returned later
          if (header.apiKey == ApiKeys.API_VERSIONS.id && !Protocol.apiVersionSupported(header.apiKey, header.apiVersion)) {
            new RequestAndSize(new ApiVersionsRequest.Builder().build(), 0)
          }
          else
            AbstractRequest.getRequest(header.apiKey, header.apiVersion, buffer)
        } catch {
          case ex: Throwable =>
            throw new InvalidRequestException(s"Error getting request for apiKey: ${header.apiKey} and apiVersion: ${header.apiVersion}", ex)
        }
      else
        null

    def body[T <: AbstractRequest](implicit classTag: ClassTag[T], nn: NotNothing[T]): T = {
      bodyAndSize.request match {
        case r: T => r
        case r =>
          throw new ClassCastException(s"Expected request with type ${classTag.runtimeClass}, but found ${r.getClass}")
      }
    }

    def requestDesc(details: Boolean): String = {
      if (requestObj != null)
        requestObj.describe(details)
      else
        s"$header -- ${body[AbstractRequest].toString(details)}"
    }

  }

}

class RequestChannel(val numProcessors: Int, val queueSize: Int) {
  private val requestQueue = new ArrayBlockingQueue[RequestChannel.Request](queueSize)

  def sendRequest(request: RequestChannel.Request) {
    requestQueue.put(request)
  }

  /** Get the next request or block until specified time has elapsed */
  def receiveRequest(timeout: Long): RequestChannel.Request =
    requestQueue.poll(timeout, TimeUnit.MILLISECONDS)

}
