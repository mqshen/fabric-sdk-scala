package org.hyperledger.fabric.sdk

import com.google.protobuf.ByteString
import common.common.{ Envelope, Payload, Status }
import org.hyperledger.fabric.sdk.ca.{ Enrollment, MemberServicesFabricCAImpl }
import org.hyperledger.fabric.sdk.chaincode.DeploymentProposalRequest
import org.hyperledger.fabric.sdk.events._
import org.hyperledger.fabric.sdk.exceptions.InvalidArgumentException
import org.hyperledger.fabric.sdk.helper.SDKUtil
import org.hyperledger.fabric.sdk.transaction.{ QueryProposalRequest, _ }
import org.hyperledger.protos.chaincode.ChaincodeID
import protos.proposal.{ Proposal, SignedProposal }

import scala.concurrent.Promise

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

  val chainEventQueue = new ChainEventQueue()
  val blockListenerManager = new BlockListenerManager(name, chainEventQueue)
  val transactionListenerManager = new TransactionListenerManager

  def addPeer(peer: Peer) = {
    if (peer.name.isEmpty) {
      throw new InvalidArgumentException("Peer added to chan has no name.")
    }
    if (!SDKUtil.checkGrpcUrl(peer.url)) {
      throw new InvalidArgumentException("Peer added to chan has invalid url.")
    }
    peer.setChain(this)
    this.peers = peers :+ peer
    this
  }

  def addOrderer(orderer: Orderer) = {
    if (!SDKUtil.checkGrpcUrl(orderer.url)) {
      throw new InvalidArgumentException("Peer added to chan has invalid url.")
    }
    orderer.setChain(this)
    this.orderers = orderers :+ orderer
    this
  }

  def addEventHub(url: String) = {
    if (!SDKUtil.checkGrpcUrl(url)) {
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


  def sendDeploymentProposal(proposalRequest: DeploymentProposalRequest, user: User) = {
    user.enrollment.map { enrollment =>
      val transactionContext = TransactionContext(this, user, MemberServicesFabricCAImpl.instance.cryptoPrimitives)
      proposalRequest.context = Some(transactionContext)
      val (deploymentProposal, txID) = proposalRequest.toProposal()
      val signedProposal = getSignedProposal(deploymentProposal, enrollment)
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
  }

  def getSignedProposal(deploymentProposal: Proposal, enrollment: Enrollment) = {
    val ecdsaSignature: Array[Byte] = MemberServicesFabricCAImpl.instance.cryptoPrimitives.ecdsaSignToBytes(enrollment.key.getPrivate, deploymentProposal.toByteArray)
    SignedProposal(deploymentProposal.toByteString, ByteString.copyFrom(ecdsaSignature))
  }

  def sendTransaction(proposalResponses: Seq[MyProposalResponse], user: User) = {
    if (proposalResponses.size == 0) throw new InvalidArgumentException("sendTransaction proposalResponses size is 0")
    val endorsements = proposalResponses.map(_.proposalResponse.endorsement.get)
    val arg = proposalResponses.head
    val proposal = arg.proposal
    val proposalResponsePayload = arg.proposalResponse.payload
    val proposalTransactionID = arg.transactionID
    user.enrollment.map { enrollment =>
      val transactionContext = TransactionContext(this, user, MemberServicesFabricCAImpl.instance.cryptoPrimitives)

      val transactionEnvelope = ProtoUtils.createSignedTx(proposal, proposalResponsePayload, endorsements, enrollment.key.getPrivate, transactionContext)
      //val transactionEnvelope = createTransactionEnvelop(transactionPayload)
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

  def createTransactionEnvelop(transactionPayload: Payload, enrollment: Enrollment): Envelope = {
    val ecdsaSignature = MemberServicesFabricCAImpl.instance.cryptoPrimitives.ecdsaSignToBytes(enrollment.key.getPrivate, transactionPayload.toByteArray)
    val ceb = Envelope(transactionPayload.toByteString, ByteString.copyFrom(ecdsaSignature))
    ceb
  }

  def registerTxListener(txid: String) = {
    val promise = Promise[Envelope]()
    val tl = new TransactionListener(txid, promise)
    transactionListenerManager.addListener(tl)
    promise
  }

  def sendQueryProposal(queryProposalRequest: QueryProposalRequest, user: User) = {
    sendProposal(queryProposalRequest, queryProposalRequest.chaincodeID, user)
  }

  def sendInvokeProposal(invokeProposalRequest: InvokeProposalRequest, user: User) = {
    sendProposal(invokeProposalRequest, invokeProposalRequest.chaincodeID, user)
  }

  def sendProposal(transactionRequest: TransactionRequest, chaincodeID: ChaincodeID, user: User) = user.enrollment.map { enrollment =>
    val transactionContext = new TransactionContext(SDKUtil.generateUUID, this, user, MemberServicesFabricCAImpl.instance.cryptoPrimitives)
    val argList = (Seq(transactionRequest.fcn) ++ transactionRequest.args).map(x => ByteString.copyFrom(x.getBytes))
    transactionRequest.context = Some(transactionContext)
    val (proposal, txID) = transactionRequest.createFabricProposal(name, chaincodeID, argList)
    val invokeProposal = getSignedProposal(proposal, enrollment)
    peers.map { peer =>
      val fabricResponse = peer.sendProposal(invokeProposal)
      val proposalResponse = new MyProposalResponse(transactionContext.transactionID, name, fabricResponse.response.get.status,
        fabricResponse.response.get.message, fabricResponse, invokeProposal)
      proposalResponse.verify()
      proposalResponse
    }
  }
}
