package org.hyperledger.fabric.sdk.transaction

import com.google.protobuf.ByteString
import common.common.{Header, HeaderType, SignatureHeader}
import msp.identities.SerializedIdentity
import org.hyperledger.fabric.sdk.ca.{Certificate, MemberServicesFabricCAImpl}
import org.hyperledger.fabric.sdk.utils.StringUtil
import org.hyperledger.protos.chaincode.{ChaincodeID, ChaincodeInput, ChaincodeInvocationSpec, ChaincodeSpec}
import protos.proposal.{ChaincodeHeaderExtension, ChaincodeProposalPayload, Proposal}

/**
  * Created by goldratio on 17/02/2017.
  */

sealed trait Type
case object GO_LANG extends Type

case class TransactionRequest(chaincodePath: String, chaincodeName: String,
                              fcn: String, args: Seq[String], userCert: Option[Certificate],
                              metadata: Array[Byte], confidential: Boolean = false, chaincodeLanguage: Type = GO_LANG) {

  var context: Option[TransactionContext] = None

  def createFabricProposal(chainID: String, chaincodeID: ChaincodeID, argList: Seq[ByteString]) = {
    val chaincodeInvocationSpec = createChaincodeInvocationSpec(chaincodeID, ccType, argList)
    val chaincodeHeaderExtension: ChaincodeHeaderExtension = ChaincodeHeaderExtension(ByteString.EMPTY, Some(chaincodeID))

    println("test test" + StringUtil.toHexString(chaincodeInvocationSpec.toByteArray))

    val creator = ByteString.copyFromUtf8(context.get.getCreator)
    val identity = SerializedIdentity(context.get.getMSPID(), creator).toByteString
    val nonce = context.get.getNonce
    println(StringUtil.toHexString(nonce.toByteArray))
    println(StringUtil.toHexString(identity.toByteArray))
    println(StringUtil.toHexString(nonce.concat(identity).toByteArray))
    val txID = MemberServicesFabricCAImpl.instance.cryptoPrimitives.hash(nonce.concat(identity).toByteArray)
    val txIDHex = StringUtil.toHexString(txID)

    val channelHeader = ProtoUtils.createChannelHeader(HeaderType.ENDORSER_TRANSACTION, txIDHex, chainID, 0, chaincodeHeaderExtension)
    //val sigHeaderBldr: SignatureHeader.Builder = SignatureHeader
    val signatureHeader = SignatureHeader(identity, nonce)


    val header = Header(channelHeader.toByteString, signatureHeader.toByteString)

    val payload = ChaincodeProposalPayload(chaincodeInvocationSpec.toByteString)
    (Proposal(header.toByteString, payload.toByteString), txIDHex)
  }


  def createChaincodeInvocationSpec(chainCodeId: ChaincodeID, langType: ChaincodeSpec.Type, args: Seq[ByteString]) = {
    val chaincodeInput = ChaincodeInput(args)
    val chaincodeSpec = ChaincodeSpec(langType, Some(chainCodeId), Some(chaincodeInput))
    ChaincodeInvocationSpec(Some(chaincodeSpec))
  }

  lazy val ccType = chaincodeLanguage match {
    case GO_LANG =>
      ChaincodeSpec.Type.GOLANG
    case _ =>
      ChaincodeSpec.Type.JAVA
  }
}
