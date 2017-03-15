package org.hyperledger.fabric.sdk.ca

import java.security.KeyPair

import com.google.protobuf.ByteString
import msp.identities.SerializedIdentity

/**
 * Created by goldratio on 17/02/2017.
 */
case class UserAttribute(name: String, value: String)
case class RegistrationRequest(enrollmentID: String, role: String, affiliation: String, attrs: Seq[UserAttribute])

case class EnrollmentRequest(enrollmentID: String, enrollmentSecret: String)

case class Enrollment(key: KeyPair, cert: String, chainKey: String, publicKey: String) {
  def getMSPID = "DEFAULT" //TODO what will this be ?

  def getAddress() = {
    val creator = ByteString.copyFromUtf8(cert)
    val identity = SerializedIdentity(getMSPID, creator).toString
    identity
  }
}

case class GetTCertBatchRequest(name: String, enrollment: Enrollment, num: Int, attrs: Seq[String])