import org.hyperledger.fabric.protos.common.common.Status
import org.hyperledger.fabric.sdk.chaincode.DeploymentProposalRequest
import org.hyperledger.fabric.sdk.transaction.{InvokeProposalRequest, QueryProposalRequest}
import org.hyperledger.fabric.sdk.{ChainCodeResponse, FabricClient, SystemConfig, User}

import scala.collection.JavaConversions._

/**
  * Created by goldratio on 17/02/2017.
  */
object Test {
  val CHAIN_CODE_NAME = "example"
  val CHAIN_CODE_PATH = "github.com/example_cc"

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
    currentChain.enroll("admin", "adminpw")

    // install
//    val installProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Install, CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME,
//      "", Seq.empty)
//
//    val responses = chain.sendDeploymentProposal(installProposalRequest)
//
//    responses.map { res =>
//      val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
//      val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
//      if(successful.size == 0)
//        throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
//    }

    // deploy
    val deployProposalRequest = new DeploymentProposalRequest(DeploymentProposalRequest.Instantiate,
      CHAIN_CODE_PATH, CHAIN_CODE_NAME, SystemConfig.CHAIN_NAME,
      "", Seq("a", "100", "b", "200"))

    val deployResponses = chain.sendDeploymentProposal(deployProposalRequest)

    import scala.concurrent.ExecutionContext.Implicits.global

    deployResponses.map { res =>
      res(0).status
      val successful = res.filter(x => x.isVerified && x.status == ChainCodeResponse.SUCCESS)
      val failed = res.filter(x => !x.isVerified || x.status != ChainCodeResponse.SUCCESS)
      if(successful.size == 0)
        throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
      val chainCodeID = successful.head.getChainCodeID
      chain.sendTransaction(successful).map { x =>
        x.future.onSuccess { case _ =>
          val invokeProposalRequest = new InvokeProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
            chainCodeID, "invoke", Seq("move", "a", "b", "100"))
          chain.sendInvokeProposal(invokeProposalRequest).map { res =>
            val successful = res.filter(x => x.isVerified && x.status == Status.SUCCESS)
            val failed = res.filter(x => !x.isVerified || x.status != Status.SUCCESS)
            if (failed.size > 0)
              throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
            chain.sendTransaction(successful).map { x =>
              val queryProposalRequest = new QueryProposalRequest(CHAIN_CODE_PATH, CHAIN_CODE_NAME,
                chainCodeID, "invoke", Seq("query", "b"))
              chain.sendQueryProposal(queryProposalRequest).map { res =>
                val successful = res.filter(x => x.isVerified && x.status == Status.SUCCESS)
                val failed = res.filter(x => !x.isVerified || x.status != Status.SUCCESS)
                if (failed.size > 0)
                  throw new Exception("Not enough endorsers :" + successful.size + ".  " + failed(0).proposalResponse.response.get.message)
              }
            }
          }
        }
      }
    }
  }

}
