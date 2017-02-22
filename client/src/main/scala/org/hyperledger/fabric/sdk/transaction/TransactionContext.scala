package org.hyperledger.fabric.sdk.transaction

import java.time.Instant

import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import org.hyperledger.fabric.sdk.helper.SDKUtil
import org.hyperledger.fabric.sdk.security.CryptoPrimitives
import org.hyperledger.fabric.sdk.{Chain, User}

/**
  * Created by goldratio on 17/02/2017.
  */
object TransactionContext {
  def apply(chain: Chain, user: User, cryptoPrimitives: CryptoPrimitives): TransactionContext =
    new TransactionContext(SDKUtil.generateUUID, chain, user, cryptoPrimitives)
}

class TransactionContext(val transactionID: String, val chain: Chain, user: User, cryptoPrimitives: CryptoPrimitives) {
  var currentTimeStamp: Option[Timestamp] = None

  def getFabricTimestamp: Timestamp = {
    if (!currentTimeStamp.isDefined) {
      val ts = Timestamp(Instant.now.toEpochMilli)
      currentTimeStamp = Some(ts)
    }
    currentTimeStamp.get
  }

  def getCreator() = chain.enrollment.get.cert

  def getMSPID() = chain.enrollment.get.getMSPID

  def getNonce: ByteString = {
    //TODO right now the server does not care need to figure out
    ByteString.copyFromUtf8(SDKUtil.generateUUID)
  }
}
