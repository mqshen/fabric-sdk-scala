package org.hyperledger.fabric.sdk.ca

import java.security.PrivateKey


/**
  * Created by goldratio on 17/02/2017.
  */
sealed trait PrivacyLevel
case object Nominal extends PrivacyLevel
case object Anonymous extends PrivacyLevel

class Certificate(cert: Array[Byte], privateKey: PrivateKey, privLevel: PrivacyLevel) {

}
