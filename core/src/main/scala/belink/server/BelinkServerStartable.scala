package belink.server

import java.util.Properties

import belink.metrics.BelinkMetricsReporter
import belink.utils.{Exit, Logging, VerifiableProperties}

/**
  * Created by goldratio on 27/06/2017.
  */
object BelinkServerStartable {

  def fromProps(serverProps: Properties) = {
    val reporters = BelinkMetricsReporter.startReporters(new VerifiableProperties(serverProps))
    new BelinkServerStartable(BelinkConfig.fromProps(serverProps), reporters)
  }

}


class BelinkServerStartable(val serverConfig: BelinkConfig, reporters: Seq[BelinkMetricsReporter]) extends Logging {
  private val server = new BelinkServer(serverConfig, belinkMetricsReporters = reporters)

  def startup() {
    try server.startup()
    catch {
      case _: Throwable =>
        // KafkaServer.startup() calls shutdown() in case of exceptions, so we invoke `exit` to set the status code
        fatal("Exiting Kafka.")
        Exit.exit(1)
    }
  }

  def shutdown() {
    try server.shutdown()
    catch {
      case _: Throwable =>
        fatal("Halting Kafka.")
        // Calling exit() can lead to deadlock as exit() can be called multiple times. Force exit.
        Exit.halt(1)
    }
  }

  def awaitShutdown(): Unit = server.awaitShutdown()

}