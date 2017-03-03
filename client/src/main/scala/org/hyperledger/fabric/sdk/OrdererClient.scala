package org.hyperledger.fabric.sdk

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import common.common.Envelope
import io.grpc.stub.StreamObserver
import io.grpc.ManagedChannelBuilder
import orderer.ab.{ AtomicBroadcastGrpc, BroadcastResponse }
import org.hyperledger.fabric.sdk.exceptions.OrdererException
import org.hyperledger.fabric.sdk.utils.Logging
import protos.peer.EndorserGrpc

/**
 * Created by goldratio on 21/02/2017.
 */

class OrdererClient(channelBuilder: ManagedChannelBuilder[_]) extends Logging {
  val channel = channelBuilder.build()
  val blockingStub = EndorserGrpc.blockingStub(channel)

  def shutdown() {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  def sendTransaction(envelope: Envelope): BroadcastResponse = {
    val finishLatch = new CountDownLatch(1)
    val broadcast = AtomicBroadcastGrpc.stub(channel)
    val bsc = AtomicBroadcastGrpc.blockingStub(channel)
    bsc.withDeadlineAfter(2, TimeUnit.MINUTES)
    var ret: Option[BroadcastResponse] = None
    val so = new StreamObserver[BroadcastResponse]() {
      def onNext(resp: BroadcastResponse) {
        // logger.info("Got Broadcast response: " + resp);
        debug("resp status value: " + resp.status.index + ", resp: " + resp.status)
        ret = Some(resp)
        finishLatch.countDown()
      }

      def onError(t: Throwable) {
        error("broadcast error " + t)
        finishLatch.countDown()
      }
      def onCompleted() {
        debug("onCompleted")
        finishLatch.countDown()
      }
    }
    val nso = broadcast.broadcast(so)
    nso.onNext(envelope)
    //nso.onCompleted();
    try {
      finishLatch.await(2, TimeUnit.MINUTES)
      debug("Done waiting for reply! Got:" + ret)
    } catch {
      case e: InterruptedException => {
        error(e)
      }
    }
    ret.getOrElse {
      throw new OrdererException("failed to connect to orderer")
    }
  }

  override def finalize() {
    try {
      shutdown()
    } catch {
      case e: InterruptedException => {
        debug("Failed to shutdown the PeerClient")
      }
    }
  }
}