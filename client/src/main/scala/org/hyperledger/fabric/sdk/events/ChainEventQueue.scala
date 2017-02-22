package org.hyperledger.fabric.sdk.events

import java.util.concurrent.LinkedBlockingQueue

import org.hyperledger.fabric.protos.peer.events.{Event, EventType}
import org.hyperledger.fabric.sdk.utils.Logging

/**
  * Created by goldratio on 21/02/2017.
  */
class ChainEventQueue extends Logging {
  private val events = new LinkedBlockingQueue[Event] //Thread safe
  private var previous = Long.MinValue

  def addBEvent(event: Event): Boolean = {
    //For now just support blocks --- other types are also reported as blocks.
    if (!event.event.isBlock) false
    else {
      val block = event.getBlock
      val num = block.getHeader.number
      //If being fed by multiple eventhubs make sure we don't add dups here.
      this synchronized {
        if (num <= previous) false // seen it!
        else {
          previous = num
          events.offer(event)
          true
        }
      }
    }
  }

  def getNextEvent(): Event = {
    try {
      events.take
    } catch {
      case e: InterruptedException => {
        warn(e)
        throw e
        //logger.warn(e)
      }
    }
  }

}
