package org.hyperledger.fabric.sdk

import java.util.concurrent.TimeUnit

import io.grpc.{ManagedChannelBuilder, StatusRuntimeException}
import org.hyperledger.fabric.protos.peer.peer.EndorserGrpc
import org.hyperledger.fabric.protos.peer.proposal.SignedProposal
import org.hyperledger.fabric.protos.peer.proposal_response.ProposalResponse
import org.hyperledger.fabric.sdk.exceptions.PeerException
import org.hyperledger.fabric.sdk.utils.Logging

/**
  * Created by goldratio on 20/02/2017.
  */
class EndorserClient(channelBuilder: ManagedChannelBuilder[_]) extends Logging {
  val channel = channelBuilder.build()
  val blockingStub = EndorserGrpc.blockingStub(channel)

  def shutdown() {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  def sendProposal(proposal: SignedProposal): ProposalResponse = {
    try {
      blockingStub.processProposal(proposal)
    } catch {
      case e: StatusRuntimeException => {
        error("Sending transaction to peer failed", e)
        throw new PeerException("Sending transaction to peer failed", e)
      }
      case e: Throwable =>
        error("Sending transaction to peer failed", e)
        throw new PeerException("Sending transaction to peer failed", e)
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
