package belink.server

import java.util.Properties

/**
  * Created by goldratio on 30/06/2017.
  */
trait ConfigHandler {
  def processConfigChanges(entityName: String, value: Properties)
}
