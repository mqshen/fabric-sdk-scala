package belink.controller

import java.util.concurrent.TimeUnit

import belink.metrics.{BelinkMetricsGroup, BelinkTimer}

import scala.collection.{Map, Seq, Set, mutable}

/**
  * Created by goldratio on 07/07/2017.
  */
class ControllerContext() {
  val stats = new ControllerStats



}

class BelinkController {
  val controllerContext = new ControllerContext()

  private[controller] val eventManager = new ControllerEventManager(controllerContext.stats.rateAndTimeMetrics,
    _ => updateMetrics())

  private def updateMetrics(): Unit = {

  }
}

private[controller] class ControllerStats extends BelinkMetricsGroup {
  val uncleanLeaderElectionRate = newMeter("UncleanLeaderElectionsPerSec", "elections", TimeUnit.SECONDS)

  val rateAndTimeMetrics: Map[ControllerState, BelinkTimer] = ControllerState.values.flatMap { state =>
    state.rateAndTimeMetricName.map { metricName =>
      state -> new BelinkTimer(newTimer(s"$metricName", TimeUnit.MILLISECONDS, TimeUnit.SECONDS))
    }
  }.toMap

}

sealed trait ControllerEvent {
  def state: ControllerState
  def process(): Unit
}