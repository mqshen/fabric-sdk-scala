package belink

import java.util.Properties

import belink.server.{BelinkServer, BelinkServerStartable}
import belink.utils.{CommandLineUtils, Logging}
import com.ynet.belink.common.utils.{Exit, Utils}
import joptsimple.OptionParser

import scala.collection.JavaConverters._
/**
  * Created by goldratio on 27/06/2017.
  */
object Belink extends Logging {

  def getPropsFromArgs(args: Array[String]): Properties = {
    val optionParser = new OptionParser
    val overrideOpt = optionParser.accepts("override", "Optional property that should override values set in server.properties file")
      .withRequiredArg()
      .ofType(classOf[String])

    if (args.length == 0) {
      CommandLineUtils.printUsageAndDie(optionParser, "USAGE: java [options] %s server.properties [--override property=value]*".format(classOf[BelinkServer].getSimpleName()))
    }

    val props = Utils.loadProps(args(0))

    if(args.length > 1) {
      val options = optionParser.parse(args.slice(1, args.length): _*)

      if(options.nonOptionArguments().size() > 0) {
        CommandLineUtils.printUsageAndDie(optionParser, "Found non argument parameters: " + options.nonOptionArguments().toArray.mkString(","))
      }

      props.putAll(CommandLineUtils.parseKeyValueArgs(options.valuesOf(overrideOpt).asScala))
    }
    props
  }

  def main(args: Array[String]): Unit = {
    try {
      val serverProps = getPropsFromArgs(args)
      val kafkaServerStartable = BelinkServerStartable.fromProps(serverProps)

      // attach shutdown handler to catch control-c
      Runtime.getRuntime().addShutdownHook(new Thread("kafka-shutdown-hook") {
        override def run(): Unit = kafkaServerStartable.shutdown()
      })

      kafkaServerStartable.startup()
      kafkaServerStartable.awaitShutdown()
    }
    catch {
      case e: Throwable =>
        fatal(e)
        Exit.exit(1)
    }
    Exit.exit(0)
  }

}
