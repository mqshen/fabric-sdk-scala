package org.hyperledger.fabric.sdk.transaction

import com.google.protobuf.{ByteString, CodedInputStream}
import org.hyperledger.fabric.protos.common.common.{ChainHeader, Header, HeaderType, Payload}
import org.hyperledger.fabric.protos.peer.proposal.{ChaincodeHeaderExtension, ChaincodeProposalPayload, Proposal}
import org.hyperledger.fabric.protos.peer.proposal_response.Endorsement
import org.hyperledger.fabric.protos.peer.transaction.{ChaincodeActionPayload, ChaincodeEndorsedAction, Transaction, TransactionAction}
import org.hyperledger.fabric.sdk.ca.MemberServicesFabricCAImpl

/**
  * Created by goldratio on 17/02/2017.
  */
object ProtoUtils {

  def createChainHeader(`type`: HeaderType, txID: String, chainID: String, epoch: Long,
                        chaincodeHeaderExtension: ChaincodeHeaderExtension): ChainHeader = {
    ChainHeader(`type`.index, 0, None, chainID, txID, epoch, chaincodeHeaderExtension.toByteString)
  }

  def payload(chaincodeProposal: Proposal, proposalResponsePayload: ChaincodeProposalPayload, endorsements: Seq[Endorsement]) = {
    val chaincodeEndorsedAction = ChaincodeEndorsedAction(proposalResponsePayload.toByteString, endorsements)

    val chainCodeProposalPayload = ChaincodeProposalPayload().mergeFrom(CodedInputStream.newInstance(chaincodeProposal.payload.toByteArray))
    val bhash = MemberServicesFabricCAImpl.instance.cryptoPrimitives.hash(chainCodeProposalPayload.toByteArray)
    val chaincodeActionPayload = ChaincodeActionPayload(ByteString.copyFrom(bhash), Some(chaincodeEndorsedAction))

    val header = Header.parseFrom(chaincodeProposal.header.toByteArray)
    val transactionAction = TransactionAction(header.signatureHeader.get.toByteString, chaincodeActionPayload.toByteString)
    val transaction = Transaction(actions = Seq(transactionAction))
    Payload(Some(header), transaction.toByteString)
  }
}
