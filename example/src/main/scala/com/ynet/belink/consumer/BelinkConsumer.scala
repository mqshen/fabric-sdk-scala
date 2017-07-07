package com.ynet.belink.consumer

import java.util.Properties

import com.ynet.belink.clients.consumer.BelinkConsumer

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

/**
  * Created by goldratio on 05/07/2017.
  */
object BelinkConsumerExample {

  def main(args: Array[String]): Unit = {
    val servers = "localhost:9092"
    val props = new Properties()
    props.put("bootstrap.servers", servers)
    props.put("group.id", "sunshine-notify")
    props.put("enable.auto.commit", "true")
    props.put("auto.commit.interval.ms", "1000")
    props.put("session.timeout.ms", "6000")
    props.put("key.deserializer", "com.ynet.belink.common.serialization.StringDeserializer")
    props.put("value.deserializer", "com.ynet.belink.common.serialization.StringDeserializer")

    val consumer = new BelinkConsumer[String, String](props)
    consumer.subscribe(Seq("data-log1"))
    consumer.poll(Long.MaxValue).asScala.foreach { record =>
      println(s"test for test:$record")
    }
  }

}
