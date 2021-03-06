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
package com.ynet.belink.common.header.internals;

import com.ynet.belink.common.MetricNameTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FetcherMetricsRegistry {

    public MetricNameTemplate fetchSizeAvg;
    public MetricNameTemplate fetchSizeMax;
    public MetricNameTemplate bytesConsumedRate;
    public MetricNameTemplate recordsPerRequestAvg;
    public MetricNameTemplate recordsConsumedRate;
    public MetricNameTemplate fetchLatencyAvg;
    public MetricNameTemplate fetchLatencyMax;
    public MetricNameTemplate fetchRequestRate;
    public MetricNameTemplate recordsLagMax;
    public MetricNameTemplate fetchThrottleTimeAvg;
    public MetricNameTemplate fetchThrottleTimeMax;
    public MetricNameTemplate topicFetchSizeAvg;
    public MetricNameTemplate topicFetchSizeMax;
    public MetricNameTemplate topicBytesConsumedRate;
    public MetricNameTemplate topicRecordsPerRequestAvg;
    public MetricNameTemplate topicRecordsConsumedRate;
    public MetricNameTemplate partitionRecordsLag;
    public MetricNameTemplate partitionRecordsLagMax;
    public MetricNameTemplate partitionRecordsLagAvg;

    public FetcherMetricsRegistry() {
        this(new HashSet<String>(), "");
    }

    public FetcherMetricsRegistry(String metricGrpPrefix) {
        this(new HashSet<String>(), metricGrpPrefix);
    }

    public FetcherMetricsRegistry(Set<String> tags, String metricGrpPrefix) {
        
        /***** Client level *****/
        String groupName = metricGrpPrefix + "-fetch-manager-metrics";
                
        this.fetchSizeAvg = new MetricNameTemplate("fetch-size-avg", groupName, 
                "The average number of bytes fetched per request", tags);

        this.fetchSizeMax = new MetricNameTemplate("fetch-size-max", groupName, 
                "The maximum number of bytes fetched per request", tags);
        this.bytesConsumedRate = new MetricNameTemplate("bytes-consumed-rate", groupName, 
                "The average number of bytes consumed per second", tags);

        this.recordsPerRequestAvg = new MetricNameTemplate("records-per-request-avg", groupName, 
                "The average number of records in each request", tags);
        this.recordsConsumedRate = new MetricNameTemplate("records-consumed-rate", groupName, 
                "The average number of records consumed per second", tags);

        this.fetchLatencyAvg = new MetricNameTemplate("fetch-latency-avg", groupName, 
                "The average time taken for a fetch request.", tags);
        this.fetchLatencyMax = new MetricNameTemplate("fetch-latency-max", groupName, 
                "The max time taken for any fetch request.", tags);
        this.fetchRequestRate = new MetricNameTemplate("fetch-rate", groupName, 
                "The number of fetch requests per second.", tags);

        this.recordsLagMax = new MetricNameTemplate("records-lag-max", groupName, 
                "The maximum lag in terms of number of records for any partition in this window", tags);

        this.fetchThrottleTimeAvg = new MetricNameTemplate("fetch-throttle-time-avg", groupName, 
                "The average throttle time in ms", tags);
        this.fetchThrottleTimeMax = new MetricNameTemplate("fetch-throttle-time-max", groupName, 
                "The maximum throttle time in ms", tags);

        /***** Topic level *****/
        Set<String> topicTags = new HashSet<>(tags);
        topicTags.add("topic");

        this.topicFetchSizeAvg = new MetricNameTemplate("fetch-size-avg", groupName, 
                "The average number of bytes fetched per request for a topic", topicTags);
        this.topicFetchSizeMax = new MetricNameTemplate("fetch-size-max", groupName, 
                "The maximum number of bytes fetched per request for a topic", topicTags);
        this.topicBytesConsumedRate = new MetricNameTemplate("bytes-consumed-rate", groupName, 
                "The average number of bytes consumed per second for a topic", topicTags);

        this.topicRecordsPerRequestAvg = new MetricNameTemplate("records-per-request-avg", groupName, 
                "The average number of records in each request for a topic", topicTags);
        this.topicRecordsConsumedRate = new MetricNameTemplate("records-consumed-rate", groupName, 
                "The average number of records consumed per second for a topic", topicTags);
        
        /***** Partition level *****/
        this.partitionRecordsLag = new MetricNameTemplate("{topic}-{partition}.records-lag", groupName, 
                "The latest lag of the partition", tags);
        this.partitionRecordsLagMax = new MetricNameTemplate("{topic}-{partition}.records-lag-max", groupName, 
                "The max lag of the partition", tags);
        this.partitionRecordsLagAvg = new MetricNameTemplate("{topic}-{partition}.records-lag-avg", groupName, 
                "The average lag of the partition", tags);
        
    
    }
    
    public List<MetricNameTemplate> getAllTemplates() {
        return Arrays.asList(
            fetchSizeAvg,
            fetchSizeMax,
            bytesConsumedRate,
            recordsPerRequestAvg,
            recordsConsumedRate,
            fetchLatencyAvg,
            fetchLatencyMax,
            fetchRequestRate,
            recordsLagMax,
            fetchThrottleTimeAvg,
            fetchThrottleTimeMax,
            topicFetchSizeAvg,
            topicFetchSizeMax,
            topicBytesConsumedRate,
            topicRecordsPerRequestAvg,
            topicRecordsConsumedRate,
            partitionRecordsLag,
            partitionRecordsLagAvg,
            partitionRecordsLagMax
        );
    }

}
