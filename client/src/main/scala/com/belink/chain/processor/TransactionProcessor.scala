package com.belink.chain.processor

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.belink.chain.peer.Peer
import com.belink.data.protos.common.common.{ChannelHeader, Header}
import com.belink.data.protos.peer.belinkcode._
import com.belink.data.protos.peer.proposal.{BelinkcodeProposalPayload, Proposal}
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.util.EntityUtils
import com.google.protobuf.ByteString
import io.netty.util.internal.StringUtil
import org.hyperledger.fabric.protos.common.common.HeaderType
import org.hyperledger.fabric.sdk.helper.SDKUtil

/**
 * Created by goldratio on 19/06/2017.
 */
class TransactionProcessor(peer: Peer) {
  val sourceSavePath = "/Volumes/disk02/WorkspaceGroup/BlockchainWorkspace/src/github.com/chaincode/"
  val goSourcePath = "github.com/chaincode/"

  val connectManager = {
    val cm = new PoolingHttpClientConnectionManager
    cm.setMaxTotal(100)
    cm.setDefaultMaxPerRoute(100)
    cm
  }

  def getSource(url: String) = {
    val client = HttpClients.custom.setConnectionManager(connectManager).build
    val httpget = new HttpGet(url)
    httpget.addHeader("Connection", "close")
    val context = new BasicHttpContext
    val response = client.execute(httpget, context)
    try {
      val entity = response.getEntity
      EntityUtils.toString(entity)
    } finally {
      EntityUtils.consume(response.getEntity)
    }
  }

  def getSourceTest(url: String) = """package main

import (
	"fmt"
	"github.com/mqshen/data/belinkcode/shim"
	"github.com/mqshen/data/protos"
	"encoding/json"
)

type AccountManagementBelinkcode struct {
}

func (t *AccountManagementBelinkcode) Init(stub shim.BelinkcodeStubInterface) protos.Response {
	fmt.Println("success init belink code")
	return shim.Success([]byte("test"))
}
// Invoke is called for every Invoke transactions. The chaincode may change
// its state variables
func (t *AccountManagementBelinkcode) Invoke(stub shim.BelinkcodeStubInterface) protos.Response {
	fmt.Println("success invoke belink code")
	results, error := stub.GetQueryResult("wl_bonus_transaction", "100161", "bonus > 5")
	if error != nil {
		return shim.Error(fmt.Sprintf("faile query result:", error.Error()))
	}
	result, error := json.Marshal(results)
	if error != nil {
		return shim.Error(fmt.Sprintf("format result error:", error.Error()))
	}
	return shim.Success([]byte(result))
}

func main() {
	err := shim.Start(new(AccountManagementBelinkcode))
	if err != nil {
		fmt.Println("Error starting Simple chaincode: " + err.Error())
	}
}"""

  def process(cert: String, fromOrgId: String, url: String) = {
    try {
      val source = getSourceTest(url)
      val sourcePath = sourceSavePath + fromOrgId
      val dir = new File(sourcePath)
      if (!dir.exists()) {
        dir.mkdir()
      }
      val sourceFile = new File(dir.getAbsolutePath + "/source.go")
      val output = new java.io.PrintWriter(sourceFile)
      try {
        output.print(source)
      } finally {
        output.close()
      }
      val codePath = goSourcePath + fromOrgId + "/"
      val belinkcodeID = new BelinkcodeID(codePath, fromOrgId, "0")
      val argList = Seq(ByteString.copyFrom("init".getBytes()))
      val input = new BelinkcodeInput(argList)
      val belinkcodeSpec = new BelinkcodeSpec(BelinkcodeSpec.Type.GOLANG, Some(belinkcodeID), Some(input))

      val chaincodeDeploymentSpec = new BelinkcodeDeploymentSpec(Some(belinkcodeSpec))
      val proposal = getInstallProposal(chaincodeDeploymentSpec, codePath, true)
      val installResponse = peer.sendProposal(proposal)
      println("test for install:" + installResponse)

      val instanceProposal = getInstallProposal(chaincodeDeploymentSpec, codePath, false)
      val instanceResponse = peer.sendProposal(instanceProposal)
      println("test for instance:" + instanceResponse)
    } catch {
      case e => e.printStackTrace()
    }
  }

  def getInstallProposal(cds: BelinkcodeDeploymentSpec, chaincodeDir: String, install: Boolean) = {
    val argList:Seq[ByteString] = if(install) {
      val goPath = System.getenv("GOPATH")
      if (StringUtil.isNullOrEmpty(goPath)) throw new IllegalArgumentException("[NetMode] Missing GOPATH environment variable")
      val rootDir = SDKUtil.combinePaths(goPath, "src")
      val dependency = Seq("github.com/golang/protobuf/")

      val projDir = SDKUtil.combinePaths(rootDir, chaincodeDir)
      val targzFilePath = SDKUtil.combinePaths(System.getProperty("java.io.tmpdir"), "deployment-package.tar.gz")
      SDKUtil.generateTarGz(projDir, targzFilePath, chaincodeDir, rootDir, dependency)
      val data = SDKUtil.readFile(new File(targzFilePath))
      // Clean up temporary files
      SDKUtil.deleteFileOrDirectory(new File(targzFilePath))
      val newCds: BelinkcodeDeploymentSpec = cds.copy(codePackage = ByteString.copyFrom(data))
      Seq[ByteString](
        ByteString.copyFrom("install", StandardCharsets.UTF_8), newCds.toByteString)
    } else {
      Seq[ByteString](ByteString.copyFrom("deploy", StandardCharsets.UTF_8), cds.toByteString)
    }

    val belinkcodeID = BelinkcodeID(name = "lscc")
    val input = new BelinkcodeInput(argList)
    val belinkcodeSpec = new BelinkcodeSpec(BelinkcodeSpec.Type.GOLANG, Some(belinkcodeID), Some(input))
    val lsccSpec = new BelinkcodeInvocationSpec(Some(belinkcodeSpec))

    val ccPropPayload = new BelinkcodeProposalPayload(input = lsccSpec.toByteString)

    val channelHeader = new ChannelHeader(HeaderType.ENDORSER_TRANSACTION.value,
      txId = UUID.randomUUID().toString,
      belinkcodeId = lsccSpec.belinkcodeSpec.get.belinkcodeId)
    val hdr = new Header(channelHeader.toByteString)
    Proposal(hdr.toByteString, ccPropPayload.toByteString)
  }
}
