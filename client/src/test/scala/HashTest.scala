import org.hyperledger.fabric.sdk.utils.StringUtil

/**
  * Created by goldratio on 24/02/2017.
  */
object HashTest {

  def hex2bytes(hex: String): Array[Byte] = {
    hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }

  def main(args: Array[String]): Unit = {
    val test = hex2bytes("0726881c139ce7fa69be86f4c4a57778728b89856d9b5a963389017ad83c803a")
    println(StringUtil.toHexStringPadded(test))
  }

}
