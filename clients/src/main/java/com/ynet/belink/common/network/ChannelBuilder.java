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
package com.ynet.belink.common.network;


import com.ynet.belink.common.BelinkException;

import java.nio.channels.SelectionKey;
import java.util.Map;

/**
 * A ChannelBuilder interface to build Channel based on configs
 */
public interface ChannelBuilder {

    /**
     * Configure this class with the given key-value pairs
     */
    void configure(Map<String, ?> configs) throws BelinkException;


    /**
     * returns a Channel with TransportLayer and Authenticator configured.
     * @param  id  channel id
     * @param  key SelectionKey
     * @param  maxReceiveSize
     * @return KafkaChannel
     */
    BelinkChannel buildChannel(String id, SelectionKey key, int maxReceiveSize) throws BelinkException;


    /**
     * Closes ChannelBuilder
     */
    void close();

}
