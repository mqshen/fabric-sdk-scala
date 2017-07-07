package belink.server

import java.util.Properties

import belink.admin.{AdminUtils, RackAwareMode}
import belink.network.RequestChannel
import belink.network.RequestChannel.Session
import belink.security.auth
import belink.security.auth._
import belink.server.QuotaFactory.QuotaManagers
import belink.utils.Logging
import com.ynet.belink.common.TopicPartition
import com.ynet.belink.common.errors.TopicExistsException
import com.ynet.belink.common.protocol.{ApiKeys, Errors, Protocol}
import com.ynet.belink.common.requests.ProduceResponse.PartitionResponse
import com.ynet.belink.common.requests._
import com.ynet.belink.common.utils.Time
import com.ynet.belink.common.internals.Topic.{GROUP_METADATA_TOPIC_NAME, TRANSACTION_STATE_TOPIC_NAME, isInternal}
import com.ynet.belink.common.network.ListenerName

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.{Map, Seq, Set, immutable}
/**
  * Created by goldratio on 28/06/2017.
  */
class BelinkApis(val requestChannel: RequestChannel,
                 val replicaManager: ReplicaManager,
                 val authorizer: Option[Authorizer],
                 val config: BelinkConfig,
                 val metadataCache: MetadataCache,
                 val quotas: QuotaManagers,
                 val clusterId: String,
                 time: Time) extends Logging {

  def handle(request: RequestChannel.Request): Unit = {
    try {
      trace("Handling request:%s from connection %s;securityProtocol:%s,principal:%s".
        format(request.requestDesc(true), request.connectionId, request.securityProtocol, request.session.principal))
      ApiKeys.forId(request.requestId) match {
        case ApiKeys.PRODUCE => handleProducerRequest(request)
        case ApiKeys.API_VERSIONS => handleApiVersionsRequest(request)
        case ApiKeys.METADATA => handleTopicMetadataRequest(request)
        case x => println(s"go for go :$x")
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

  def handleApiVersionsRequest(request: RequestChannel.Request) {
    // Note that broker returns its full list of supported ApiKeys and versions regardless of current
    // authentication state (e.g., before SASL authentication on an SASL listener, do note that no
    // Kafka protocol requests may take place on a SSL listener before the SSL handshake is finished).
    // If this is considered to leak information about the broker version a workaround is to use SSL
    // with client authentication which is performed at an earlier stage of the connection where the
    // ApiVersionRequest is not available.
    def sendResponseCallback(requestThrottleMs: Int) {
      val responseSend =
        if (Protocol.apiVersionSupported(ApiKeys.API_VERSIONS.id, request.header.apiVersion))
          ApiVersionsResponse.apiVersionsResponse(requestThrottleMs, config.interBrokerProtocolVersion.messageFormatVersion).toSend(request.connectionId, request.header)
        else ApiVersionsResponse.unsupportedVersionSend(request.connectionId, request.header)
      requestChannel.sendResponse(RequestChannel.Response(request, responseSend))
    }
    sendResponseMaybeThrottle(request, request.header.clientId, sendResponseCallback)
  }

  private def sendResponseMaybeThrottle(request: RequestChannel.Request, clientId: String, sendResponseCallback: Int => Unit) {

    if (request.apiRemoteCompleteTimeNanos == -1) {
      // When this callback is triggered, the remote API call has completed
      request.apiRemoteCompleteTimeNanos = time.nanoseconds
    }
    quotas.request.maybeRecordAndThrottle(request.session.sanitizedUser, clientId,
      request.requestThreadTimeNanos, sendResponseCallback,
      callback => request.recordNetworkThreadTimeCallback = Some(callback))
  }

  private def sendResponseMaybeThrottle(request: RequestChannel.Request, createResponse: Int => AbstractResponse) {
    sendResponseMaybeThrottle(request, request.header.clientId, { requestThrottleMs =>
      sendResponse(request, createResponse(requestThrottleMs))
    })
  }

  private def sendResponse(request: RequestChannel.Request, response: AbstractResponse) {
    requestChannel.sendResponse(RequestChannel.Response(request, response))
  }

  private def authorize(session: Session, operation: Operation, resource: Resource): Boolean =
    authorizer.forall(_.authorize(session, operation, resource))

  private def getTopicMetadata(allowAutoTopicCreation: Boolean, topics: Set[String], listenerName: ListenerName,
                               errorUnavailableEndpoints: Boolean): Seq[MetadataResponse.TopicMetadata] = {
    val topicResponses = metadataCache.getTopicMetadata(topics, listenerName, errorUnavailableEndpoints)
    if (topics.isEmpty || topicResponses.size == topics.size) {
      topicResponses
    } else {
      val nonExistentTopics = topics -- topicResponses.map(_.topic).toSet
      val responsesForNonExistentTopics = nonExistentTopics.map { topic =>
        if (isInternal(topic)) {
          val topicMetadata = createInternalTopic(topic)
          if (topicMetadata.error == Errors.COORDINATOR_NOT_AVAILABLE)
            new MetadataResponse.TopicMetadata(Errors.INVALID_REPLICATION_FACTOR, topic, true, java.util.Collections.emptyList())
          else
            topicMetadata
        } else if (allowAutoTopicCreation && config.autoCreateTopicsEnable) {
          createTopic(topic, config.numPartitions, config.defaultReplicationFactor)
        } else {
          new MetadataResponse.TopicMetadata(Errors.UNKNOWN_TOPIC_OR_PARTITION, topic, false, java.util.Collections.emptyList())
        }
      }
      topicResponses ++ responsesForNonExistentTopics
    }
  }

  private def createInternalTopic(topic: String): MetadataResponse.TopicMetadata = {
    if (topic == null)
      throw new IllegalArgumentException("topic must not be null")

    val aliveBrokers = metadataCache.getAliveBrokers

    topic match {
      case GROUP_METADATA_TOPIC_NAME =>
        if (aliveBrokers.size < config.offsetsTopicReplicationFactor) {
          error(s"Number of alive brokers '${aliveBrokers.size}' does not meet the required replication factor " +
            s"'${config.offsetsTopicReplicationFactor}' for the offsets topic (configured via " +
            s"'${BelinkConfig.OffsetsTopicReplicationFactorProp}'). This error can be ignored if the cluster is starting up " +
            s"and not all brokers are up yet.")
          new MetadataResponse.TopicMetadata(Errors.COORDINATOR_NOT_AVAILABLE, topic, true, java.util.Collections.emptyList())
        } else {
          createTopic(topic, config.offsetsTopicPartitions, config.offsetsTopicReplicationFactor.toInt
         //   ,groupCoordinator.offsetsTopicConfigs
          )
        }
//      case TRANSACTION_STATE_TOPIC_NAME =>
//        if (aliveBrokers.size < config.transactionTopicReplicationFactor) {
//          error(s"Number of alive brokers '${aliveBrokers.size}' does not meet the required replication factor " +
//            s"'${config.transactionTopicReplicationFactor}' for the transactions state topic (configured via " +
//            s"'${BelinkConfig.TransactionsTopicReplicationFactorProp}'). This error can be ignored if the cluster is starting up " +
//            s"and not all brokers are up yet.")
//          new MetadataResponse.TopicMetadata(Errors.COORDINATOR_NOT_AVAILABLE, topic, true, java.util.Collections.emptyList())
//        } else {
//          createTopic(topic, config.transactionTopicPartitions, config.transactionTopicReplicationFactor.toInt,
//            txnCoordinator.transactionTopicConfigs)
//        }
      case _ => throw new IllegalArgumentException(s"Unexpected internal topic name: $topic")
    }
  }

  private def createTopic(topic: String,
                          numPartitions: Int,
                          replicationFactor: Int,
                          properties: Properties = new Properties()): MetadataResponse.TopicMetadata = {
    try {
      AdminUtils.createTopic(topic, numPartitions, replicationFactor, properties, RackAwareMode.Safe)
      info("Auto creation of topic %s with %d partitions and replication factor %d is successful"
        .format(topic, numPartitions, replicationFactor))
      metadataCache.addPartitionInfo(topic, numPartitions)
      new MetadataResponse.TopicMetadata(Errors.LEADER_NOT_AVAILABLE, topic, isInternal(topic),
        java.util.Collections.emptyList())
    } catch {
      case _: TopicExistsException => // let it go, possibly another broker created this topic
        new MetadataResponse.TopicMetadata(Errors.LEADER_NOT_AVAILABLE, topic, isInternal(topic),
          java.util.Collections.emptyList())
      case ex: Throwable  => // Catch all to prevent unhandled errors
        new MetadataResponse.TopicMetadata(Errors.forException(ex), topic, isInternal(topic),
          java.util.Collections.emptyList())
    }
  }

  /**
    * Handle a topic metadata request
    */
  def handleTopicMetadataRequest(request: RequestChannel.Request) {
    val metadataRequest = request.body[MetadataRequest]
    val requestVersion = request.header.apiVersion()

    val topics = metadataRequest.topics.asScala.toSet

    var (authorizedTopics, unauthorizedForDescribeTopics) =
      topics.partition(topic => authorize(request.session, Describe, new Resource(Topic, topic)))

    var unauthorizedForCreateTopics = Set[String]()

    if (authorizedTopics.nonEmpty) {
      val nonExistingTopics = metadataCache.getNonExistingTopics(authorizedTopics)
      if (metadataRequest.allowAutoTopicCreation && config.autoCreateTopicsEnable && nonExistingTopics.nonEmpty) {
        if (!authorize(request.session, Create, Resource.ClusterResource)) {
          authorizedTopics --= nonExistingTopics
          unauthorizedForCreateTopics ++= nonExistingTopics
        }
      }
    }

    val unauthorizedForCreateTopicMetadata = unauthorizedForCreateTopics.map(topic =>
      new MetadataResponse.TopicMetadata(Errors.TOPIC_AUTHORIZATION_FAILED, topic, isInternal(topic),
        java.util.Collections.emptyList()))

    // do not disclose the existence of topics unauthorized for Describe, so we've not even checked if they exist or not
    val unauthorizedForDescribeTopicMetadata =
    // In case of all topics, don't include topics unauthorized for Describe
      if ((requestVersion == 0 && (metadataRequest.topics == null || metadataRequest.topics.isEmpty)) || metadataRequest.isAllTopics)
        Set.empty[MetadataResponse.TopicMetadata]
      else
        unauthorizedForDescribeTopics.map(topic =>
          new MetadataResponse.TopicMetadata(Errors.UNKNOWN_TOPIC_OR_PARTITION, topic, false, java.util.Collections.emptyList()))

    // In version 0, we returned an error when brokers with replicas were unavailable,
    // while in higher versions we simply don't include the broker in the returned broker list
    val errorUnavailableEndpoints = requestVersion == 0
    val topicMetadata =
      if (authorizedTopics.isEmpty)
        Seq.empty[MetadataResponse.TopicMetadata]
      else
        getTopicMetadata(metadataRequest.allowAutoTopicCreation, authorizedTopics, request.listenerName,
          errorUnavailableEndpoints)

    val completeTopicMetadata = topicMetadata ++ unauthorizedForCreateTopicMetadata ++ unauthorizedForDescribeTopicMetadata

    val brokers = metadataCache.getAliveBrokers

    trace("Sending topic metadata %s and brokers %s for correlation id %d to client %s".format(completeTopicMetadata.mkString(","),
      brokers.mkString(","), request.header.correlationId, request.header.clientId))

    sendResponseMaybeThrottle(request, requestThrottleMs =>
      new MetadataResponse(
        requestThrottleMs,
        brokers.map(_.getNode(request.listenerName)).asJava,
        clusterId,
        metadataCache.getControllerId.getOrElse(MetadataResponse.NO_CONTROLLER_ID),
        completeTopicMetadata.asJava
      ))
  }
}
