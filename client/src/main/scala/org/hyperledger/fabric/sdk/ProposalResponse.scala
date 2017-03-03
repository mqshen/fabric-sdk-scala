package org.hyperledger.fabric.sdk

import com.google.protobuf.InvalidProtocolBufferException
import msp.identities.SerializedIdentity
import org.hyperledger.fabric.sdk.ca.MemberServicesFabricCAImpl
import org.hyperledger.protos.chaincode.{ ChaincodeDeploymentSpec, ChaincodeID, ChaincodeInvocationSpec }
import protos.proposal.{ ChaincodeProposalPayload, Proposal, SignedProposal }
import protos.proposal_response.ProposalResponse

/**
 * Created by goldratio on 20/02/2017.
 */
class MyProposalResponse(val transactionID: String, val chainCodeID: String, status: Int, val message: String,
                         val proposalResponse: ProposalResponse, signedProposal: SignedProposal)
    extends ChainCodeResponse(transactionID, chainCodeID, status, message) {
  var isVerified = false
  val endorsement = proposalResponse.getEndorsement
  val proposal = Proposal.parseFrom(signedProposal.proposalBytes.toByteArray)

  def verify() = {
    if (isVerified)
      isVerified
    else {
      val sig = this.endorsement.signature
      try {
        val endorser = SerializedIdentity.parseFrom(this.endorsement.endorser.toByteArray)
        // TODO check chain of trust. Need to handle CA certs somewhere
        val plainText = proposalResponse.payload.concat(endorsement.endorser)
        this.isVerified = MemberServicesFabricCAImpl.instance.cryptoPrimitives.verify(plainText.toByteArray, sig.toByteArray, endorser.idBytes.toByteArray)
      } catch {
        case e: InvalidProtocolBufferException => {
          this.isVerified = false
        }
      }
      this.isVerified
    }
  }

  def getChainCodeID: ChaincodeID = {
    val ppl = ChaincodeProposalPayload.parseFrom(proposal.payload.toByteArray)
    val ccis = ChaincodeInvocationSpec.parseFrom(ppl.input.toByteArray)
    val scs = ccis.getChaincodeSpec
    val cci = scs.getInput
    val deps = cci.args(2)
    val chaincodeDeploymentSpec = ChaincodeDeploymentSpec.parseFrom(deps.toByteArray)
    chaincodeDeploymentSpec.chaincodeSpec.get.chaincodeId.get
  }

}
