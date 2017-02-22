package org.hyperledger.fabric.sdk.ca

import java.security.KeyPair
import java.util.Base64

import org.apache.http.{HttpEntity, HttpHost, HttpResponse}
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.{AuthCache, CredentialsProvider, HttpClient}
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.{BasicAuthCache, BasicCredentialsProvider, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.util.encoders.Hex
import org.hyperledger.fabric.sdk.exceptions.EnrollmentException
import org.hyperledger.fabric.sdk.{SystemConfig, User}
import org.hyperledger.fabric.sdk.security.CryptoPrimitives
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.read

/**
  * Created by goldratio on 17/02/2017.
  */
object MemberServicesFabricCAImpl {
  val COP_BASEPATH = "/api/v1/cfssl/"
  val COP_ENROLLMENBASE = COP_BASEPATH + "enroll"
  val instance = new MemberServicesFabricCAImpl(SystemConfig.FABRIC_CA_SERVICES_LOCATION)
}

case class EnrollResult(result: String, success: Boolean)

class MemberServicesFabricCAImpl(url: String) extends MemberServices {
  import MemberServicesFabricCAImpl._
  implicit val formats = org.json4s.DefaultFormats

  var cryptoPrimitives = CryptoPrimitives(SystemConfig.DEFAULT_HASH_ALGORITHM, SystemConfig.DEFAULT_SECURITY_LEVEL)
  override def getSecurityLevel: Int = cryptoPrimitives.securityLevel

  override def setSecurityLevel(securityLevel: Int): Unit = cryptoPrimitives.securityLevel = securityLevel

  override def getHashAlgorithm: String = cryptoPrimitives.hashAlgorithm

  override def setHashAlgorithm(hashAlgorithm: String): Unit = cryptoPrimitives.hashAlgorithm = hashAlgorithm

  override def register(req: RegistrationRequest, registrar: User): String = {
    //TODO
    ""
  }

  override def enroll(req: EnrollmentRequest): Enrollment = {
    // generate ECDSA keys: signing and encryption keys
    val signingKeyPair: KeyPair = cryptoPrimitives.ecdsaKeyGen()
    //  KeyPair encryptionKeyPair = cryptoPrimitives.ecdsaKeyGen();
    val user = req.enrollmentID

    val csr: PKCS10CertificationRequest = cryptoPrimitives.generateCertificationRequest(user, signingKeyPair)
    val pem: String = cryptoPrimitives.certificationRequestToPEM(csr)


    val json = ("certificate_request" -> pem)

    val responseBody: String = httpPost(url + COP_ENROLLMENBASE, compact(render(json)), new UsernamePasswordCredentials(user, req.enrollmentSecret))

    val jsonResult = read[EnrollResult](responseBody)
    if (!jsonResult.success) {
      val e: EnrollmentException = new EnrollmentException("COP Failed response success is false. " + jsonResult.result, new Exception)
      throw e
    }
    val b64dec: Base64.Decoder = Base64.getDecoder
    val signedPem: String = new String(b64dec.decode(jsonResult.result.getBytes))

    val enrollment: Enrollment = Enrollment(signingKeyPair, signedPem, "", Hex.toHexString(signingKeyPair.getPublic.getEncoded))
    enrollment

  }

  def httpPost(url: String, body: String, credentials: UsernamePasswordCredentials) = {
    val provider: CredentialsProvider = new BasicCredentialsProvider
    provider.setCredentials(AuthScope.ANY, credentials)
    val client: HttpClient = HttpClientBuilder.create.setDefaultCredentialsProvider(provider).build
    val httpPost: HttpPost = new HttpPost(url)
    val authCache: AuthCache = new BasicAuthCache
    val targetHost: HttpHost = new HttpHost(httpPost.getURI.getHost, httpPost.getURI.getPort)
    authCache.put(targetHost, new BasicScheme)
    val context: HttpClientContext = HttpClientContext.create
    context.setCredentialsProvider(provider)
    context.setAuthCache(authCache)
    httpPost.setEntity(new StringEntity(body))
    val response: HttpResponse = client.execute(httpPost, context)
    val status: Int = response.getStatusLine.getStatusCode
    val entity: HttpEntity = response.getEntity
    val responseBody: String = if (entity != null) EntityUtils.toString(entity)
    else null
    if (status >= 400) {
      val e: Exception = new Exception("POST request to %s failed with status code: %d. Response: %s".format(url, status, responseBody))
      throw e
    }
    responseBody
  }

  override def getTCertBatch(req: GetTCertBatchRequest): Unit = {

  }
}
