package com.belink.chain.peer

import java.util.concurrent.TimeUnit

import com.belink.data.protos.peer.peer.EngineGrpc
import com.belink.data.protos.peer.proposal.{ Proposal, ProposalResponse }
import io.grpc.{ ManagedChannelBuilder, StatusRuntimeException }

/**
 * Created by goldratio on 19/06/2017.
 */
class EngineClient(channelBuilder: ManagedChannelBuilder[_]) {

  val channel = channelBuilder.build()
  val blockingStub = EngineGrpc.blockingStub(channel)

  def shutdown() {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  def sendProposal(proposal: Proposal): ProposalResponse = {
    try {
      blockingStub.processProposal(proposal)
    } catch {
      case e: StatusRuntimeException => {
        println("start" + e.getStatus)
        throw new Exception("Sending transaction to peer failed", e)
      }
      case e: Throwable =>
        throw new Exception("Sending transaction to peer failed", e)
    }
  }

  override def finalize() {
    try {
      shutdown()
    } catch {
      case e: InterruptedException => {
        println("Failed to shutdown the PeerClient")
      }
    }
  }
}
