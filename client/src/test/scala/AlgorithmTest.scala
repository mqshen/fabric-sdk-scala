import java.io._
import java.util

import com.google.common.io.BaseEncoding
import org.hyperledger.fabric.protos.common.common._
import org.hyperledger.fabric.protos.common.ledger.BlockchainInfo
import org.hyperledger.fabric.protos.msp.identities.SerializedIdentity
import org.hyperledger.fabric.protos.peer.chaincode.{ChaincodeID, ChaincodeInvocationSpec}
import org.hyperledger.fabric.protos.peer.proposal.ChaincodeProposalPayload
import org.hyperledger.fabric.protos.peer.transaction.{ChaincodeActionPayload, Transaction}
import org.hyperledger.fabric.sdk.ca._
import org.hyperledger.fabric.sdk.chaincode.DeploymentProposalRequest
import org.hyperledger.fabric.sdk.helper.SDKUtil
import org.hyperledger.fabric.sdk.transaction.{InvokeProposalRequest, QueryProposalRequest}
import org.hyperledger.fabric.sdk.{ChainCodeResponse, FabricClient, SystemConfig, User}

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
  * Created by goldratio on 17/02/2017.
  */
object AlgorithmTest {
  val CHAIN_CODE_NAME = "algorithm"
  val CHAIN_CODE_PATH = "github.com/chaincode/algorithm/"
  val CHAIN_CODE_VERSION = "1"
  val bonusId = "7R3DPR6j5Ytoi8heKXMBhtvF8indvsCYKctdXhDmaGVXvYARVLnsskgp4qL8qKXLSbi6t1tKn4YbE17oEL26yNpGK5YaD3SemQNR"


  val QUERY_CHAIN_CODE_NAME = "qscc"
  val QUERY_CHAIN_CODE_PATH = "github.com/hyperledger/fabric/core/chaincode/qscc"
  val QUERY_CHAIN_CODE_VERSION = ""


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
    val client = FabricClient.instance
    val chain = client.newChain(SystemConfig.CHAIN_NAME)

    SystemConfig.PEER_LOCATIONS.foreach{ url =>
      val peer = client.newPeer(url)
      chain.addPeer(peer)
    }

    SystemConfig.ORDERER_LOCATIONS.foreach{ url =>
      val peer = client.newOrderer(url)
      chain.addOrderer(peer)
    }

    SystemConfig.EVENTHUB_LOCATIONS.foreach{ url =>
      chain.addEventHub(url)
    }

    chain.initialize()
    client.userContext = Some(new User("admin"))

    //currentChain.enroll("asset", "assetpw")

    //currentChain.enroll("owner", "ownerpw")

    val admin = enrollUser("admin", Some("passwd"))

    def printUser(user: User) = {
      user.enrollment.map { e =>
        val base64Encoder = BaseEncoding.base64()
        val privateKey = base64Encoder.encode(e.key.getPrivate.getEncoded)
        val publicKey = base64Encoder.encode(e.key.getPublic.getEncoded)
        val address = base64Encoder.encode(e.cert.getBytes)
        println(privateKey)
        println(publicKey)
        println(address)
        println(e.cert)
        println("--------------------------------------------------")
      }
    }

    printUser(admin)

    val asset = enrollUser("asset1", None, Some(admin))
    val owner = enrollUser("owner1", None, Some(admin))
    val assetAddress = asset.enrollment.get.cert
    val ownerAddress = owner.enrollment.get.cert


    def doInstall(): Unit = {
      // install

      val installProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Install,
        CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME, CHAIN_CODE_VERSION, Seq("github.com/golang/protobuf/"), "", Seq.empty)

      val responses = chain.sendDeploymentProposal(installProposalRequest, admin)

      responses.map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (successful.size == 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
      }

      // deploy
      val deployProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Instantiate,
        CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME, CHAIN_CODE_VERSION, Seq(""),
        "", Seq(bonusId, "100", "b", "200"))

      val deployResponses = chain.sendDeploymentProposal(deployProposalRequest, admin)

      import scala.concurrent.ExecutionContext.Implicits.global

      deployResponses.map { res =>
        res(0).status
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (successful.size == 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, admin).map { x =>
          x.future.onSuccess { case _ =>
              println("deploy success")
          }
        }
      }
    }


    val chainCodeID = ChaincodeID(name = CHAIN_CODE_NAME, version = CHAIN_CODE_VERSION)

    def doTest() = {
      val detailTransferProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "apply", Seq(bonusId, "bubiV8hUtmXcaBRgVgZGHDnCt9b85K4bRkf9bYFc", "[{\"expire\":2000000000,\"amount\":2}]"))
      chain.sendInvokeProposal(detailTransferProposalRequest, asset).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, asset).get
      }
      Thread.sleep(10000)
    }




    def queryBlockchainInfo() = {
      val chainCodeID = ChaincodeID(path = QUERY_CHAIN_CODE_PATH,
        name = QUERY_CHAIN_CODE_NAME,
        version = QUERY_CHAIN_CODE_VERSION)
      val nonce = SDKUtil.generateNonce
      val userQueryProposalRequest = new QueryProposalRequest(chainCodeID.path, chainCodeID.name,
        chainCodeID, "GetChainInfo", Seq("testchainid", "2"))


      chain.sendQueryProposal(userQueryProposalRequest, admin).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        val blockchainInfo = BlockchainInfo.parseFrom(successful.head.proposalResponse.response.get.payload.toByteArray)
        println(blockchainInfo)
      }
    }


    def queryBlockchainByNumber() = {
      val chainCodeID = ChaincodeID(path = QUERY_CHAIN_CODE_PATH,
        name = QUERY_CHAIN_CODE_NAME,
        version = QUERY_CHAIN_CODE_VERSION)
      val nonce = SDKUtil.generateNonce
      val userQueryProposalRequest = new QueryProposalRequest(chainCodeID.path, chainCodeID.name,
        chainCodeID, "GetBlockByNumber", Seq("testchainid", "3"))

      chain.sendQueryProposal(userQueryProposalRequest, admin).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        val blockInfo = Block.parseFrom(successful.head.proposalResponse.response.get.payload.toByteArray)

        blockInfo.data.map{ blockData =>
          blockData.data.foreach { db =>
            val env = Envelope.parseFrom(db.toByteArray)
            val payload = Payload.parseFrom(env.payload.toByteArray)
            val plh = payload.getHeader

            val channelHeader = ChannelHeader.parseFrom(plh.channelHeader.toByteArray)
            val txID = channelHeader.txId
            println("get transactionId:" + txID)

            channelHeader.`type` match {
              case HeaderType.ENDORSER_TRANSACTION.value =>
                val tx = Transaction.parseFrom(payload.data.toByteArray)
                val h = SignatureHeader.parseFrom(tx.actions(0).header.toByteArray)
                val identity = SerializedIdentity.parseFrom(h.creator.toByteArray)
                val chaincodeActionPayload = ChaincodeActionPayload.parseFrom( tx.actions(0).payload.toByteArray)
                val cppNoTransient = ChaincodeProposalPayload.parseFrom(chaincodeActionPayload.chaincodeProposalPayload.toByteArray)
                val pPayl = ChaincodeProposalPayload.parseFrom(cppNoTransient.input.toByteArray)
                val spec = ChaincodeInvocationSpec.parseFrom(pPayl.toByteArray)
                println(spec)
                println(identity.idBytes.toStringUtf8)
            }
            payload.data
          }
        }
      }
    }

//    doInstall()
//    Thread.sleep(10000)
//    doTest()

    queryBlockchainInfo
    queryBlockchainByNumber
  }

}
