package com.belink.chain.http

import java.io.IOException

import com.belink.chain.exception.{ ChainViewException, HttpConnectionManager }
import org.apache.http.client.methods.{ CloseableHttpResponse, HttpGet, HttpPost, HttpRequestBase }
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.util.EntityUtils
import org.slf4j.{ Logger, LoggerFactory }

/**
 * Created by goldratio on 24/06/2017.
 */
class HttpClient(httpConnectionManager: HttpConnectionManager) {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def get(url: String) = {
    val httpGet = new HttpGet(url)
    httpGet.addHeader("Connection", "close")
    withContextExecute(httpGet) { response =>
      val entity = response.getEntity()
      if (entity != null) {
        EntityUtils.toString(entity)
      } else
        throw new ChainViewException("链节点通讯故障")
    }
  }

  def post(url: String, postContent: String): String = {
    val httpPost = new HttpPost(url)
    httpPost.addHeader("Connection", "close")
    httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded")
    httpPost.setEntity(new ByteArrayEntity(postContent.toString().getBytes("UTF8")))
    withContextExecute(httpPost) { response =>
      val entity = response.getEntity()
      if (entity != null) {
        EntityUtils.toString(entity)
      } else
        throw new ChainViewException("链节点通讯故障")
    }
  }

  def withContextExecute(request: HttpRequestBase)(op: (CloseableHttpResponse) => String): String = {
    val client = httpConnectionManager.getHttpClient()
    val context = new BasicHttpContext()
    val response = client.execute(request, context)
    try {
      op(response)
    } finally {
      if (response != null) {
        try {
          EntityUtils.consume(response.getEntity())
          response.close()
        } catch {
          case e: IOException =>
            throw new ChainViewException("链节点通讯故障", e)
        }
      }
    }
  }
}
