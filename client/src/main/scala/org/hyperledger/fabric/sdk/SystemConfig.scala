package org.hyperledger.fabric.sdk

/**
  * Created by goldratio on 17/02/2017.
  */
object SystemConfig {
  val DEFAULT_SECURITY_LEVEL = 256 //TODO make configurable //Right now by default FAB services is using
  val DEFAULT_HASH_ALGORITHM = "SHA2"

  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load

  val CHAIN_NAME = config.getString("fabric.chain.name")

  val PEER_LOCATIONS = config.getStringList("fabric.peers")
  val ORDERER_LOCATIONS = config.getStringList("fabric.orderers")
  val EVENTHUB_LOCATIONS = config.getStringList("fabric.eventhubs")

  val FABRIC_CA_SERVICES_LOCATION = config.getString("fabric.ca.server")

  val CACERTS = config.getStringList("fabric.ca.cacerts")
}
