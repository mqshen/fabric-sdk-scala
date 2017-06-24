package com.belink.chain

import java.io._

import com.belink.chain.exception.HttpConnectionManager
import com.belink.chain.listener.ChainListener
import com.belink.chain.listener.bubi.BubiChainListener
import com.belink.chain.peer.Peer
import com.belink.chain.processor.TransactionProcessor
import org.apache.commons.codec.binary.{Base32, Base64, Hex}
import org.hyperledger.fabric.sdk.{SystemConfig, User}
import org.hyperledger.fabric.sdk.ca._

import scala.collection.mutable

/**
 * Created by goldratio on 18/06/2017.
 */
object Client {

  val members = mutable.Map.empty[String, User]

  def enrollUser(name: String, secret: Option[String], register: Option[User] = None) = {
    val user: User = getMember(name)
    if (!user.isEnrolled) {
      val sec = secret.getOrElse {
        val attrs = UserAttribute("hf.Registrar.DelegateRoles", "client,user")
        val req = RegistrationRequest(name, "client", "org1", Seq(attrs))
        MemberServicesFabricCAImpl.instance.register(req, register.get)
      }
      val req = EnrollmentRequest(name, sec)

      val enrollment = MemberServicesFabricCAImpl.instance.enroll(req)
      val fileName = SystemConfig.USER_CERT_PATH + name
      val file = new File(fileName)
      val oos = new ObjectOutputStream(new FileOutputStream(file))
      oos.writeObject(enrollment)
      oos.close()

      user.enrollment = Some(enrollment)
    }
    members.put(name, user)
    user
  }

  def getMember(name: String) = {
    members.get(name) match {
      case Some(user) => user
      case _ =>
        val user = new User(name)
        val fileName = SystemConfig.USER_CERT_PATH + name
        val file = new File(fileName)
        if (file.exists()) {
          val userEnrollment = new ObjectInputStream(new FileInputStream(file)).readObject().asInstanceOf[Enrollment]
          user.enrollment = Some(userEnrollment)
        }
        user
    }
  }

  def main(args: Array[String]): Unit = {
//    val chainListener = new ChainListener("grpc://101.251.195.187:7053")
//    val admin = enrollUser("admin", Some("passwd"))
//    chainListener.start(admin)

    val hex = new Hex
    val test = hex.decode("7b2266726f6d4f72674964223a2022313233222c202275726c223a202275726c227d".getBytes())
    println(new String(test))
    val peer = new Peer("test", "grpc://localhost:7051")
    val httpConnectionManager = new HttpConnectionManager
    val processor = new TransactionProcessor(peer)

    val chainListener = new BubiChainListener(httpConnectionManager, processor)
    chainListener.start()
  }

}
