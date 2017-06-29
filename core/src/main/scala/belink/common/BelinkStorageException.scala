package belink.common

/**
  * Created by goldratio on 29/06/2017.
  */
class BelinkStorageException(message: String, t: Throwable) extends RuntimeException(message, t) {
  def this(message: String) = this(message, null)
  def this(t: Throwable) = this("", t)
}
