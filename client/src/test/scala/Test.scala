import com.google.common.io.BaseEncoding
import org.hyperledger.fabric.sdk.ca.{MemberServicesFabricCAImpl, RegistrationRequest, UserAttribute}
import org.hyperledger.fabric.sdk.chaincode.DeploymentProposalRequest
import org.hyperledger.fabric.sdk.transaction.{InvokeProposalRequest, QueryProposalRequest}
import org.hyperledger.fabric.sdk.{ChainCodeResponse, FabricClient, SystemConfig, User}
import org.hyperledger.protos.chaincode.ChaincodeID

import scala.collection.JavaConversions._

/**
  * Created by goldratio on 17/02/2017.
  */
object Test {
  val CHAIN_CODE_NAME = "bonusttt"
  val CHAIN_CODE_PATH = "github.com/bonus"


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
    client.userContext = Some(new User("admin", chain))

    val currentChain = client.getChain(SystemConfig.CHAIN_NAME)

    //currentChain.enroll("asset", "assetpw")

    //currentChain.enroll("owner", "ownerpw")

    currentChain.enroll("admin", "adminpw")

    //val base64Encoder = BaseEncoding.base64()
    val asset = chain.getMember("admin")
    val attrs = UserAttribute("hf.Registrar.DelegateRoles", "client,user")
    val registerUser = "test2"
    val req = RegistrationRequest(registerUser, "client", "org1", Seq(attrs))
    val registerPassword = MemberServicesFabricCAImpl.instance.register(req, asset)

    currentChain.enroll(registerUser, registerPassword)


//    asset.enrollment.map { e =>
//      val privateKey = base64Encoder.encode(e.key.getPrivate.getEncoded)
//      val publicKey = base64Encoder.encode(e.key.getPublic.getEncoded)
//      val address = e.cert
//      println(privateKey)
//      println(publicKey)
//      println(base64Encoder.encode(address.getBytes))
//    }
    val assetAddress = asset.enrollment.get.cert

//    val owner = chain.getMember("owner")
//    val ownerAddress = owner.enrollment.get.cert

    val admin = chain.getMember("admin")
    def doTransfer(): Unit = {
      val chainCodeID = ChaincodeID(name = CHAIN_CODE_NAME, version = "2")

      val invokeProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "issue", Seq("a", assetAddress, "5000"))
      chain.sendInvokeProposal(invokeProposalRequest, owner.enrollment.get).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, owner.enrollment.get).map { x =>
          println("success")
        }
      }

      Thread.sleep(30000)


      val assignProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "assign", Seq("a", ownerAddress, "50", "20171010"))
      chain.sendInvokeProposal(assignProposalRequest, asset.enrollment.get).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, asset.enrollment.get).map { x =>
          println("success")
        }
      }

      Thread.sleep(30000)

      val transferProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "transfer", Seq("a", assetAddress, "20", "20171001"))
      chain.sendInvokeProposal(transferProposalRequest, owner.enrollment.get).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, owner.enrollment.get).map { x =>
          println("success")
        }
      }

    }

    def doQuery(): Unit = {
      val chainCodeID = ChaincodeID(name = CHAIN_CODE_NAME, version = "2")

      val queryProposalRequest = new QueryProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "queryOrg", Seq("a"))
      chain.sendQueryProposal(queryProposalRequest, admin.enrollment.get).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        println(res)
        val balance = successful.head.proposalResponse.response.get.payload.toStringUtf8
        println(balance)
      }

      val userQueryProposalRequest = new QueryProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "query", Seq(ownerAddress, "a"))
      chain.sendQueryProposal(userQueryProposalRequest, admin.enrollment.get).map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (failed.size > 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        println(res)
        val balance = successful.head.proposalResponse.response.get.payload.toStringUtf8
        println(balance)
      }

      val transferQueryProposalRequest = new QueryProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
        chainCodeID, "query", Seq(assetAddress, "a"))
      chain.sendQueryProposal(transferQueryProposalRequest, admin.enrollment.get).map { res =>
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

      val installProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Install, CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME,
        "", Seq.empty)

      val responses = chain.sendDeploymentProposal(installProposalRequest, admin.enrollment.get)

      responses.map { res =>
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (successful.size == 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
      }

      // deploy
      val deployProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Instantiate,
        CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME,
        "", Seq("a", "100", "b", "200"))

      val deployResponses = chain.sendDeploymentProposal(deployProposalRequest, admin.enrollment.get)

      import scala.concurrent.ExecutionContext.Implicits.global

      deployResponses.map { res =>
        res(0).status
        val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
        val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
        if (successful.size == 0)
          throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
        chain.sendTransaction(successful, admin.enrollment.get).map { x =>
          x.future.onSuccess { case _ =>
              println("deploy success")
          }
        }
      }
    }


    //doInstall()
    //doTransfer()
    //doQuery()

  }

}
