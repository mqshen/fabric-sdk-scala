package belink.server

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets

/**
  * Created by goldratio on 28/06/2017.
  */
object QuotaId {

  /**
    * Sanitizes user principal to a safe value for use in MetricName
    * and as Zookeeper node name
    */
  def sanitize(user: String): String = {
    val encoded = URLEncoder.encode(user, StandardCharsets.UTF_8.name)
    val builder = new StringBuilder
    for (i <- 0 until encoded.length) {
      encoded.charAt(i) match {
        case '*' => builder.append("%2A") // Metric ObjectName treats * as pattern
        case '+' => builder.append("%20") // Space URL-encoded as +, replace with percent encoding
        case c => builder.append(c)
      }
    }
    builder.toString
  }

  /**
    * Decodes sanitized user principal
    */
  def desanitize(sanitizedUser: String): String = {
    URLDecoder.decode(sanitizedUser, StandardCharsets.UTF_8.name)
  }
}

class ClientQuotaManager {

}

