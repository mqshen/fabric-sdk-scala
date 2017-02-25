package org.hyperledger.fabric.sdk.chaincode

import java.io.File
import java.nio.charset.{Charset, StandardCharsets}

import com.google.protobuf.ByteString
import common.msp_principal.{MSPPrincipal, MSPRole}
import common.policies.SignaturePolicy.Type.SignedBy
import common.policies.{Policy, SignaturePolicy, SignaturePolicyEnvelope}
import io.netty.util.internal.StringUtil
import org.hyperledger.fabric.sdk.ca.Certificate
import org.hyperledger.fabric.sdk.helper.SDKUtil
import org.hyperledger.fabric.sdk.transaction.{GO_LANG, TransactionRequest, Type}
import org.hyperledger.protos.chaincode.{ChaincodeDeploymentSpec, ChaincodeID, ChaincodeInput, ChaincodeSpec}

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
    //val dockerFileContents = getDockerFileContents().format(chaincodeName)
    //val dockerFilePath = SDKUtil.combinePaths(projDir, "Dockerfile")
    //Files.write(dockerFileContents.getBytes, new File(dockerFilePath))
    // Create the compressed archive

    //SDKUtil.deleteFileOrDirectory(new File(dockerFilePath))
    val chainCodeVerion = "1"

    val argList = deployType match {
      case Install =>
        val projDir = SDKUtil.combinePaths(rootDir, chaincodeDir)
        val targzFilePath = SDKUtil.combinePaths(System.getProperty("java.io.tmpdir"), "deployment-package.tar.gz")
        SDKUtil.generateTarGz(projDir, targzFilePath)
        val data = SDKUtil.readFile(new File(targzFilePath))
        // Clean up temporary files
        SDKUtil.deleteFileOrDirectory(new File(targzFilePath))
        Seq[ByteString](
          ByteString.copyFrom("install", StandardCharsets.UTF_8),
          createDeploymentSpec(chaincodeName, args, ByteString.copyFrom(data), chaincodeDir, chainCodeVerion).toByteString
        )
      case Instantiate =>
        val principal = MSPPrincipal(principal = MSPRole("DEFAULT").toByteString)
        val t = SignaturePolicy(SignedBy(0))
        val policy = SignaturePolicyEnvelope(policy=Some(t), identities = Seq(principal))
        Seq[ByteString](
          ByteString.copyFrom("deploy", StandardCharsets.UTF_8),
          ByteString.copyFrom("testchainid", StandardCharsets.UTF_8),
          createDeploymentSpec(chaincodeName, args, ByteString.EMPTY, chaincodeDir, chainCodeVerion).toByteString,
          //policy.toByteString,
          ByteString.copyFrom("DEFAULT", StandardCharsets.UTF_8),
          ByteString.copyFrom("escc", StandardCharsets.UTF_8),
          ByteString.copyFrom("vscc", StandardCharsets.UTF_8)
        )
    }

    val lcccID = ChaincodeID(name = LCCC_CHAIN_NAME)
    createFabricProposal(context.get.chain.name, lcccID, argList)
  }


  def createDeploymentSpec(name: String, args: Seq[String], codePackage: ByteString,
                           chaincodePath: String, chaincodeVersion: String) = {
    val chaincodeID = ChaincodeID(chaincodePath, name, chaincodeVersion)


    val inputArgList = if (args.isEmpty) Seq.empty[ByteString] else {
      var argList = Seq(
        ByteString.copyFrom("Init", Charset.forName("UTF-8"))
      )
      args.foreach { arg =>
        argList = argList :+ ByteString.copyFrom(arg.getBytes)
      }
      argList
    }

    val chaincodeInput = ChaincodeInput(inputArgList)
    // Construct the ChaincodeSpec
    val chaincodeSpec = ChaincodeSpec(ccType, Some(chaincodeID), Some(chaincodeInput))
    val chaincodeDeploymentSpecBuilder = ChaincodeDeploymentSpec(Some(chaincodeSpec), codePackage = codePackage)
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
