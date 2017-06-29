package belink.server

import belink.network.RequestChannel
import belink.utils.Logging
import com.ynet.belink.common.utils.{Time, Utils}

/**
  * Created by goldratio on 28/06/2017.
  */
class BelinkRequestHandlerPool(val requestChannel: RequestChannel,
                               val apis: BelinkApis,
                               time: Time,
                               numThreads: Int) extends Logging {

  val runnables = new Array[BelinkRequestHandler](numThreads)
  for(i <- 0 until numThreads) {
    runnables(i) = new BelinkRequestHandler(i, numThreads, requestChannel, apis, time)
    Utils.daemonThread("kafka-request-handler-" + i, runnables(i)).start()
  }

  def shutdown() {
    info("shutting down")
    for (handler <- runnables)
      handler.initiateShutdown()
    for (handler <- runnables)
      handler.awaitShutdown()
    info("shut down completely")
  }

}
