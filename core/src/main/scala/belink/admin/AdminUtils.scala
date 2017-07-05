/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package belink.admin

import java.util.Random
import java.util.Properties

import belink.utils.Logging

import scala.collection._
import scala.collection.JavaConverters._
import mutable.ListBuffer
import scala.collection.mutable
import collection.Map
import collection.Set

trait AdminUtilities {
  def changeTopicConfig(topic: String, configs: Properties)
  def changeClientIdConfig(clientId: String, configs: Properties)
  def changeUserOrUserClientIdConfig(sanitizedEntityName: String, configs: Properties)
  def changeBrokerConfig(brokerIds: Seq[Int], configs: Properties)
  def fetchEntityConfig(entityType: String, entityName: String): Properties
}

object AdminUtils extends Logging with AdminUtilities {
  val rand = new Random
  val AdminClientId = "__admin_client"
  val EntityConfigChangeZnodePrefix = "config_change_"

  private def replicaIndex(firstReplicaIndex: Int, secondReplicaShift: Int, replicaIndex: Int, nBrokers: Int): Int = {
    val shift = 1 + (secondReplicaShift + replicaIndex) % (nBrokers - 1)
    (firstReplicaIndex + shift) % nBrokers
  }

  override def changeTopicConfig(topic: String, configs: Properties): Unit = {

  }

  override def changeClientIdConfig(clientId: String, configs: Properties): Unit = {

  }

  override def changeUserOrUserClientIdConfig(sanitizedEntityName: String, configs: Properties): Unit = {

  }

  override def changeBrokerConfig(brokerIds: Seq[Int], configs: Properties): Unit = {

  }

  override def fetchEntityConfig(entityType: String, entityName: String): Properties = {
    null
  }
}
