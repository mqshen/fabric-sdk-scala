package belink.metrics

import com.yammer.metrics.core.Timer
/**
  * Created by goldratio on 29/06/2017.
  */
class BelinkTimer(metric: Timer) {

  def time[A](f: => A): A = {
    val ctx = metric.time
    try {
      f
    }
    finally {
      ctx.stop()
    }
  }
}
