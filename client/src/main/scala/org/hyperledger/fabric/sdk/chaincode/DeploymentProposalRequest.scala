package org.hyperledger.fabric.sdk.chaincode

import java.io.File
import java.nio.charset.{Charset, StandardCharsets}

import com.google.common.io.Files
import com.google.protobuf.ByteString
import io.netty.util.internal.StringUtil
import org.hyperledger.fabric.protos.peer.chaincode.{ChaincodeDeploymentSpec, ChaincodeID, ChaincodeInput, ChaincodeSpec}
import org.hyperledger.fabric.sdk.ca.Certificate
import org.hyperledger.fabric.sdk.helper.SDKUtil
import org.hyperledger.fabric.sdk.transaction.{GO_LANG, TransactionRequest, Type}

/**
  * Created by goldratio on 17/02/2017.
  */
object DeploymentProposalRequest {
  val LCCC_CHAIN_NAME = "lccc"
  sealed trait DeployType
  case object Install extends DeployType
  case object Instantiate extends DeployType
}
class DeploymentProposalRequest(deployType: DeploymentProposalRequest.DeployType, chaincodePath: String, chaincodeName: String, chaincodeID: String,
                                fcn: String, args: Seq[String], userCert: Option[Certificate] = None, metadata: Array[Byte] = Array.empty)
  extends TransactionRequest(chaincodePath, chaincodeName, fcn, args, userCert, metadata) {
  import DeploymentProposalRequest._


  def toProposal() = {
    val (rootDir, chaincodeDir) = chaincodeLanguage match {
      case GO_LANG =>
        val goPath = System.getenv ("GOPATH")
        if (StringUtil.isNullOrEmpty(goPath)) throw new IllegalArgumentException("[NetMode] Missing GOPATH environment variable")
        (SDKUtil.combinePaths(goPath, "src"), chaincodePath)
      case _ =>
        val ccFile = new File(chaincodePath)
        (ccFile.getParent, ccFile.getName)
    }
    val projDir = SDKUtil.combinePaths(rootDir, chaincodeDir)
    //val dockerFileContents = getDockerFileContents().format(chaincodeName)
    //val dockerFilePath = SDKUtil.combinePaths(projDir, "Dockerfile")
    //Files.write(dockerFileContents.getBytes, new File(dockerFilePath))
    val targzFilePath = SDKUtil.combinePaths(System.getProperty("java.io.tmpdir"), "deployment-package.tar.gz")
    // Create the compressed archive
    SDKUtil.generateTarGz(projDir, targzFilePath)
    val data = SDKUtil.readFile(new File(targzFilePath))
    // Clean up temporary files
    SDKUtil.deleteFileOrDirectory(new File(targzFilePath))
    //SDKUtil.deleteFileOrDirectory(new File(dockerFilePath))
    val depspec = createDeploymentSpec(chaincodeName, args, data, chaincodeDir, "0.8.0-snapshot-9f561b8")

    val argList = deployType match {
      case Install =>
        Seq[ByteString](
          ByteString.copyFrom("install", StandardCharsets.UTF_8),
          depspec.toByteString
        )
      case Instantiate =>
        Seq[ByteString](
          ByteString.copyFrom("deploy", StandardCharsets.UTF_8),
          ByteString.copyFrom("default", StandardCharsets.UTF_8),
          depspec.toByteString
        )
    }

    val lcccID = ChaincodeID(name = LCCC_CHAIN_NAME)
    createFabricProposal(context.get.chain.name, lcccID, argList)
  }


  def createDeploymentSpec(name: String, args: Seq[String], codePackage: Array[Byte],
                           chaincodePath: String, chaincodeVersion: String) = {
    val chaincodeID = ChaincodeID(chaincodePath, name, chaincodeVersion)

    var argList = Seq(
      ByteString.copyFrom("init", Charset.forName("UTF-8"))
    )
    args.foreach { arg =>
      argList = argList :+ ByteString.copyFrom(arg.getBytes)
    }

    val chaincodeInput = ChaincodeInput(argList)
    // Construct the ChaincodeSpec
    val chaincodeSpec = ChaincodeSpec(ccType, Some(chaincodeID), Some(chaincodeInput))
    val chaincodeDeploymentSpecBuilder = ChaincodeDeploymentSpec(Some(chaincodeSpec),
      Some(context.get.getFabricTimestamp), ByteString.copyFrom(codePackage),
      ChaincodeDeploymentSpec.ExecutionEnvironment.DOCKER)
    chaincodeDeploymentSpecBuilder
  }

  def getDockerFileContents() = {
    chaincodeLanguage match {
      case GO_LANG =>
        new String(SDKUtil.readFileFromClasspath("Go.Docker"))
      case _ =>
        new String(SDKUtil.readFileFromClasspath("Java.Docker"))
    }
  }


}
