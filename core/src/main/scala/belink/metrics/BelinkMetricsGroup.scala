package belink.metrics

import java.util.concurrent.TimeUnit
import javax.management.ObjectName

import belink.utils.Logging
import com.yammer.metrics.Metrics
import com.yammer.metrics.core.{Gauge, MetricName}

/**
  * Created by goldratio on 29/06/2017.
  */
trait BelinkMetricsGroup extends Logging {

  /**
    * Creates a new MetricName object for gauges, meters, etc. created for this
    * metrics group.
    * @param name Descriptive name of the metric.
    * @param tags Additional attributes which mBean will have.
    * @return Sanitized metric name object.
    */
  private def metricName(name: String, tags: scala.collection.Map[String, String] = Map.empty) = {
    val klass = this.getClass
    val pkg = if (klass.getPackage == null) "" else klass.getPackage.getName
    val simpleName = klass.getSimpleName.replaceAll("\\$$", "")
    // Tags may contain ipv6 address with ':', which is not valid in JMX ObjectName
    def quoteIfRequired(value: String) = if (value.contains(':')) ObjectName.quote(value) else value
    val metricTags = tags.map(kv => (kv._1, quoteIfRequired(kv._2)))

    explicitMetricName(pkg, simpleName, name, metricTags)
  }


  private def explicitMetricName(group: String, typeName: String, name: String, tags: scala.collection.Map[String, String] = Map.empty) = {
    val nameBuilder: StringBuilder = new StringBuilder

    nameBuilder.append(group)

    nameBuilder.append(":type=")

    nameBuilder.append(typeName)

    if (name.length > 0) {
      nameBuilder.append(",name=")
      nameBuilder.append(name)
    }

    val scope: String = BelinkMetricsGroup.toScope(tags).getOrElse(null)
    val tagsName = BelinkMetricsGroup.toMBeanName(tags)
    tagsName match {
      case Some(tn) =>
        nameBuilder.append(",").append(tn)
      case None =>
    }

    new MetricName(group, typeName, name, scope, nameBuilder.toString())
  }

  def newGauge[T](name: String, metric: Gauge[T], tags: scala.collection.Map[String, String] = Map.empty) =
    Metrics.defaultRegistry().newGauge(metricName(name, tags), metric)

  def newMeter(name: String, eventType: String, timeUnit: TimeUnit, tags: scala.collection.Map[String, String] = Map.empty) =
    Metrics.defaultRegistry().newMeter(metricName(name, tags), eventType, timeUnit)

  def newHistogram(name: String, biased: Boolean = true, tags: scala.collection.Map[String, String] = Map.empty) =
    Metrics.defaultRegistry().newHistogram(metricName(name, tags), biased)

  def newTimer(name: String, durationUnit: TimeUnit, rateUnit: TimeUnit, tags: scala.collection.Map[String, String] = Map.empty) =
    Metrics.defaultRegistry().newTimer(metricName(name, tags), durationUnit, rateUnit)

  def removeMetric(name: String, tags: scala.collection.Map[String, String] = Map.empty) =
    Metrics.defaultRegistry().removeMetric(metricName(name, tags))

}

object BelinkMetricsGroup {
  private def toMBeanName(tags: collection.Map[String, String]): Option[String] = {
    val filteredTags = tags.filter { case (_, tagValue) => tagValue != "" }
    if (filteredTags.nonEmpty) {
      val tagsString = filteredTags.map { case (key, value) => "%s=%s".format(key, value) }.mkString(",")
      Some(tagsString)
    }
    else None
  }

  private def toScope(tags: collection.Map[String, String]): Option[String] = {
    val filteredTags = tags.filter { case (_, tagValue) => tagValue != ""}
    if (filteredTags.nonEmpty) {
      // convert dot to _ since reporters like Graphite typically use dot to represent hierarchy
      val tagsString = filteredTags
        .toList.sortWith((t1, t2) => t1._1 < t2._1)
        .map { case (key, value) => "%s.%s".format(key, value.replaceAll("\\.", "_"))}
        .mkString(".")

      Some(tagsString)
    }
    else None
  }
}