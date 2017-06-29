package com.belink.chain.listener.bubi

import com.belink.chain.config.SystemConfig
import com.belink.chain.http.HttpClient
import org.json4s.jackson.Serialization.read

/**
 * Created by goldratio on 24/06/2017.
 */
case class CurrentLedger(ledger_sequence: Long)

class BubiChainView(httpClient: HttpClient) {

  implicit val formats = org.json4s.DefaultFormats

  def currentLedger() = {
    val info = httpClient.get(s"${SystemConfig.bubiHost}/getLedger")
    val json = read[BlockInfoRes](info)
    if (json.error_code == 0) {
      val blockInfo = json.result
      (blockInfo.ledger_seq, blockInfo.tx_count)
    } else throw new Exception("get Chain Block fained")
  }

  def blockInfo(seq: Long) = {
    val info = httpClient.get(s"${SystemConfig.bubiHost}/getLedger?seq=${seq}")
    val json = read[BlockInfoRes](info)
    if (json.error_code == 0) json.result else throw new Exception("get Chain Block fained")
  }

  def transactions(index: Int, number: Long) = {
    val info = httpClient.get(s"${SystemConfig.bubiHost}/getTransactionHistory?start=$index&limit=$number")
    try {
      val json = read[TransactionRes](info)
      if (json.error_code == 0) json.result.transactions else throw new Exception("get transaction failed")
    } catch {
      case e =>
        e.printStackTrace()
        println(info)
        throw e
    }
  }

  def addressInfo(address: String) = {
    val info = httpClient.get(s"${SystemConfig.bubiHost}/account?address=$address")
    val json = read[AddressInfoRes](info)
    if (json.error_code == 0) json.result.get else throw new Exception("get Chain Block fained")
  }

  def transactionInfo(hash: String) = {
    val info = httpClient.get(s"${SystemConfig.bubiHost}/transaction?hash=$hash")
    val json = read[TransactionRes](info)
    if (json.error_code == 0) json.result.transactions(0) else throw new Exception("get Chain Block fained")
  }

}
