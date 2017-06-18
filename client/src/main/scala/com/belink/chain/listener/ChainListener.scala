package com.belink.chain.listener

import java.util.concurrent.Executors

import org.hyperledger.fabric.protos.common.common._
import org.hyperledger.fabric.protos.msp.identities.SerializedIdentity
import org.hyperledger.fabric.protos.peer.chaincode.ChaincodeInvocationSpec
import org.hyperledger.fabric.protos.peer.events.Event
import org.hyperledger.fabric.protos.peer.proposal.ChaincodeProposalPayload
import org.hyperledger.fabric.protos.peer.transaction.{ChaincodeActionPayload, Transaction}
import org.hyperledger.fabric.sdk.events._
import org.hyperledger.fabric.sdk.utils.ShutdownableThread

/**
  * Created by goldratio on 18/06/2017.
  */
class MyBlockListenerManager(chainId: String, chainEventQueue: ChainEventQueue) extends ShutdownableThread("block-listener-manager") {
  val pool = Executors.newFixedThreadPool(4)
  /**
    * This method is repeatedly invoked until the thread shuts down or this method throws an exception
    */
  override def doWork(): Unit = while (true) {
    var event: Event = null
    while (event == null) event = chainEventQueue.getNextEvent()
    val block = event.getBlock
    val data = block.getData
    data.data.foreach { db =>
      try {
        val env = Envelope.parseFrom(db.toByteArray)
        val payload = Payload.parseFrom(env.payload.toByteArray)
        val plh = payload.getHeader

        val blockChainID = ChannelHeader.parseFrom(plh.channelHeader.toByteArray).channelId //getChainHeader.chainID

        if (chainId == blockChainID) {
          pool.submit(new Runnable {
            override def run() = {
              val env = Envelope.parseFrom(db.toByteArray)
              val payload = Payload.parseFrom(env.payload.toByteArray)
              val plh = payload.getHeader

              val channelHeader = ChannelHeader.parseFrom(plh.channelHeader.toByteArray)
              val txID = channelHeader.txId
              println("get transactionId:" + txID)

              channelHeader.`type` match {
                case HeaderType.ENDORSER_TRANSACTION.value =>
                  val tx = Transaction.parseFrom(payload.data.toByteArray)
                  val h = SignatureHeader.parseFrom(tx.actions(0).header.toByteArray)
                  val identity = SerializedIdentity.parseFrom(h.creator.toByteArray)
                  val chaincodeActionPayload = ChaincodeActionPayload.parseFrom( tx.actions(0).payload.toByteArray)
                  val cppNoTransient = ChaincodeProposalPayload.parseFrom(chaincodeActionPayload.chaincodeProposalPayload.toByteArray)
                  val pPayl = ChaincodeProposalPayload.parseFrom(cppNoTransient.input.toByteArray)
                  val spec = ChaincodeInvocationSpec.parseFrom(pPayl.toByteArray)
                  println(spec)
                  println(identity.idBytes.toStringUtf8)
                case _ =>
                  println("---------------------")
              }
            }
          })
        }
      } catch {
        case _ =>
      }
    }
  }
}

class ChainListener(url: String) {
  val chainEventQueue = new ChainEventQueue()
  val eventHub = new EventHub(url, None, chainEventQueue)
  val blockListenerManager = new MyBlockListenerManager("event-listener", chainEventQueue)
  val transactionListenerManager = new TransactionListenerManager

  def start() = {
    val blockListener = new BlockListener("block-listener", transactionListenerManager)
    blockListenerManager.start()
    eventHub.connect()
  }

}
