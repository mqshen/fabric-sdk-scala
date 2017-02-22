package org.hyperledger.fabric.sdk.utils

/**
  * Created by goldratio on 22/02/2017.
  */
object CoreUtils {
  /**
    * Do the given action and log any exceptions thrown without rethrowing them
    * @param log The log method to use for logging. E.g. logger.warn
    * @param action The action to execute
    */
  def swallow(log: (Object, Throwable) => Unit, action: => Unit) {
    try {
      action
    } catch {
      case e: Throwable => log(e.getMessage(), e)
    }
  }
}
