package com.belink.chain.listener.bubi

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import com.belink.chain.config.SystemConfig
import com.belink.chain.exception.HttpConnectionManager
import com.belink.chain.http.HttpClient
import com.belink.chain.processor.TransactionProcessor
import org.apache.commons.codec.binary.{Base64, Hex}
import org.json4s.jackson.Serialization.read

/**
  * Created by goldratio on 24/06/2017.
  */
case class DataAuth(fromOrgId: String, url: String)

class BubiChainListener(httpConnectionManager: HttpConnectionManager, processor: TransactionProcessor) {
  implicit val formats = org.json4s.DefaultFormats

  val httpClient = new HttpClient(httpConnectionManager)
  val chainView = new BubiChainView(httpClient)
  val ex = new ScheduledThreadPoolExecutor(1)
  val hex = new Hex

  def start() = {
    val task = new Runnable {
      var currentLedger = 0L
      var lastTransactionNumber = 0L
      override def run() = {
        try {
          val (ledgerNo, txCount) = chainView.currentLedger()
          if (ledgerNo > currentLedger) {
            val queryCount = txCount - lastTransactionNumber
            if(queryCount > 0) {
              chainView.transactions(0, queryCount).foreach { tx =>
                if (tx.operations.size == 1) {
                  val operation = tx.operations.head
                  if (operation.dest_address == SystemConfig.address) {
                    operation.metadata.map { m =>
                      val metadata = hex.decode(m.getBytes())
                      val auth = read[DataAuth](new String(metadata))
                      processor.process(operation.source_address.get, auth.fromOrgId, auth.url)
                    }
                  }
                }
              }
            }
            lastTransactionNumber = txCount
            currentLedger = ledgerNo
          }
        } catch {
          case e => e.printStackTrace()
        }
      }
    }
    ex.scheduleAtFixedRate(task, 0, 5, TimeUnit.SECONDS)
  }

}
