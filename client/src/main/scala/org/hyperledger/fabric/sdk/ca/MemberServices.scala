package org.hyperledger.fabric.sdk.ca

import org.hyperledger.fabric.sdk.User

/**
  * Created by goldratio on 17/02/2017.
  */
trait MemberServices {

  def getSecurityLevel: Int


  def setSecurityLevel(securityLevel: Int)


  def getHashAlgorithm: String


  def setHashAlgorithm(hashAlgorithm: String)

  def register(req: RegistrationRequest, registrar: User): String


  def enroll(req: EnrollmentRequest): Enrollment


  def getTCertBatch(req: GetTCertBatchRequest)

}
