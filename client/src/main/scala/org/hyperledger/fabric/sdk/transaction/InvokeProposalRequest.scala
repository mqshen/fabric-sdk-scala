package org.hyperledger.fabric.sdk.transaction

import org.hyperledger.protos.chaincode.ChaincodeID


/**
  * Created by goldratio on 21/02/2017.
  */
class InvokeProposalRequest(chaincodePath: String, chaincodeName: String, val chaincodeID: ChaincodeID,
                            fcn: String, args: Seq[String]) extends
  TransactionRequest(chaincodePath, chaincodeName, fcn, args, None, Array.empty) {


}
