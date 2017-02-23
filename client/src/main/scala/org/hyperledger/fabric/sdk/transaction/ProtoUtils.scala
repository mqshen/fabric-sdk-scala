package org.hyperledger.fabric.sdk.transaction

import java.security.PrivateKey

import com.google.protobuf.{ByteString, CodedInputStream}
import common.common._
import org.hyperledger.fabric.sdk.ca.MemberServicesFabricCAImpl
import org.hyperledger.fabric.sdk.utils.StringUtil
import protos.proposal.{ChaincodeHeaderExtension, ChaincodeProposalPayload, Proposal}
import protos.proposal_response.Endorsement
import protos.transaction.{ChaincodeActionPayload, ChaincodeEndorsedAction, Transaction, TransactionAction}

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

  def createSignedTx(chaincodeProposal: Proposal, proposalResponsePayload: ChaincodeProposalPayload,
                     endorsements: Seq[Endorsement], privateKey: PrivateKey) = {
    val hdr = Header.parseFrom(chaincodeProposal.header.toByteArray)
    val pPayl = ChaincodeProposalPayload.parseFrom(chaincodeProposal.payload.toByteArray)
    //val shdr = SignatureHeader.parseFrom(hdr.signatureHeader.toByteArray)
    //val chdr = ChannelHeader.parseFrom(hdr.channelHeader.toByteArray)
    //val hdrExt = ChaincodeHeaderExtension.parseFrom(chdr.extension.toByteArray)
    val cea = ChaincodeEndorsedAction(proposalResponsePayload.toByteString, endorsements)
    val cppNoTransient = ChaincodeProposalPayload(pPayl.input)
    println("test1:" + StringUtil.toHexString(cppNoTransient.toByteArray))
    val propPayloadBytes = cppNoTransient.toByteString
    val capBytes = ChaincodeActionPayload(propPayloadBytes, Some(cea)).toByteString
    val transactionAction = TransactionAction(hdr.signatureHeader, capBytes)
    println("test2:" + StringUtil.toHexString(transactionAction.toByteArray))
    val transaction = Transaction(actions = Seq(transactionAction))
    val payl = Payload(Some(hdr), transaction.toByteString)
    println("test3:" + StringUtil.toHexString(payl.toByteArray))
    val sig = MemberServicesFabricCAImpl.instance.cryptoPrimitives.ecdsaSignToBytes(privateKey, payl.toByteArray)
    Envelope(payl.toByteString, ByteString.copyFrom(sig))
  }
}
