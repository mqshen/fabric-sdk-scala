package belink.blockchain

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import belink.server.BelinkConfig
import belink.utils.{Logging, Scheduler}
import com.ynet.belink.common.utils.Time

/**
  * Created by goldratio on 27/06/2017.
  */
class BlockChainManager(val config: BelinkConfig,
                        time: Time,
                        scheduler: Scheduler,
                        val isShuttingDown: AtomicBoolean) extends Logging {

  private def syncBlockChain(): Unit = {
    trace("start sync blockchain's block")
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
