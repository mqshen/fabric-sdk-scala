package belink.network

import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, LinkedBlockingQueue, TimeUnit}

import belink.api.{ControlledShutdownRequest, RequestOrResponse}
import belink.server.QuotaId
import belink.utils.{Logging, NotNothing}
import com.ynet.belink.common.errors.InvalidRequestException
import com.ynet.belink.common.network.{ListenerName, Send}
import com.ynet.belink.common.protocol.{ApiKeys, Protocol, SecurityProtocol}
import com.ynet.belink.common.requests._
import com.ynet.belink.common.security.auth.BelinkPrincipal
import com.ynet.belink.common.utils.Time

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

  case class Response(request: Request, responseSend: Option[Send], responseAction: ResponseAction) {
    request.responseCompleteTimeNanos = Time.SYSTEM.nanoseconds
    if (request.apiLocalCompleteTimeNanos == -1L) request.apiLocalCompleteTimeNanos = Time.SYSTEM.nanoseconds

    def processor: Int = request.processor
  }

  object Response {

    def apply(request: Request, responseSend: Send): Response = {
      require(request != null, "request should be non null")
      require(responseSend != null, "responseSend should be non null")
      new Response(request, Some(responseSend), SendAction)
    }

    def apply(request: Request, response: AbstractResponse): Response = {
      require(request != null, "request should be non null")
      require(response != null, "response should be non null")
      apply(request, response.toSend(request.connectionId, request.header))
    }

  }

  trait ResponseAction
  case object SendAction extends ResponseAction
  case object NoOpAction extends ResponseAction
  case object CloseConnectionAction extends ResponseAction

  case class Session(principal: BelinkPrincipal, clientAddress: InetAddress) {
    val sanitizedUser = QuotaId.sanitize(principal.getName)
  }

  object EmptySession extends Session(BelinkPrincipal.ANONYMOUS, InetAddress.getLocalHost())

  case class Request(processor: Int, connectionId: String, session: Session, private var buffer: ByteBuffer,
                     startTimeMs: Long, listenerName: ListenerName, securityProtocol: SecurityProtocol) {

    @volatile var requestDequeueTimeNanos = -1L
    @volatile var apiLocalCompleteTimeNanos = -1L
    @volatile var responseCompleteTimeNanos = -1L
    @volatile var responseDequeueTimeNanos = -1L
    @volatile var apiRemoteCompleteTimeNanos = -1L
    @volatile var recordNetworkThreadTimeCallback: Option[Long => Unit] = None

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

    def requestThreadTimeNanos = {
      if (apiLocalCompleteTimeNanos == -1L) apiLocalCompleteTimeNanos = Time.SYSTEM.nanoseconds
      math.max(apiLocalCompleteTimeNanos - requestDequeueTimeNanos, 0L)
    }

  }

}

class RequestChannel(val numProcessors: Int, val queueSize: Int) {
  private var responseListeners: List[(Int) => Unit] = Nil
  private val requestQueue = new ArrayBlockingQueue[RequestChannel.Request](queueSize)
  private val responseQueues = new Array[BlockingQueue[RequestChannel.Response]](numProcessors)
  for(i <- 0 until numProcessors)
    responseQueues(i) = new LinkedBlockingQueue[RequestChannel.Response]()

  def sendRequest(request: RequestChannel.Request) {
    requestQueue.put(request)
  }

  def addResponseListener(onResponse: Int => Unit) {
    responseListeners ::= onResponse
  }

  /** Send a response back to the socket server to be sent over the network */
  def sendResponse(response: RequestChannel.Response) {
    responseQueues(response.processor).put(response)
    for(onResponse <- responseListeners)
      onResponse(response.processor)
  }

  /** Get the next request or block until specified time has elapsed */
  def receiveRequest(timeout: Long): RequestChannel.Request =
    requestQueue.poll(timeout, TimeUnit.MILLISECONDS)

  def receiveResponse(processor: Int): RequestChannel.Response = {
    val response = responseQueues(processor).poll()
    if (response != null)
      response.request.responseDequeueTimeNanos = Time.SYSTEM.nanoseconds
    response
  }

}
