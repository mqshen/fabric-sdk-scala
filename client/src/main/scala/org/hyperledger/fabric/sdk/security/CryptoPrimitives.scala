package org.hyperledger.fabric.sdk.security

import java.io.{BufferedInputStream, ByteArrayInputStream, ByteArrayOutputStream, StringWriter}
import java.math.BigInteger
import java.security.interfaces.ECPrivateKey
import java.security._
import java.security.cert.{Certificate => _, _}
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

import io.netty.util.internal.StringUtil
import org.bouncycastle.asn1.nist.NISTNamedCurves
import org.bouncycastle.asn1.{ASN1Integer, DERSequenceGenerator}
import org.bouncycastle.crypto.digests.{SHA256Digest, SHA3Digest}
import org.bouncycastle.crypto.params.{ECDomainParameters, ECPrivateKeyParameters}
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.{PKCS10CertificationRequest, PKCS10CertificationRequestBuilder}
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.hyperledger.fabric.sdk.SystemConfig
import org.hyperledger.fabric.sdk.exceptions.CryptoException
import org.hyperledger.fabric.sdk.helper.SDKUtil

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

/**
  * Created by goldratio on 17/02/2017.
  */
object CryptoPrimitives {
  val SECURITY_PROVIDER: String = BouncyCastleProvider.PROVIDER_NAME
  val ASYMMETRIC_KEY_TYPE: String = "EC"
  val KEY_AGREEMENT_ALGORITHM: String = "ECDH"
  val SYMMETRIC_KEY_TYPE: String = "AES"
  val SYMMETRIC_KEY_BYTE_COUNT: Int = 32
  val SYMMETRIC_ALGORITHM: String = "AES/CFB/NoPadding"
  val MAC_KEY_BYTE_COUNT: Int = 32
  // TODO most of these config values should come from genesis block or config
  // file
  val CERTIFICATE_FORMAT = "X.509"
  val DEFAULT_SIGNATURE_ALGORITHM = "SHA3-256WITHECDSA"

  def apply( hashAlgorithm: String , securityLevel: Int ): CryptoPrimitives = {
    val cryptoPrimitives = new CryptoPrimitives( hashAlgorithm, securityLevel )
    cryptoPrimitives.init()
    cryptoPrimitives
  }
}

class CryptoPrimitives(var hashAlgorithm: String, var securityLevel: Int) {
  import CryptoPrimitives._

  Security.addProvider(new BouncyCastleProvider)
//
//
//  Security.getProviders().map { provider =>
//    provider.getServices.filter(_.getType == "Signature").map { al =>
//      println(al.getAlgorithm)
//    }
//  }

  var curveName: String = ""

  def init() {
    if (securityLevel != 256 && securityLevel != 384)
      throw new RuntimeException("Illegal level: " + securityLevel + " must be either 256 or 384")
    if (StringUtil.isNullOrEmpty(this.hashAlgorithm) ||
      !(this.hashAlgorithm.equalsIgnoreCase("SHA2") || this.hashAlgorithm.equalsIgnoreCase("SHA3")))
      throw new RuntimeException("Illegal Hash function family: " + this.hashAlgorithm + " - must be either SHA2 or SHA3")
    // this.suite = this.algorithm.toLowerCase() + '-' + this.securityLevel;
    if (this.securityLevel == 256) {
      this.curveName = "P-256" // Curve that is currently used by FAB services.
      // TODO: HashOutputSize=32 ?
    }
    else if (this.securityLevel == 384) {
      this.curveName = "secp384r1"
      // TODO: HashOutputSize=48 ?
    }
  }

  def ecdsaKeyGen() = generateKey( "ECDSA", this.curveName )

  def generateKey(encryptionName: String, curveName: String) = {
    try {
      val ecGenSpec: ECGenParameterSpec = new ECGenParameterSpec(curveName)
      val g: KeyPairGenerator = KeyPairGenerator.getInstance(encryptionName, SECURITY_PROVIDER)
      g.initialize(ecGenSpec, new SecureRandom)
      val pair: KeyPair = g.generateKeyPair
      pair
    }
    catch {
      case exp: Exception => {
        throw new CryptoException("Unable to generate key pair", exp)
      }
    }
  }

  def generateCertificationRequest(subject: String, pair: KeyPair) = {
    val p10Builder: PKCS10CertificationRequestBuilder = new JcaPKCS10CertificationRequestBuilder(new X500Principal("CN=" + subject), pair.getPublic)
    val csBuilder: JcaContentSignerBuilder = new JcaContentSignerBuilder("SHA256withECDSA")
    // csBuilder.setProvider("EC");
    val signer: ContentSigner = csBuilder.build(pair.getPrivate)
    p10Builder.build(signer)
  }

  def certificationRequestToPEM(csr: PKCS10CertificationRequest) = {
    val pemCSR: PemObject = new PemObject("CERTIFICATE REQUEST", csr.getEncoded)
    val str: StringWriter = new StringWriter
    val pemWriter: JcaPEMWriter = new JcaPEMWriter(str)
    pemWriter.writeObject(pemCSR)
    pemWriter.close()
    str.close()
    str.toString
  }

  def ecdsaSignToBytes(privateKey: PrivateKey, data: Array[Byte]) = {
    try {
      var encoded: Array[Byte] = data
      encoded = SDKUtil.hash(data, getHashDigest)
      // char[] hexenncoded = Hex.encodeHex(encoded);
      // encoded = new String(hexenncoded).getBytes();
      val params = NISTNamedCurves.getByName(this.curveName)
      val curve_N = params.getN
      val ecParams: ECDomainParameters = new ECDomainParameters(params.getCurve, params.getG, curve_N, params.getH)
      val signer: ECDSASigner = new ECDSASigner
      val privKey: ECPrivateKeyParameters = new ECPrivateKeyParameters((privateKey.asInstanceOf[ECPrivateKey]).getS, ecParams)
      signer.init(true, privKey)
      val sigs = preventMalleability(signer.generateSignature(encoded), curve_N)
      val s: ByteArrayOutputStream = new ByteArrayOutputStream
      val seq: DERSequenceGenerator = new DERSequenceGenerator(s)
      seq.addObject(new ASN1Integer(sigs(0)))
      seq.addObject(new ASN1Integer(sigs(1)))
      seq.close()
      s.toByteArray
    } catch {
      case e: Exception => {
        throw new CryptoException("Could not sign the message using private key", e)
      }
    }
  }

  private def getHashDigest = {
    if (this.hashAlgorithm.equalsIgnoreCase("SHA3")) new SHA3Digest
    //else if (this.hashAlgorithm.equalsIgnoreCase("SHA2")) new SHA256Digest
    else new SHA256Digest // default Digest?
  }

  private def preventMalleability(sigs: Array[BigInteger], curve_n: BigInteger) = {
    val cmpVal = curve_n.divide(BigInteger.valueOf(2l))
    val sval = sigs(1)
    if (sval.compareTo(cmpVal) == 1) sigs(1) = curve_n.subtract(sval)
    sigs
  }

  def verify(plainText: Array[Byte], signature: Array[Byte], pemCertificate: Array[Byte]) = {
    var isVerified = false
    if (plainText.isEmpty || signature.isEmpty || pemCertificate.isEmpty ) false
    else {
      try {
        val pem = new BufferedInputStream(new ByteArrayInputStream(pemCertificate))
        val certFactory = CertificateFactory.getInstance(CERTIFICATE_FORMAT)
        val certificate = certFactory.generateCertificate(pem).asInstanceOf[X509Certificate]
        isVerified = validateCertificate(certificate)
        if (isVerified) {
          // only proceed if cert is trusted
          val signatureAlgorithm = certificate.getSigAlgName
          var sig = Signature.getInstance(signatureAlgorithm)
          sig.initVerify(certificate)
          sig.update(plainText)
          isVerified = sig.verify(signature)
          if (!isVerified) {
            // TODO currently fabric is trying to decide if the signature algorithm should
            // be passed along inside the certificate or configured manually or included in
            // the message itself. fabric-ca is also about to align its defaults with fabric.
            // while we wait, we will try both the algorithm specified in the cert and the
            // hardcoded default from fabric/fabric-ca
            sig = Signature.getInstance(DEFAULT_SIGNATURE_ALGORITHM)
            sig.initVerify(certificate)
            sig.update(plainText)
            isVerified = sig.verify(signature)
          }
        }
      } catch {
        case e: Any => {
          isVerified = false
        }
        case e: Any => {
          isVerified = false
        }
      }
      isVerified
    }
  } // verify

  def validateCertificate (cert: java.security.cert.Certificate): Boolean = {
    var isValidated = false
    if (cert == null) return isValidated
    try {
      if (keyStore == null) throw new CryptoException("Crypto does not have a trust store. No certificate can be validated", null)
      val parms = new PKIXParameters(keyStore)
      parms.setRevocationEnabled(false)
      val certValidator = CertPathValidator.getInstance(CertPathValidator.getDefaultType)
      // PKIX
      val start = Seq(cert)
      val certFactory = CertificateFactory.getInstance(CERTIFICATE_FORMAT)
      val certPath = certFactory.generateCertPath(start.asJava)
      certValidator.validate(certPath, parms)
      isValidated = true
    } catch {
      case e: Any => {
        isValidated = false
      }
    }
    isValidated
  } // validateCertificate

  lazy val keyStore: KeyStore = {
    val store = KeyStore.getInstance(KeyStore.getDefaultType)
    store.load(null, null)
    try {
      val cf = CertificateFactory.getInstance(CERTIFICATE_FORMAT)

      // manually populate peer CA certs while we wait for MSP configuration
      // to be implemented by Fabric
      SystemConfig.CACERTS.foreach { caCertFile =>
        try {
          val bis = new BufferedInputStream(this.getClass.getResourceAsStream(caCertFile))
          val caCert = cf.generateCertificate(bis)
          store.setCertificateEntry(caCertFile, caCert)
        } catch {
          case _ =>
        }
      }
    } catch {
      case e1: CertificateException => {
      }
    }
    store
  }

  def hash(input: Array[Byte]): Array[Byte] = {
    val digest = new SHA256Digest
    val retValue = new Array[Byte](digest.getDigestSize)
    digest.update(input, 0, input.length)
    digest.doFinal(retValue, 0)
    retValue
  }
}