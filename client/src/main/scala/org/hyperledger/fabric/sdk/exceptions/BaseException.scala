package org.hyperledger.fabric.sdk.exceptions

/**
  * Created by goldratio on 17/02/2017.
  */
class BaseException(message: String, cause: Throwable = null) extends Exception(message, cause) {

}

class InvalidArgumentException(message: String, cause: Throwable = null) extends BaseException(message, cause)

class CryptoException(message: String, cause: Throwable = null) extends BaseException(message, cause)

class EnrollmentException(message: String, cause: Throwable = null) extends BaseException(message, cause)

class PeerException(message: String, cause: Throwable = null) extends BaseException(message, cause)

class OrdererException(message: String, cause: Throwable = null) extends BaseException(message, cause)

