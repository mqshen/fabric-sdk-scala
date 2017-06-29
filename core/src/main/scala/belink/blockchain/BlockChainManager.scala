package belink.blockchain

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import belink.network.RequestChannel
import belink.network.RequestChannel.EmptySession
import belink.server.BelinkConfig
import belink.utils.{Logging, Scheduler}
import com.belink.chain.http.{HttpClient, HttpConnectionManager}
import com.belink.chain.listener.bubi.BubiChainView
import com.ynet.belink.common.network.ListenerName
import com.ynet.belink.common.protocol.{ApiKeys, SecurityProtocol}
import com.ynet.belink.common.requests.{ProduceRequest, RequestHeader}
import com.ynet.belink.common.utils.Time
import org.apache.commons.codec.binary.Hex

/**
  * Created by goldratio on 27/06/2017.
  */
case class DataAuth(fromOrgId: String, url: String)

class BlockChainManager(val config: BelinkConfig,
                        time: Time,
                        scheduler: Scheduler,
                        requestChannel: RequestChannel,
                        val isShuttingDown: AtomicBoolean) extends Logging {
  implicit val formats = org.json4s.DefaultFormats
  val listenerName = new ListenerName("local block manager")

  val httpConnectionManager = new HttpConnectionManager
  val httpClient = new HttpClient(httpConnectionManager)
  val chainView = new BubiChainView(httpClient)
  val ex = new ScheduledThreadPoolExecutor(1)
  val hex = new Hex

  var currentLedger = 1178600L
  var lastTransactionNumber = 225546L

  private def syncBlockChain(): Unit = {
    trace("start sync blockchain's block")
    val (ledgerNo, txCount) = chainView.currentLedger()
    if (ledgerNo > currentLedger) {
      val queryCount = txCount - lastTransactionNumber
      if (queryCount > 0) {
        //chainView.transactions(0, queryCount).foreach { tx =>
        chainView.transactions(0, 20).foreach { tx =>
          if (tx.operations.size == 1) {
            val operation = tx.operations.head
            operation.metadata.map { m =>
              val metadata = hex.decode(m.getBytes())
              //val auth = read[DataAuth](new String(metadata))
              //processor.process(operation.source_address.get, auth.fromOrgId, auth.url)
              //val payload = ByteBuffer.wrap(m.getBytes())
              val requestHeader = new RequestHeader(ApiKeys.PRODUCE.id, ApiKeys.PRODUCE.latestVersion(), "", 0)
              val requestBuilder = new ProduceRequest.Builder(3, 0, 1000)
              val request = requestBuilder.build()
              val payload = request.serialize(requestHeader)

              val req = RequestChannel.Request(processor = 0, connectionId = "", session = EmptySession,
                buffer = payload, startTimeMs = time.milliseconds, listenerName = listenerName,
                securityProtocol = SecurityProtocol.PLAINTEXT)
              requestChannel.sendRequest(req)
            }
          }
        }
      }
      lastTransactionNumber = txCount
      currentLedger = ledgerNo
    }
  }

  def startup() {
    // start ISR expiration thread
    // A follower can lag behind leader for up to config.replicaLagTimeMaxMs x 1.5 before it is removed from ISR
    scheduler.schedule("blockchain-sync", syncBlockChain, period = config.blockchainSyncInterval , unit = TimeUnit.MILLISECONDS)
  }

  def shutdown(checkpointHW: Boolean = true) {
    info("Shutting down")
    //replicaFetcherManager.shutdown()
    info("Shut down completely")
  }
}
