package org.hyperledger.fabric.sdk

import org.slf4j.LoggerFactory

/**
 * Created by goldratio on 17/02/2017.
 */
object FabricClient {
  val logger = LoggerFactory.getLogger(getClass)
  val instance = new FabricClient
}

class FabricClient {
  import FabricClient._
  var chains = Map.empty[String, Chain]
  var userContext: Option[User] = None

  def newChain(name: String) = {
    logger.trace("Creating chain :" + name)
    val newChain = Chain(name, this)
    chains = chains + (name -> newChain)
    newChain
  }

  def newPeer(url: String) = {
    Peer("peer", url)
  }

  def newOrderer(url: String) = {
    Orderer(url)
  }

  def getChain(name: String) = chains(name)

}
