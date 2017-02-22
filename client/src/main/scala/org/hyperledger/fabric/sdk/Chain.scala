package org.hyperledger.fabric.sdk

import com.google.protobuf.ByteString
import org.hyperledger.fabric.protos.common.common.{Block, Envelope, Payload, Status}
import org.hyperledger.fabric.protos.peer.chaincode.ChaincodeID
import org.hyperledger.fabric.protos.peer.proposal.{ChaincodeProposalPayload, Proposal, SignedProposal}
import org.hyperledger.fabric.sdk.ca.{Enrollment, MemberServicesFabricCAImpl}
import org.hyperledger.fabric.sdk.chaincode.DeploymentProposalRequest
import org.hyperledger.fabric.sdk.events._
import org.hyperledger.fabric.sdk.exceptions.InvalidArgumentException
import org.hyperledger.fabric.sdk.helper.SDKUtil
import org.hyperledger.fabric.sdk.transaction.{QueryProposalRequest, _}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}


/**
  * Created by goldratio on 17/02/2017.
  */
object Chain {

  def apply(name: String, clientContext: FabricClient): Chain = new Chain(name, clientContext)

}

class Chain(val name: String, clientContext: FabricClient) {
  var peers = Seq.empty[Peer]
  var orderers = Seq.empty[Orderer]
  var eventHubs = Seq.empty[EventHub]
  var enrollment: Option[Enrollment] = None

  val members = mutable.Map.empty[String, User]
  val chainEventQueue = new ChainEventQueue()
  val blockListenerManager = new BlockListenerManager(name, chainEventQueue)
  val transactionListenerManager = new TransactionListenerManager

  def addPeer(peer: Peer) = {
    if (peer.name.isEmpty) {
      throw new InvalidArgumentException("Peer added to chan has no name.")
    }
    if(!SDKUtil.checkGrpcUrl(peer.url)) {
      throw new InvalidArgumentException("Peer added to chan has invalid url.")
    }
    peer.setChain(this)
    this.peers = peers :+ peer
    this
  }

  def addOrderer(orderer: Orderer) = {
    if(!SDKUtil.checkGrpcUrl(orderer.url)) {
      throw new InvalidArgumentException("Peer added to chan has invalid url.")
    }
    orderer.setChain(this)
    this.orderers = orderers :+ orderer
    this
  }

  def addEventHub(url: String) = {
    if(!SDKUtil.checkGrpcUrl(url)) {
      throw new InvalidArgumentException("Peer added to chan has invalid url.")
    }
    val eventHub = new EventHub(url, None, chainEventQueue)
    this.eventHubs = eventHubs :+ eventHub
    this
  }

  def initialize() = {
    //TODO for multi chain
    if (peers.size == 0) {
      // assume this makes no sense.  have no orders seems reasonable if all you do is query.
      throw new InvalidArgumentException("Chain needs at least one peer.")
    }
    runEventQueue()
    eventHubs.foreach(_.connect())
    registerBlockListener()
  }

  def runEventQueue() = {
    blockListenerManager.start()
  }


  def registerBlockListener() = {
    val blockListener = new BlockListener("block-listener", transactionListenerManager)
    blockListenerManager.registerBlockListener(blockListener)
  }


  def enroll(name: String, secret: String) = {
    val user: User = getMember(name)
    if (!user.isEnrolled) {
      user.enroll(secret)
    }
    enrollment = user.enrollment
    members.put(name, user)
    user
  }

  def getMember(name: String) = {
    members.get(name) match {
      case Some(user) => user
      case _ =>
        val user = new User(name, this)
        user
    }
  }

  def sendDeploymentProposal(proposalRequest: DeploymentProposalRequest) = FabricClient.instance.userContext.map { userContext =>
    val transactionContext = TransactionContext(this, userContext, MemberServicesFabricCAImpl.instance.cryptoPrimitives)
    proposalRequest.context = Some(transactionContext)
    val (deploymentProposal, txID) = proposalRequest.toProposal()
    val signedProposal = getSignedProposal(deploymentProposal)
    val proposalResponses = peers.map { peer =>
      val fabricResponse = peer.sendProposal(signedProposal)
      val proposalResponse = new MyProposalResponse(txID,
        transactionContext.chain.name, fabricResponse.response.get.status,
        fabricResponse.response.get.message, fabricResponse, signedProposal)
      proposalResponse.verify()
      proposalResponse
    }
    proposalResponses
  }

  def getSignedProposal(deploymentProposal: Proposal) = {
    val ecdsaSignature: Array[Byte] = MemberServicesFabricCAImpl.instance.cryptoPrimitives.ecdsaSignToBytes(enrollment.get.key.getPrivate, deploymentProposal.toByteArray)
    SignedProposal(deploymentProposal.toByteString, ByteString.copyFrom(ecdsaSignature))
  }

  def sendTransaction(proposalResponses: Seq[MyProposalResponse]) = {
    if(proposalResponses.size == 0) throw new InvalidArgumentException("sendTransaction proposalResponses size is 0")
    val endorsements = proposalResponses.map(_.proposalResponse.endorsement.get)
    val arg = proposalResponses.head
    val proposal = arg.proposal
    val proposalResponsePayload = ChaincodeProposalPayload.parseFrom(arg.proposalResponse.payload.toByteArray)
    val proposalTransactionID = arg.transactionID
    FabricClient.instance.userContext.map { userContext =>
      val transactionPayload = ProtoUtils.payload(proposal, proposalResponsePayload, endorsements)
      val transactionEnvelope = createTransactionEnvelop(transactionPayload)
      val promise = registerTxListener(proposalTransactionID)
      var success = false
      orderers.takeWhile(_ => !success).foreach { orderer =>
        val resp = orderer.sendTransaction(transactionEnvelope)
        if (resp.status == Status.SUCCESS) {
          success = true
        }
      }
      promise
    }
  }



  def createTransactionEnvelop(transactionPayload: Payload): Envelope = {
    val ecdsaSignature = MemberServicesFabricCAImpl.instance.cryptoPrimitives.ecdsaSignToBytes(enrollment.get.key.getPrivate, transactionPayload.toByteArray)
    val ceb = Envelope(transactionPayload.toByteString, ByteString.copyFrom(ecdsaSignature))
    ceb
  }

  def registerTxListener(txid: String) = {
    val promise = Promise[Envelope]()
    new TransactionListener(txid, promise)
    promise
  }

  def sendQueryProposal(queryProposalRequest: QueryProposalRequest) = {
    sendProposal(queryProposalRequest, queryProposalRequest.chaincodeID)
  }

  def sendInvokeProposal(invokeProposalRequest: InvokeProposalRequest) = {
    sendProposal(invokeProposalRequest, invokeProposalRequest.chaincodeID)
  }

  def sendProposal(transactionRequest: TransactionRequest, chaincodeID: ChaincodeID) = FabricClient.instance.userContext.map { userContext =>
    val transactionContext = new TransactionContext(SDKUtil.generateUUID, this, userContext, MemberServicesFabricCAImpl.instance.cryptoPrimitives)
    val argList = (Seq(transactionRequest.fcn) ++ transactionRequest.args).map (x => ByteString.copyFrom(x.getBytes) )
    val (proposal, txID) = transactionRequest.createFabricProposal(name, chaincodeID, argList)
    val invokeProposal = getSignedProposal(proposal)
    peers.map { peer =>
      val fabricResponse = peer.sendProposal(invokeProposal)
      val proposalResponse = new MyProposalResponse(transactionContext.transactionID, name, fabricResponse.response.get.status,
        fabricResponse.response.get.message, fabricResponse, invokeProposal)
      proposalResponse.verify()
      proposalResponse
    }
  }
}
