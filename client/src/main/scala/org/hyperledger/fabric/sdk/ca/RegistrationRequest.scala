package org.hyperledger.fabric.sdk.ca

import java.security.KeyPair

/**
  * Created by goldratio on 17/02/2017.
  */
case class RegistrationRequest(enrollmentID: String, roles: Seq[String], affiliation: String)

case class EnrollmentRequest(enrollmentID: String, enrollmentSecret: String)

case class Enrollment(key: KeyPair, cert: String, chainKey: String, publicKey: String) {
  def getMSPID = "DEFAULT" //TODO what will this be ?
}

case class GetTCertBatchRequest(name: String, enrollment: Enrollment, num: Int, attrs: Seq[String])