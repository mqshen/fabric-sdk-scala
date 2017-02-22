package org.hyperledger.fabric.sdk

import org.hyperledger.fabric.protos.common.common.Envelope
import org.hyperledger.fabric.sdk.communicate.Endpoint

/**
  * Created by goldratio on 17/02/2017.
  */
case class Orderer(url: String, pem: Option[String] = None, var chain: Option[Chain] = None) {

  private[sdk] def setChain(chain: Chain) {
    this.chain = Some(chain)
  }

  def sendTransaction(transaction: Envelope) = {
    val orderClient = new OrdererClient(new Endpoint(url, pem).channelBuilder)
    orderClient.sendTransaction(transaction)
  }
}
