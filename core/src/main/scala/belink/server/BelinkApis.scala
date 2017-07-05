package belink.server

import belink.admin.AdminUtils
import belink.network.RequestChannel
import belink.network.RequestChannel.Session
import belink.security.auth
import belink.security.auth._
import belink.utils.Logging
import com.ynet.belink.common.TopicPartition
import com.ynet.belink.common.protocol.{ApiKeys, Errors}
import com.ynet.belink.common.requests.ProduceResponse.PartitionResponse
import com.ynet.belink.common.requests.{FetchRequest, ProduceRequest, ProduceResponse}

import scala.collection.JavaConverters._
import scala.collection.{Map, immutable}
/**
  * Created by goldratio on 28/06/2017.
  */
class BelinkApis(val requestChannel: RequestChannel,
                 val replicaManager: ReplicaManager,
                 val authorizer: Option[Authorizer]) extends Logging {

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

    val (existingAndAuthorizedForDescribeTopics, nonExistingOrUnauthorizedForDescribeTopics) =
      produceRequest.partitionRecordsOrFail.asScala.partition { case (tp, _) =>
        authorize(request.session, Describe, new Resource(auth.Topic, tp.topic))
      }

    val (authorizedRequestInfo, unauthorizedForWriteRequestInfo) = existingAndAuthorizedForDescribeTopics.partition {
      case (tp, _) => authorize(request.session, Write, new Resource(auth.Topic, tp.topic))
    }

    val internalTopicsAllowed = request.header.clientId == AdminUtils.AdminClientId

    // the callback for sending a produce response
    def sendResponseCallback(responseStatus: Map[TopicPartition, PartitionResponse]) {

      val mergedResponseStatus = responseStatus ++
        unauthorizedForWriteRequestInfo.mapValues(_ => new PartitionResponse(Errors.TOPIC_AUTHORIZATION_FAILED)) ++
        nonExistingOrUnauthorizedForDescribeTopics.mapValues(_ => new PartitionResponse(Errors.UNKNOWN_TOPIC_OR_PARTITION))

      var errorInResponse = false

      mergedResponseStatus.foreach { case (topicPartition, status) =>
        if (status.error != Errors.NONE) {
          errorInResponse = true
          debug("Produce request with correlation id %d from client %s on partition %s failed due to %s".format(
            request.header.correlationId,
            request.header.clientId,
            topicPartition,
            status.error.exceptionName))
        }
      }

      def produceResponseCallback(delayTimeMs: Int) {
        if (produceRequest.acks == 0) {
          // no operation needed if producer request.required.acks = 0; however, if there is any error in handling
          // the request, since no response is expected by the producer, the server will close socket server so that
          // the producer client will know that some error has happened and will refresh its metadata
          if (errorInResponse) {
            val exceptionsSummary = mergedResponseStatus.map { case (topicPartition, status) =>
              topicPartition -> status.error.exceptionName
            }.mkString(", ")
            info(
              s"Closing connection due to error during produce request with correlation id ${request.header.correlationId} " +
                s"from client id ${request.header.clientId} with ack=0\n" +
                s"Topic and partition to exceptions: $exceptionsSummary"
            )
            //requestChannel.closeConnection(request.processor, request)
          } else {
            //requestChannel.noOperation(request.processor, request)
          }
        } else {
          val respBody = new ProduceResponse(mergedResponseStatus.asJava, delayTimeMs)
          //requestChannel.sendResponse(new RequestChannel.Response(request, respBody))
        }
      }

      // When this callback is triggered, the remote API call has completed
      //request.apiRemoteCompleteTimeMs = time.milliseconds
//
//      quotas.produce.recordAndMaybeThrottle(
//        request.session.sanitizedUser,
//        request.header.clientId,
//        numBytesAppended,
//        produceResponseCallback)
    }

    replicaManager.appendRecords(
      produceRequest.timeout.toLong,
      produceRequest.acks,
      internalTopicsAllowed,
      authorizedRequestInfo,
      sendResponseCallback)

    // if the request is put into the purgatory, it will have a held reference and hence cannot be garbage collected;
    // hence we clear its data here inorder to let GC re-claim its memory since it is already appended to log
    produceRequest.clearPartitionRecords()

  }

  private def authorize(session: Session, operation: Operation, resource: Resource): Boolean =
    authorizer.forall(_.authorize(session, operation, resource))

}
