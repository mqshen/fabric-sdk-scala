package belink.common

/**
  * Created by goldratio on 30/06/2017.
  */
trait NotificationHandler {
  def processNotification(notificationMessage: String)
}
