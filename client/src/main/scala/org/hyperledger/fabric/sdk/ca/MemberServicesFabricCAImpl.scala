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
import org.hyperledger.fabric.sdk.exceptions.{EnrollmentException, RegisterException}
import org.hyperledger.fabric.sdk.{SystemConfig, User}
import org.hyperledger.fabric.sdk.security.CryptoPrimitives
import org.json4s.jackson.Serialization.{read, write}


/**
 * Created by goldratio on 17/02/2017.
 */
object MemberServicesFabricCAImpl {
  val COP_BASE_URL = "/api/v1/cfssl/"
  val COP_ENROLLMENT_URL= COP_BASE_URL + "enroll"
  val COP_REGISTER_URL= COP_BASE_URL + "register"
  val instance = new MemberServicesFabricCAImpl(SystemConfig.FABRIC_CA_SERVICES_LOCATION)
}

case class ServerInfo(CAName: String, CAChain: String)
case class EnrollmentResponse(Cert: String, ServerInfo: ServerInfo)
case class EnrollmentResult(result: EnrollmentResponse, success: Boolean)
case class RegisterCredential(secret: String)
case class RegisterResult(result: RegisterCredential, success: Boolean)

class MemberServicesFabricCAImpl(url: String) extends MemberServices {
  import MemberServicesFabricCAImpl._
  implicit val formats = org.json4s.DefaultFormats

  var cryptoPrimitives = CryptoPrimitives(SystemConfig.DEFAULT_HASH_ALGORITHM, SystemConfig.DEFAULT_SECURITY_LEVEL)

  override def getSecurityLevel: Int = cryptoPrimitives.securityLevel

  override def setSecurityLevel(securityLevel: Int): Unit = cryptoPrimitives.securityLevel = securityLevel

  override def getHashAlgorithm: String = cryptoPrimitives.hashAlgorithm

  override def setHashAlgorithm(hashAlgorithm: String): Unit = cryptoPrimitives.hashAlgorithm = hashAlgorithm

  override def register(req: RegistrationRequest, registrar: User): String = {
    val reqRequest = Map("id" -> req.enrollmentID, "type" -> req.role, "affiliation" -> req.affiliation, "attrs" -> req.attrs)
    val reqBody = write(reqRequest)

    registrar.enrollment.map { enrollment =>
      val cert = new String(Base64.getEncoder.encode(enrollment.cert.getBytes))
      val body = new String(Base64.getEncoder.encode(reqBody.getBytes))
      val bodyAndCert = body + "." + cert

      val ecdsaSignature = instance.cryptoPrimitives.ecdsaSignToBytes(enrollment.key.getPrivate, bodyAndCert.getBytes)
      val b64Sign = new String(Base64.getEncoder.encode(ecdsaSignature))

      val authToken = cert + "." + b64Sign
      val responseBody: String = httpPost(url + COP_REGISTER_URL, reqBody, authToken)

      val jsonResult = read[RegisterResult](responseBody)
      if (!jsonResult.success) {
        throw new RegisterException("COP Failed response success is false. " + jsonResult.result)
      }
      jsonResult.result.secret
    }.getOrElse("")
  }

  override def enroll(req: EnrollmentRequest): Enrollment = {
    val signingKeyPair: KeyPair = cryptoPrimitives.ecdsaKeyGen()
    val user = req.enrollmentID

    val csr: PKCS10CertificationRequest = cryptoPrimitives.generateCertificationRequest(user, signingKeyPair)
    val pem: String = cryptoPrimitives.certificationRequestToPEM(csr)

    val json = Map("certificate_request" -> pem)

    val responseBody: String = httpPost(url + COP_ENROLLMENT_URL, write(json), new UsernamePasswordCredentials(user, req.enrollmentSecret))

    println(responseBody)
    val jsonResult = read[EnrollmentResult](responseBody)
    if (!jsonResult.success) {
      throw new EnrollmentException("COP Failed response success is false. " + jsonResult.result)
    }
    val b64dec: Base64.Decoder = Base64.getDecoder
    val signedPem: String = new String(b64dec.decode(jsonResult.result.Cert.getBytes))
    val caPem = b64dec.decode(jsonResult.result.ServerInfo.CAChain.getBytes)
    println(caPem)

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
    val responseBody: String = if (entity != null) EntityUtils.toString(entity) else ""
    if (status >= 400) {
      val e: Exception = new Exception("POST request to %s failed with status code: %d. Response: %s".format(url, status, responseBody))
      throw e
    }
    responseBody
  }

  def httpPost(url: String, body: String, token: String) = {
    val client: HttpClient = HttpClientBuilder.create.build
    val httpPost: HttpPost = new HttpPost(url)
    httpPost.addHeader("Authorization", token)
    val context: HttpClientContext = HttpClientContext.create
    httpPost.setEntity(new StringEntity(body))
    val response: HttpResponse = client.execute(httpPost, context)
    val status: Int = response.getStatusLine.getStatusCode
    val entity: HttpEntity = response.getEntity
    val responseBody: String = if (entity != null) EntityUtils.toString(entity) else ""
    if (status >= 400) {
      val e: Exception = new Exception("POST request to %s failed with status code: %d. Response: %s".format(url, status, responseBody))
      throw e
    }
    responseBody
  }

  override def getTCertBatch(req: GetTCertBatchRequest): Unit = {

  }

}
