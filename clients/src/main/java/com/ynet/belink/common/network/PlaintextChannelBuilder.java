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
import com.ynet.belink.common.security.auth.PrincipalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectionKey;
import java.util.Map;

public class PlaintextChannelBuilder implements ChannelBuilder {
    private static final Logger log = LoggerFactory.getLogger(PlaintextChannelBuilder.class);
    private PrincipalBuilder principalBuilder;
    private Map<String, ?> configs;

    public void configure(Map<String, ?> configs) throws BelinkException {
        try {
            this.configs = configs;
            principalBuilder = ChannelBuilders.createPrincipalBuilder(configs);
        } catch (Exception e) {
            throw new BelinkException(e);
        }
    }

    public BelinkChannel buildChannel(String id, SelectionKey key, int maxReceiveSize) throws BelinkException {
        try {
            PlaintextTransportLayer transportLayer = new PlaintextTransportLayer(key);
            Authenticator authenticator = new DefaultAuthenticator();
            authenticator.configure(transportLayer, this.principalBuilder, this.configs);
            return new BelinkChannel(id, transportLayer, authenticator, maxReceiveSize);
        } catch (Exception e) {
            log.warn("Failed to create channel due to ", e);
            throw new BelinkException(e);
        }
    }

    public void close() {
        this.principalBuilder.close();
    }

}
