package org.hyperledger.fabric.sdk.events

import java.util.concurrent.Executors

import common.common.{Block, ChannelHeader, Envelope, Payload}
import org.hyperledger.fabric.sdk.utils.ShutdownableThread
import protos.events.Event

import scala.collection.mutable


/**
  * Created by goldratio on 21/02/2017.
  */
class BlockListener(val name: String, transactionListenerManager: TransactionListenerManager) {

  def received(block: Block): Unit = {
    val data = block.getData

    data.data.foreach { db =>
      val env = Envelope.parseFrom(db.toByteArray)
      val payload = Payload.parseFrom(env.payload.toByteArray)
      val plh = payload.getHeader
      val txID = ChannelHeader.parseFrom(plh.channelHeader.toByteArray).txId
      transactionListenerManager.receive(txID, env)
    }
  }

}

class BlockListenerManager(chainId: String, chainEventQueue: ChainEventQueue) extends ShutdownableThread("block-listener-manager") {
  val pool = Executors.newFixedThreadPool(4)
  val blockListeners  = new mutable.LinkedHashMap[String, BlockListener]

  /**
    * This method is repeatedly invoked until the thread shuts down or this method throws an exception
    */
  override def doWork(): Unit = {
    while(true){
      var event: Event = null
      while(event == null) event = chainEventQueue.getNextEvent()
      val block = event.getBlock
      val data = block.getData
      data.data.foreach { db =>
        try {
          val env = Envelope.parseFrom(db.toByteArray)
          val payload = Payload.parseFrom(env.payload.toByteArray)
          val plh = payload.getHeader

          val blockChainID = ChannelHeader.parseFrom(plh.channelHeader.toByteArray).channelId//getChainHeader.chainID

          if (chainId == blockChainID) {
            val blCopy = new Array[BlockListener](blockListeners.size)
            blockListeners synchronized {
              blockListeners.zipWithIndex.foreach { case (listener, i) =>
                blCopy(i) = listener._2
              }
            }
            blCopy.foreach { bl =>
              pool.submit(new Runnable {
                override def run() = {
                  bl.received(event.getBlock)
                }
              })
            }
          }
        } catch {
          case _ =>
        }
      }
    }
  }

  def registerBlockListener(blockListener: BlockListener) = {
    blockListeners.update(blockListener.name, blockListener)
  }
}
