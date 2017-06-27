package belink.security

import java.util.{List, Properties}

import com.ynet.belink.common.config.ConfigDef
import com.ynet.belink.common.config.ConfigDef._
import com.ynet.belink.common.security.authenticator.CredentialCache
import com.ynet.belink.common.security.scram.{ScramCredential, ScramCredentialUtils, ScramMechanism}

/**
  * Created by goldratio on 27/06/2017.
  */
class CredentialProvider(saslEnabledMechanisms: List[String]) {

  val credentialCache = new CredentialCache
  ScramCredentialUtils.createCache(credentialCache, saslEnabledMechanisms)

  def updateCredentials(username: String, config: Properties) {
    for (mechanism <- ScramMechanism.values()) {
      val cache = credentialCache.cache(mechanism.mechanismName, classOf[ScramCredential])
      if (cache != null) {
        config.getProperty(mechanism.mechanismName) match {
          case null => cache.remove(username)
          case c => cache.put(username, ScramCredentialUtils.credentialFromString(c))
        }
      }
    }
  }
}

object CredentialProvider {
  def userCredentialConfigs: ConfigDef = {
    ScramMechanism.values.foldLeft(new ConfigDef) {
      (c, m) => c.define(m.mechanismName, Type.STRING, null, Importance.MEDIUM, s"User credentials for SCRAM mechanism ${m.mechanismName}")
    }
  }
}
