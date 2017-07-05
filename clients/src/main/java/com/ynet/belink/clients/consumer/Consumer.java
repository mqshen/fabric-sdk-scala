/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ynet.belink.clients.consumer;

import  com.ynet.belink.common.Metric;
import  com.ynet.belink.common.MetricName;
import  com.ynet.belink.common.PartitionInfo;
import  com.ynet.belink.common.TopicPartition;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @see BelinkConsumer
 */
public interface Consumer<K, V> extends Closeable {

    /**
     * @see BelinkConsumer#assignment()
     */
    public Set<TopicPartition> assignment();

    /**
     * @see BelinkConsumer#subscription()
     */
    public Set<String> subscription();

    void subscribe(Collection<String> topics, ConsumerRebalanceListener listener);

    /**
     * @see BelinkConsumer#subscribe(Collection)
     */
    public void subscribe(Collection<String> topics);


    /**
     * @see BelinkConsumer#assign(Collection)
     */
    public void assign(Collection<TopicPartition> partitions);


    void subscribe(Pattern pattern, ConsumerRebalanceListener listener);

    /**
     * @see BelinkConsumer#unsubscribe()
     */
    public void unsubscribe();

    /**
     * @see BelinkConsumer#poll(long)
     */
    public ConsumerRecords<K, V> poll(long timeout);

    /**
     * @see BelinkConsumer#commitSync()
     */
    public void commitSync();

    /**
     * @see BelinkConsumer#commitSync(Map)
     */
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets);

    /**
     * @see BelinkConsumer#commitAsync()
     */
    public void commitAsync();

    /**
     * @see BelinkConsumer#commitAsync(OffsetCommitCallback)
     */
    public void commitAsync(OffsetCommitCallback callback);

    /**
     * @see BelinkConsumer#commitAsync(Map, OffsetCommitCallback)
     */
    public void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback);

    /**
     * @see BelinkConsumer#seek(TopicPartition, long)
     */
    public void seek(TopicPartition partition, long offset);

    /**
     * @see BelinkConsumer#seekToBeginning(Collection)
     */
    public void seekToBeginning(Collection<TopicPartition> partitions);

    /**
     * @see BelinkConsumer#seekToEnd(Collection)
     */
    public void seekToEnd(Collection<TopicPartition> partitions);

    /**
     * @see BelinkConsumer#position(TopicPartition)
     */
    public long position(TopicPartition partition);

    /**
     * @see BelinkConsumer#committed(TopicPartition)
     */
    public OffsetAndMetadata committed(TopicPartition partition);

    /**
     * @see BelinkConsumer#metrics()
     */
    public Map<MetricName, ? extends Metric> metrics();

    /**
     * @see BelinkConsumer#partitionsFor(String)
     */
    public List<PartitionInfo> partitionsFor(String topic);

    /**
     * @see BelinkConsumer#listTopics()
     */
    public Map<String, List<PartitionInfo>> listTopics();

    /**
     * @see BelinkConsumer#paused()
     */
    public Set<TopicPartition> paused();

    /**
     * @see BelinkConsumer#pause(Collection)
     */
    public void pause(Collection<TopicPartition> partitions);

    /**
     * @see BelinkConsumer#resume(Collection)
     */
    public void resume(Collection<TopicPartition> partitions);

    /**
     * @see BelinkConsumer#offsetsForTimes(Map)
     */
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch);

    /**
     * @see BelinkConsumer#beginningOffsets(Collection)
     */
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions);

    /**
     * @see BelinkConsumer#endOffsets(Collection)
     */
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions);

    /**
     * @see BelinkConsumer#close()
     */
    public void close();

    /**
     * @see BelinkConsumer#close(long, TimeUnit)
     */
    public void close(long timeout, TimeUnit unit);

    /**
     * @see BelinkConsumer#wakeup()
     */
    public void wakeup();

}
