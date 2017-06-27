package belink.metrics

import belink.utils.{CoreUtils, VerifiableProperties}

/**
  * Created by goldratio on 27/06/2017.
  */
class BelinkMetricsConfig(props: VerifiableProperties) {

  /**
    * Comma-separated list of reporter types. These classes should be on the
    * classpath and will be instantiated at run-time.
    */
  val reporters = CoreUtils.parseCsvList(props.getString("kafka.metrics.reporters", ""))

  /**
    * The metrics polling interval (in seconds).
    */
  val pollingIntervalSecs = props.getInt("kafka.metrics.polling.interval.secs", 10)
}
