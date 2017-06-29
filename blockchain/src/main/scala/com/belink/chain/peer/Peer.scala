package com.belink.chain.peer

import com.belink.data.protos.peer.proposal.{ Proposal, ProposalResponse }
import org.hyperledger.fabric.sdk.communicate.Endpoint
import org.hyperledger.fabric.sdk.exceptions.InvalidArgumentException
import org.hyperledger.fabric.sdk.helper.SDKUtil


/**
 * Created by goldratio on 19/06/2017.
 */
case class Peer(name: String, url: String) {
  val endorserClient = new EngineClient(new Endpoint(url).channelBuilder)

  def sendProposal(proposal: Proposal): ProposalResponse = {
    if (proposal == null) throw new Exception("Proposal is null")
    if (!SDKUtil.checkGrpcUrl(url)) throw new InvalidArgumentException("Bad peer url.")
    endorserClient.sendProposal(proposal)
  }

}