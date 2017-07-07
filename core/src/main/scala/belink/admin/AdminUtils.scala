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

import belink.common.Topic
import belink.log.LogConfig
import belink.server.ConfigType
import belink.utils.Logging
import com.ynet.belink.common.errors.{InvalidPartitionsException, InvalidReplicaAssignmentException, InvalidReplicationFactorException}

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

  def assignReplicasToBrokers(brokerMetadatas: Seq[BrokerMetadata],
                              nPartitions: Int,
                              replicationFactor: Int,
                              fixedStartIndex: Int = -1,
                              startPartitionId: Int = -1): Map[Int, Seq[Int]] = {
    if (nPartitions <= 0)
      throw new InvalidPartitionsException("number of partitions must be larger than 0")
    if (replicationFactor <= 0)
      throw new InvalidReplicationFactorException("replication factor must be larger than 0")
    if (replicationFactor > brokerMetadatas.size)
      throw new InvalidReplicationFactorException(s"replication factor: $replicationFactor larger than available brokers: ${brokerMetadatas.size}")
    if (brokerMetadatas.forall(_.rack.isEmpty))
      assignReplicasToBrokersRackUnaware(nPartitions, replicationFactor, brokerMetadatas.map(_.id), fixedStartIndex,
        startPartitionId)
    else {
      if (brokerMetadatas.exists(_.rack.isEmpty))
        throw new AdminOperationException("Not all brokers have rack information for replica rack aware assignment")
      assignReplicasToBrokersRackAware(nPartitions, replicationFactor, brokerMetadatas, fixedStartIndex,
        startPartitionId)
    }
  }

  private def assignReplicasToBrokersRackUnaware(nPartitions: Int,
                                                 replicationFactor: Int,
                                                 brokerList: Seq[Int],
                                                 fixedStartIndex: Int,
                                                 startPartitionId: Int): Map[Int, Seq[Int]] = {
    val ret = mutable.Map[Int, Seq[Int]]()
    val brokerArray = brokerList.toArray
    val startIndex = if (fixedStartIndex >= 0) fixedStartIndex else rand.nextInt(brokerArray.length)
    var currentPartitionId = math.max(0, startPartitionId)
    var nextReplicaShift = if (fixedStartIndex >= 0) fixedStartIndex else rand.nextInt(brokerArray.length)
    for (_ <- 0 until nPartitions) {
      if (currentPartitionId > 0 && (currentPartitionId % brokerArray.length == 0))
        nextReplicaShift += 1
      val firstReplicaIndex = (currentPartitionId + startIndex) % brokerArray.length
      val replicaBuffer = mutable.ArrayBuffer(brokerArray(firstReplicaIndex))
      for (j <- 0 until replicationFactor - 1)
        replicaBuffer += brokerArray(replicaIndex(firstReplicaIndex, nextReplicaShift, j, brokerArray.length))
      ret.put(currentPartitionId, replicaBuffer)
      currentPartitionId += 1
    }
    ret
  }

  private def assignReplicasToBrokersRackAware(nPartitions: Int,
                                               replicationFactor: Int,
                                               brokerMetadatas: Seq[BrokerMetadata],
                                               fixedStartIndex: Int,
                                               startPartitionId: Int): Map[Int, Seq[Int]] = {
    val brokerRackMap = brokerMetadatas.collect { case BrokerMetadata(id, Some(rack)) =>
      id -> rack
    }.toMap
    val numRacks = brokerRackMap.values.toSet.size
    val arrangedBrokerList = getRackAlternatedBrokerList(brokerRackMap)
    val numBrokers = arrangedBrokerList.size
    val ret = mutable.Map[Int, Seq[Int]]()
    val startIndex = if (fixedStartIndex >= 0) fixedStartIndex else rand.nextInt(arrangedBrokerList.size)
    var currentPartitionId = math.max(0, startPartitionId)
    var nextReplicaShift = if (fixedStartIndex >= 0) fixedStartIndex else rand.nextInt(arrangedBrokerList.size)
    for (_ <- 0 until nPartitions) {
      if (currentPartitionId > 0 && (currentPartitionId % arrangedBrokerList.size == 0))
        nextReplicaShift += 1
      val firstReplicaIndex = (currentPartitionId + startIndex) % arrangedBrokerList.size
      val leader = arrangedBrokerList(firstReplicaIndex)
      val replicaBuffer = mutable.ArrayBuffer(leader)
      val racksWithReplicas = mutable.Set(brokerRackMap(leader))
      val brokersWithReplicas = mutable.Set(leader)
      var k = 0
      for (_ <- 0 until replicationFactor - 1) {
        var done = false
        while (!done) {
          val broker = arrangedBrokerList(replicaIndex(firstReplicaIndex, nextReplicaShift * numRacks, k, arrangedBrokerList.size))
          val rack = brokerRackMap(broker)
          // Skip this broker if
          // 1. there is already a broker in the same rack that has assigned a replica AND there is one or more racks
          //    that do not have any replica, or
          // 2. the broker has already assigned a replica AND there is one or more brokers that do not have replica assigned
          if ((!racksWithReplicas.contains(rack) || racksWithReplicas.size == numRacks)
            && (!brokersWithReplicas.contains(broker) || brokersWithReplicas.size == numBrokers)) {
            replicaBuffer += broker
            racksWithReplicas += rack
            brokersWithReplicas += broker
            done = true
          }
          k += 1
        }
      }
      ret.put(currentPartitionId, replicaBuffer)
      currentPartitionId += 1
    }
    ret
  }

  private[admin] def getRackAlternatedBrokerList(brokerRackMap: Map[Int, String]): IndexedSeq[Int] = {
    val brokersIteratorByRack = getInverseMap(brokerRackMap).map { case (rack, brokers) =>
      (rack, brokers.toIterator)
    }
    val racks = brokersIteratorByRack.keys.toArray.sorted
    val result = new mutable.ArrayBuffer[Int]
    var rackIndex = 0
    while (result.size < brokerRackMap.size) {
      val rackIterator = brokersIteratorByRack(racks(rackIndex))
      if (rackIterator.hasNext)
        result += rackIterator.next()
      rackIndex = (rackIndex + 1) % racks.length
    }
    result
  }

  private[admin] def getInverseMap(brokerRackMap: Map[Int, String]): Map[String, Seq[Int]] = {
    brokerRackMap.toSeq.map { case (id, rack) => (rack, id) }
      .groupBy { case (rack, _) => rack }
      .map { case (rack, rackAndIdList) => (rack, rackAndIdList.map { case (_, id) => id }.sorted) }
  }

  def getBrokerMetadatas(rackAwareMode: RackAwareMode = RackAwareMode.Enforced,
                         brokerList: Option[Seq[Int]] = None): Seq[BrokerMetadata] = {
    //TODO zk
    val allBrokers = Seq(BrokerMetadata(0, None))//zkUtils.getAllBrokersInCluster()
    val brokers = brokerList.map(brokerIds => allBrokers.filter(b => brokerIds.contains(b.id))).getOrElse(allBrokers)
    val brokersWithRack = brokers.filter(_.rack.nonEmpty)
    if (rackAwareMode == RackAwareMode.Enforced && brokersWithRack.nonEmpty && brokersWithRack.size < brokers.size) {
      throw new AdminOperationException("Not all brokers have rack information. Add --disable-rack-aware in command line" +
        " to make replica assignment without rack information.")
    }
    val brokerMetadatas = rackAwareMode match {
      case RackAwareMode.Disabled => brokers.map(broker => BrokerMetadata(broker.id, None))
      case RackAwareMode.Safe if brokersWithRack.size < brokers.size =>
        brokers.map(broker => BrokerMetadata(broker.id, None))
      case _ => brokers.map(broker => BrokerMetadata(broker.id, broker.rack))
    }
    brokerMetadatas.sortBy(_.id)
  }

  def createTopic(topic: String,
                  partitions: Int,
                  replicationFactor: Int,
                  topicConfig: Properties = new Properties,
                  rackAwareMode: RackAwareMode = RackAwareMode.Enforced) {
    val brokerMetadatas = getBrokerMetadatas(rackAwareMode)
    val replicaAssignment = AdminUtils.assignReplicasToBrokers(brokerMetadatas, partitions, replicationFactor)
    AdminUtils.createOrUpdateTopicPartitionAssignmentPathInZK(topic, replicaAssignment, topicConfig)
  }

  def createOrUpdateTopicPartitionAssignmentPathInZK( topic: String,
                                                     partitionReplicaAssignment: Map[Int, Seq[Int]],
                                                     config: Properties = new Properties,
                                                     update: Boolean = false) {
    validateCreateOrUpdateTopic(topic, partitionReplicaAssignment, config, update)
    //todo

    // Configs only matter if a topic is being created. Changing configs via AlterTopic is not supported
//    if (!update) {
//      // write out the config if there is any, this isn't transactional with the partition assignments
//      writeEntityConfig(getEntityConfigPath(ConfigType.Topic, topic), config)
//    }
//
//    // create the partition assignment
//    writeTopicPartitionAssignment(topic, partitionReplicaAssignment, update)
  }


  def validateCreateOrUpdateTopic(topic: String,
                                  partitionReplicaAssignment: Map[Int, Seq[Int]],
                                  config: Properties,
                                  update: Boolean): Unit = {
    // validate arguments
    Topic.validate(topic)

    if (partitionReplicaAssignment.values.map(_.size).toSet.size != 1)
      throw new InvalidReplicaAssignmentException("All partitions should have the same number of replicas")

    partitionReplicaAssignment.values.foreach(reps =>
      if (reps.size != reps.toSet.size)
        throw new InvalidReplicaAssignmentException("Duplicate replica assignment found: " + partitionReplicaAssignment)
    )


    // Configs only matter if a topic is being created. Changing configs via AlterTopic is not supported
    if (!update)
      LogConfig.validate(config)
  }
}
