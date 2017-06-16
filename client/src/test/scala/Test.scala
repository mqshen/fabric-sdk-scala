import java.io._
import java.util.Base64

import com.google.common.io.BaseEncoding
import org.hyperledger.fabric.protos.peer.chaincode.ChaincodeID
import org.hyperledger.fabric.sdk.ca._
import org.hyperledger.fabric.sdk.chaincode.DeploymentProposalRequest
import org.hyperledger.fabric.sdk.transaction.{InvokeProposalRequest, QueryProposalRequest}
import org.hyperledger.fabric.sdk.{ChainCodeResponse, FabricClient, SystemConfig, User}

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
  * Created by goldratio on 17/02/2017.
  */
object Test {
  val CHAIN_CODE_NAME = "bonus"
  val CHAIN_CODE_PATH = "github.com/chaincode/bonus/"
  val CHAIN_CODE_VERSION = "1"
  val bonusId = "7R3DPR6j5Ytoi8heKXMBhtvF8indvsCYKctdXhDmaGVXvYARVLnsskgp4qL8qKXLSbi6t1tKn4YbE17oEL26yNpGK5YaD3SemQNR"


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

    //val base64Encoder = BaseEncoding.base64()
    val asset = enrollUser("asset1", None, Some(admin))
    val owner = enrollUser("owner1", None, Some(admin))
    //printUser(asset)
    //printUser(owner)
//    val attrs = UserAttribute("hf.Registrar.DelegateRoles", "client,user")
//    val registerUser = "test4"
//    val req = RegistrationRequest(registerUser, "client", "org1", Seq(attrs))
//    val registerPassword = MemberServicesFabricCAImpl.instance.register(req, asset)

//    enrollUser(registerUser, registerPassword)
//    val owner =  getMember(registerUser)

    val assetAddress = asset.enrollment.get.cert
    val ownerAddress = owner.enrollment.get.cert


    def doIssue(): Unit = {
      val chainCodeID = ChaincodeID(name = CHAIN_CODE_NAME, version = CHAIN_CODE_VERSION)

      val invokeProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "issue", Seq(bonusId, assetAddress, "5000"))
      chain.sendInvokeProposal(invokeProposalRequest, admin).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, admin).map { x =>
          println("success")
        }
      }

      Thread.sleep(10000)
    }

    val chainCodeID = ChaincodeID(name = CHAIN_CODE_NAME, version = CHAIN_CODE_VERSION)
    def doTransfer(): Unit = {

      val assignProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "assign", Seq(bonusId, ownerAddress, "50", "2000000000"))
      chain.sendInvokeProposal(assignProposalRequest, asset).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, asset).map { x =>
          println("success")
        }
      }

      Thread.sleep(10000)

      val assign2ProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "assign", Seq(bonusId, ownerAddress, "50", "2100000000"))
      chain.sendInvokeProposal(assign2ProposalRequest, asset).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, asset).map { x =>
          println("success")
        }
      }

      Thread.sleep(10000)

      val transferProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "transfer", Seq(bonusId, assetAddress, "20", "2000000000"))
      chain.sendInvokeProposal(transferProposalRequest, owner).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, owner).map { x =>
          println("success")
        }
      }


      Thread.sleep(10000)

      val detailTransferProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "transferWithDetail", Seq(bonusId, assetAddress, "[{\"expire\":2000000000,\"amount\":20,\"ext\":\"1\"},{\"expire\":2100000000,\"amount\":30,\"ext\":\"\"}]"))
      chain.sendInvokeProposal(detailTransferProposalRequest, owner).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, owner).map { x =>
          println("success")
        }
      }

      Thread.sleep(10000)
    }

    def doQuery(): Unit = {
      val chainCodeID = ChaincodeID(name = CHAIN_CODE_NAME, version = CHAIN_CODE_VERSION)

      val queryProposalRequest = new QueryProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "queryOrg", Seq(bonusId))
      chain.sendQueryProposal(queryProposalRequest, admin).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        println(res)
        val balance = successful.head.proposalResponse.response.get.payload.toStringUtf8
        println(balance)
      }

      val userQueryProposalRequest = new QueryProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "query", Seq(ownerAddress, bonusId))
      chain.sendQueryProposal(userQueryProposalRequest, admin).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        println(res)
        val balance = successful.head.proposalResponse.response.get.payload.toStringUtf8
        println(balance)
      }

      val transferQueryProposalRequest = new QueryProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "query", Seq(assetAddress, bonusId))
      chain.sendQueryProposal(transferQueryProposalRequest, admin).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        println(res)
        val balance = successful.head.proposalResponse.response.get.payload.toStringUtf8
        println(balance)
      }


    }


    def doInstall(): Unit = {
      // install

      val installProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Install,
        CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME, CHAIN_CODE_VERSION, Seq("github.com/golang/protobuf/"), "", Seq.empty)

      //val installProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Install,
      //  CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME, CHAIN_CODE_VERSION, Seq.empty, "", Seq.empty)

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

    def doTest() = {
      val detailTransferProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "transferWithDetail", Seq(bonusId, "bubiV8hUtmXcaBRgVgZGHDnCt9b85K4bRkf9bYFc", "[{\"expire\":2000000000,\"amount\":2}]"))
      chain.sendInvokeProposal(detailTransferProposalRequest, asset).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, asset).get
      }
      Thread.sleep(10000)
    }

    doInstall()
    Thread.sleep(10000)
    doIssue()
    doTransfer()
    doTest()
    doQuery()

  }

}
