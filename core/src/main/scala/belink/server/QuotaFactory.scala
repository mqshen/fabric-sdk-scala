/**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package belink.server

import belink.server.QuotaType._
import belink.utils.Logging
import com.ynet.belink.common.TopicPartition
import com.ynet.belink.common.metrics.Metrics
import com.ynet.belink.common.utils.Time

object QuotaType  {
  case object Fetch extends QuotaType
  case object Produce extends QuotaType
  case object Request extends QuotaType
  case object LeaderReplication extends QuotaType
  case object FollowerReplication extends QuotaType
}
sealed trait QuotaType

object QuotaFactory extends Logging {

  object UnboundedQuota extends ReplicaQuota {
    override def isThrottled(topicPartition: TopicPartition): Boolean = false
    override def isQuotaExceeded(): Boolean = false
  }

  case class QuotaManagers(fetch: ClientQuotaManager, produce: ClientQuotaManager, request: ClientRequestQuotaManager, leader: ReplicationQuotaManager, follower: ReplicationQuotaManager) {
    def shutdown() {
      fetch.shutdown
      produce.shutdown
      request.shutdown
    }
  }

  def instantiate(cfg: BelinkConfig, metrics: Metrics, time: Time): QuotaManagers = {
    QuotaManagers(
      new ClientQuotaManager(clientFetchConfig(cfg), metrics, Fetch, time),
      new ClientQuotaManager(clientProduceConfig(cfg), metrics, Produce, time),
      new ClientRequestQuotaManager(clientRequestConfig(cfg), metrics, time),
      new ReplicationQuotaManager(replicationConfig(cfg), metrics, LeaderReplication, time),
      new ReplicationQuotaManager(replicationConfig(cfg), metrics, FollowerReplication, time)
    )
  }

  def clientProduceConfig(cfg: BelinkConfig): ClientQuotaManagerConfig = {
    if (cfg.producerQuotaBytesPerSecondDefault != Long.MaxValue)
      warn(s"${BelinkConfig.ProducerQuotaBytesPerSecondDefaultProp} has been deprecated in 0.11.0.0 and will be removed in a future release. Use dynamic quota defaults instead.")
    ClientQuotaManagerConfig(
      quotaBytesPerSecondDefault = cfg.producerQuotaBytesPerSecondDefault,
      numQuotaSamples = cfg.numQuotaSamples,
      quotaWindowSizeSeconds = cfg.quotaWindowSizeSeconds
    )
  }

  def clientFetchConfig(cfg: BelinkConfig): ClientQuotaManagerConfig = {
    if (cfg.consumerQuotaBytesPerSecondDefault != Long.MaxValue)
      warn(s"${BelinkConfig.ConsumerQuotaBytesPerSecondDefaultProp} has been deprecated in 0.11.0.0 and will be removed in a future release. Use dynamic quota defaults instead.")
    ClientQuotaManagerConfig(
      quotaBytesPerSecondDefault = cfg.consumerQuotaBytesPerSecondDefault,
      numQuotaSamples = cfg.numQuotaSamples,
      quotaWindowSizeSeconds = cfg.quotaWindowSizeSeconds
    )
  }

  def clientRequestConfig(cfg: BelinkConfig): ClientQuotaManagerConfig = {
    ClientQuotaManagerConfig(
      numQuotaSamples = cfg.numQuotaSamples,
      quotaWindowSizeSeconds = cfg.quotaWindowSizeSeconds
    )
  }

  def replicationConfig(cfg: BelinkConfig): ReplicationQuotaManagerConfig =
    ReplicationQuotaManagerConfig(
      numQuotaSamples = cfg.numReplicationQuotaSamples,
      quotaWindowSizeSeconds = cfg.replicationQuotaWindowSizeSeconds
    )
}
