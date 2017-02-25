package org.hyperledger.fabric.sdk.events

import java.util.concurrent.atomic.AtomicBoolean

import common.common.Envelope
import org.hyperledger.fabric.sdk.utils.ShutdownableThread

import scala.collection.mutable
import scala.concurrent.Promise

/**
  * Created by goldratio on 22/02/2017.
  */
class TransactionListener(val txID: String, promise: Promise[Envelope]) {
  val fired = new AtomicBoolean(false)

  def fire(envelope: Envelope) {
    if (!fired.getAndSet(true) && !promise.isCompleted)
      promise success(envelope)
    //es.execute(() -> future.complete(envelope))
  }
}

class TransactionListenerManager {
  val txListeners  = new mutable.LinkedHashMap[String, mutable.ListBuffer[TransactionListener]]

  def addListener(txListener: TransactionListener) {
    txListeners synchronized {
      val tl = txListeners.get(txListener.txID).getOrElse{
        val list = mutable.ListBuffer.empty[TransactionListener]
        txListeners.update(txListener.txID, list)
        list
      }
      tl += txListener
    }
  }

  def receive(txID: String, envelope: Envelope) {
    var txL:Array[TransactionListener] = Array.empty//new Array[TransactionListener](txListeners.size + 2)
    txListeners synchronized {
      txListeners.get(txID).foreach { list =>
        txL = new Array[TransactionListener](list.size + 2)
        list.zipWithIndex.foreach{case (listener, i) =>
          txL(i) = listener
        }
      }
    }
    txL.foreach { l =>
      try {
        l.fire(envelope)
      } catch {
        case e: Throwable => {
        }
      }
    }
  }
}