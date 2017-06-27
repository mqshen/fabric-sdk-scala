package com.belink.chain.config

/**
 * Created by goldratio on 24/06/2017.
 */
object SystemConfig {

  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load

  val bubiHost = config.getString("bubi.host")

  val address = config.getString("bubi.address")
}
