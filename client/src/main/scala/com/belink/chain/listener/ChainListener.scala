package com.belink.chain.listener

import com.belink.chain.peer.Peer
import com.belink.chain.processor.TransactionProcessor
import com.google.protobuf.ByteString
import org.hyperledger.fabric.protos.common.common._
import org.hyperledger.fabric.protos.msp.identities.SerializedIdentity
import org.hyperledger.fabric.protos.peer.chaincode.ChaincodeInvocationSpec
import org.hyperledger.fabric.protos.peer.proposal.ChaincodeProposalPayload
import org.hyperledger.fabric.protos.peer.transaction.{ChaincodeActionPayload, Transaction}
import org.hyperledger.fabric.sdk.User
import org.hyperledger.fabric.sdk.events._

/**
 * Created by goldratio on 18/06/2017.
 */

class MyBlockListener(val name: String, processor: TransactionProcessor) extends BlockListener {

  override def received(block: ByteString): Unit = {
    println("start receive")
    val env = Envelope.parseFrom(block.toByteArray)
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
        val chaincodeActionPayload = ChaincodeActionPayload.parseFrom(tx.actions(0).payload.toByteArray)
        val cppNoTransient = ChaincodeProposalPayload.parseFrom(chaincodeActionPayload.chaincodeProposalPayload.toByteArray)
        val pPayl = ChaincodeProposalPayload.parseFrom(cppNoTransient.input.toByteArray)
        val spec = ChaincodeInvocationSpec.parseFrom(pPayl.toByteArray)
        spec.chaincodeSpec.map { chaincodeSpec =>
          chaincodeSpec.input.map { input =>
            val args = input.args.map(_.toStringUtf8)
            if (args.size < 3)
              return
            args.head match {
              case "auth" =>
                processor.process(identity.idBytes.toStringUtf8, args(1), args(2))
              case x =>
                //TODO print log
                print(x)
            }
          }
          println(spec)
          println(identity.idBytes.toStringUtf8)
        }
      case _ =>
        println("---------------------")
    }
  }

}

class ChainListener(url: String) {
  val chainEventQueue = new ChainEventQueue()
  val eventHub = new EventHub(url, None, chainEventQueue)
  val blockListenerManager = new BlockListenerManager("testchainid", chainEventQueue)

  def start(user: User) = {
    val peer = new Peer("test", "grpc://localhost:7051")
    val processor = new TransactionProcessor(peer)
    val blockListener = new MyBlockListener("block-listen", processor)
    blockListenerManager.registerBlockListener(blockListener)
    blockListenerManager.start()
    eventHub.connect(user.enrollment.get.key.getPrivate, user.enrollment.get.cert)
  }

}
