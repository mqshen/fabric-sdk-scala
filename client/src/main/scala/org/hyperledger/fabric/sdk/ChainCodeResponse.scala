package org.hyperledger.fabric.sdk

/**
 * Created by goldratio on 20/02/2017.
 */
object ChainCodeResponse {
  sealed trait Status
  case object UNDEFINED extends Status
  case object SUCCESS extends Status
  case object FAILURE extends Status

}
class ChainCodeResponse(transactionID: String, chainCodeID: String, iStatus: Int, message: String) {
  import ChainCodeResponse._
  val status = iStatus match {
    case 200 => SUCCESS
    case 200 => FAILURE
    case _   => UNDEFINED
  }

}
