package org.hyperledger.fabric.sdk

import org.hyperledger.fabric.protos.peer.proposal.SignedProposal
import org.hyperledger.fabric.protos.peer.proposal_response.ProposalResponse
import org.hyperledger.fabric.sdk.communicate.Endpoint
import org.hyperledger.fabric.sdk.exceptions.{ InvalidArgumentException, PeerException }
import org.hyperledger.fabric.sdk.helper.SDKUtil

/**
 * Created by goldratio on 17/02/2017.
 */
case class Peer(name: String, url: String) {
  var chain: Option[Chain] = None
  val endorserClient = new EndorserClient(new Endpoint(url).channelBuilder)

  private[sdk] def setChain(chain: Chain) {
    this.chain = Some(chain)
  }

  def sendProposal(proposal: SignedProposal): ProposalResponse = {
    if (proposal == null) throw new PeerException("Proposal is null")
    if (chain == null) throw new PeerException("Chain is null")
    if (!SDKUtil.checkGrpcUrl(url)) throw new InvalidArgumentException("Bad peer url.")
    endorserClient.sendProposal(proposal)
  }

}
