package org.hyperledger.fabric.sdk.events

import io.grpc.stub.StreamObserver
import org.hyperledger.fabric.protos.peer.events._
import org.hyperledger.fabric.sdk.communicate.Endpoint
import org.hyperledger.fabric.sdk.utils.Logging


/**
  * Created by goldratio on 21/02/2017.
  */
class EventHub(url: String, pem: Option[String], eventQueue: ChainEventQueue) extends Logging {
  var connected = false
  val channel = new Endpoint(url, pem).channelBuilder.build
  val events: EventsGrpc.EventsStub = EventsGrpc.stub(channel)
  var sender: StreamObserver[Event] = null

  def connect() {
    if (connected) {
      warn("Event Hub already connected.")
    } else {
      val eventStream = new StreamObserver[Event]() {
        def onNext(event: Event) = {
          eventQueue.addBEvent(event) //add to chain queue
        }

        def onError(t: Throwable) = {
          error("Error in stream: " + t.getMessage)
        }

        def onCompleted() = {
          info("Stream completed")
        }
      }
      sender = events.chat(eventStream)
      blockListener()
      connected = true
    }
  }

  def blockListener() {
    val register = Event.Event.Register(Register(Seq(Interest(EventType.BLOCK))))
    val blockEvent = Event(event = register)
    sender.onNext(blockEvent)
  }
}
