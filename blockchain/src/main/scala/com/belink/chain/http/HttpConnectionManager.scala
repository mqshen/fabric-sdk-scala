package com.belink.chain.http

import java.security.cert.X509Certificate

import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.ssl.{ SSLContexts, TrustStrategy }

/**
 * Created by goldratio on 7/29/16.
 */
class HttpConnectionManager {

  lazy val normalCm = {
    val cm = new PoolingHttpClientConnectionManager()
    // 设置整个连接池最大连接数
    // 是路由的默认最大连接（该值默认为2），限制数量实际使用DefaultMaxPerRoute并非MaxTotal
    cm.setMaxTotal(100)
    cm.setDefaultMaxPerRoute(100)
    cm
  }

  lazy val trustCm = {
    val socketFactoryRegistry = RegistryBuilder.create[ConnectionSocketFactory]()
      .register(
        "https",
        new SSLConnectionSocketFactory(SSLContexts.custom()
          .loadTrustMaterial(null, new TrustStrategy() {
            override def isTrusted(chain: Array[X509Certificate], authType: String): Boolean = {
              true
            }
          }).build())).build()
    val cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry)
    cm.setMaxTotal(100)
    cm.setDefaultMaxPerRoute(100)
    cm
  }

  def getHttpClient() = {
    HttpClients.custom().setConnectionManager(normalCm).build()
  }

  def getHttpClientInTrust() = {
    HttpClients.custom().setConnectionManager(trustCm).build()
  }

}
