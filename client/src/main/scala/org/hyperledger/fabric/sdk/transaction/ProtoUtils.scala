package org.hyperledger.fabric.sdk.transaction

import java.security.PrivateKey

import com.google.protobuf.{ ByteString, CodedInputStream }
import org.hyperledger.fabric.protos.common.common._
import org.hyperledger.fabric.protos.msp.identities.SerializedIdentity
import org.hyperledger.fabric.protos.peer.proposal.{ ChaincodeHeaderExtension, ChaincodeProposalPayload, Proposal }
import org.hyperledger.fabric.protos.peer.proposal_response.Endorsement
import org.hyperledger.fabric.protos.peer.transaction.{ ChaincodeActionPayload, ChaincodeEndorsedAction, Transaction, TransactionAction }
import org.hyperledger.fabric.sdk.ca.MemberServicesFabricCAImpl

/**
 * Created by goldratio on 17/02/2017.
 */
object ProtoUtils {

  def createChannelHeader(`type`: HeaderType, txID: String, chainID: String, epoch: Long,
                          chaincodeHeaderExtension: ChaincodeHeaderExtension): ChannelHeader = {
    ChannelHeader(`type`.value, 0, None, chainID, txID, epoch, chaincodeHeaderExtension.toByteString)
  }

  def payload(chaincodeProposal: Proposal, proposalResponsePayload: ChaincodeProposalPayload, endorsements: Seq[Endorsement]) = {
    val header = Header.parseFrom(chaincodeProposal.header.toByteArray)
    //val chdr = ChannelHeader.parseFrom(header.channelHeader.toByteArray)
    //val shdr = SignatureHeader.parseFrom(header.signatureHeader.toByteArray)

    val chaincodeEndorsedAction = ChaincodeEndorsedAction(proposalResponsePayload.toByteString, endorsements)

    val chainCodeProposalPayload = ChaincodeProposalPayload().mergeFrom(CodedInputStream.newInstance(chaincodeProposal.payload.toByteArray))
    val bhash = MemberServicesFabricCAImpl.instance.cryptoPrimitives.hash(chainCodeProposalPayload.toByteArray)
    val chaincodeActionPayload = ChaincodeActionPayload(ByteString.copyFrom(bhash), Some(chaincodeEndorsedAction))

    val transactionAction = TransactionAction(header.signatureHeader, chaincodeActionPayload.toByteString)
    val transaction = Transaction(actions = Seq(transactionAction))
    Payload(Some(header), transaction.toByteString)
  }

  def createSignedTx(chaincodeProposal: Proposal, proposalResponsePayload: ByteString,
                     endorsements: Seq[Endorsement], privateKey: PrivateKey, context: TransactionContext) = {
    val hdr = Header.parseFrom(chaincodeProposal.header.toByteArray)
    val pPayl = ChaincodeProposalPayload.parseFrom(chaincodeProposal.payload.toByteArray)
    //val shdr = SignatureHeader.parseFrom(hdr.signatureHeader.toByteArray)
    //val chdr = ChannelHeader.parseFrom(hdr.channelHeader.toByteArray)
    //val hdrExt = ChaincodeHeaderExtension.parseFrom(chdr.extension.toByteArray)
    val creator = ByteString.copyFromUtf8(context.getCreator)
    val orderSignatureHeader = SignatureHeader.parseFrom(hdr.signatureHeader.toByteArray)
    val nonce = orderSignatureHeader.nonce
    val identity = SerializedIdentity(context.getMSPID(), creator).toByteString
    val signatureHeader = SignatureHeader(identity, nonce)

    val cea = ChaincodeEndorsedAction(proposalResponsePayload, endorsements)
    val cppNoTransient = ChaincodeProposalPayload(pPayl.input)
    val propPayloadBytes = cppNoTransient.toByteString
    val capBytes = ChaincodeActionPayload(propPayloadBytes, Some(cea)).toByteString
    val transactionAction = TransactionAction(signatureHeader.toByteString, capBytes)
    val transaction = Transaction(actions = Seq(transactionAction))
    val payl = Payload(Some(hdr), transaction.toByteString)
    val sig = MemberServicesFabricCAImpl.instance.cryptoPrimitives.ecdsaSignToBytes(privateKey, payl.toByteArray)
    Envelope(payl.toByteString, ByteString.copyFrom(sig))
  }
}
