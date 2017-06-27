package belink.utils

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ScheduledThreadPoolExecutor, ThreadFactory, TimeUnit}

import com.ynet.belink.common.utils.Utils

/**
  * Created by goldratio on 27/06/2017.
  */
trait Scheduler {

  /**
    * Initialize this scheduler so it is ready to accept scheduling of tasks
    */
  def startup()

  /**
    * Shutdown this scheduler. When this method is complete no more executions of background tasks will occur.
    * This includes tasks scheduled with a delayed execution.
    */
  def shutdown()

  /**
    * Check if the scheduler has been started
    */
  def isStarted: Boolean

  /**
    * Schedule a task
    * @param name The name of this task
    * @param delay The amount of time to wait before the first execution
    * @param period The period with which to execute the task. If < 0 the task will execute only once.
    * @param unit The unit for the preceding times.
    */
  def schedule(name: String, fun: ()=>Unit, delay: Long = 0, period: Long = -1, unit: TimeUnit = TimeUnit.MILLISECONDS)
}

@threadsafe
class BelinkScheduler(val threads: Int,
                     val threadNamePrefix: String = "kafka-scheduler-",
                     daemon: Boolean = true) extends Scheduler with Logging  {
  private var executor: ScheduledThreadPoolExecutor = null
  private val schedulerThreadId = new AtomicInteger(0)

  override def startup() {
    debug("Initializing task scheduler.")
    this synchronized {
      if(isStarted)
        throw new IllegalStateException("This scheduler has already been started!")
      executor = new ScheduledThreadPoolExecutor(threads)
      executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
      executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
      executor.setThreadFactory(new ThreadFactory() {
        def newThread(runnable: Runnable): Thread =
          Utils.newThread(threadNamePrefix + schedulerThreadId.getAndIncrement(), runnable, daemon)
      })
    }
  }

  override def shutdown() {
    debug("Shutting down task scheduler.")
    // We use the local variable to avoid NullPointerException if another thread shuts down scheduler at same time.
    val cachedExecutor = this.executor
    if (cachedExecutor != null) {
      this synchronized {
        cachedExecutor.shutdown()
        this.executor = null
      }
      cachedExecutor.awaitTermination(1, TimeUnit.DAYS)
    }
  }

  def schedule(name: String, fun: ()=>Unit, delay: Long, period: Long, unit: TimeUnit) = {
    debug("Scheduling task %s with initial delay %d ms and period %d ms."
      .format(name, TimeUnit.MILLISECONDS.convert(delay, unit), TimeUnit.MILLISECONDS.convert(period, unit)))
    this synchronized {
      ensureRunning
      val runnable = CoreUtils.runnable {
        try {
          trace("Beginning execution of scheduled task '%s'.".format(name))
          fun()
        } catch {
          case t: Throwable => error("Uncaught exception in scheduled task '" + name +"'", t)
        } finally {
          trace("Completed execution of scheduled task '%s'.".format(name))
        }
      }
      if(period >= 0)
        executor.scheduleAtFixedRate(runnable, delay, period, unit)
      else
        executor.schedule(runnable, delay, unit)
    }
  }

  def isStarted: Boolean = {
    this synchronized {
      executor != null
    }
  }

  private def ensureRunning = {
    if(!isStarted)
      throw new IllegalStateException("Kafka scheduler is not running.")
  }
}
