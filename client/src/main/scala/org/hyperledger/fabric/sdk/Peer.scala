package org.hyperledger.fabric.sdk

import org.hyperledger.fabric.sdk.communicate.Endpoint
import org.hyperledger.fabric.sdk.exceptions.{InvalidArgumentException, PeerException}
import org.hyperledger.fabric.sdk.helper.SDKUtil
import protos.proposal.SignedProposal
import protos.proposal_response.ProposalResponse

/**
  * Created by goldratio on 17/02/2017.
  */
case class Peer(name: String, url: String) {
  var chain: Option[Chain] = None
  val endorserClent = new EndorserClient(new Endpoint(url).channelBuilder)

  private[sdk] def setChain(chain: Chain) {
    this.chain = Some(chain)
  }

  def sendProposal(proposal: SignedProposal): ProposalResponse = {
    if (proposal == null) throw new PeerException("Proposal is null")
    if (chain == null) throw new PeerException("Chain is null")
    if (!SDKUtil.checkGrpcUrl(url)) throw new InvalidArgumentException("Bad peer url.")
    endorserClent.sendProposal(proposal)
  }

}
