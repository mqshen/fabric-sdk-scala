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
package com.ynet.belink.common.requests;

import com.ynet.belink.common.network.NetworkSend;
import com.ynet.belink.common.network.Send;
import com.ynet.belink.common.protocol.ApiKeys;
import com.ynet.belink.common.protocol.types.Struct;

import java.nio.ByteBuffer;

public abstract class AbstractResponse extends AbstractRequestResponse {
    public static final String THROTTLE_TIME_KEY_NAME = "throttle_time_ms";
    public static final int DEFAULT_THROTTLE_TIME = 0;

    public Send toSend(String destination, RequestHeader requestHeader) {
        return toSend(destination, requestHeader.apiVersion(), requestHeader.toResponseHeader());
    }

    /**
     * This should only be used if we need to return a response with a different version than the request, which
     * should be very rare (an example is @link {@link ApiVersionsResponse#unsupportedVersionSend(String, RequestHeader)}).
     * Typically {@link #toSend(String, RequestHeader)} should be used.
     */
    public Send toSend(String destination, short version, ResponseHeader responseHeader) {
        return new NetworkSend(destination, serialize(version, responseHeader));
    }

    /**
     * Visible for testing, typically {@link #toSend(String, RequestHeader)} should be used instead.
     */
    public ByteBuffer serialize(short version, ResponseHeader responseHeader) {
        return serialize(responseHeader.toStruct(), toStruct(version));
    }

    protected abstract Struct toStruct(short version);

    public static AbstractResponse getResponse(ApiKeys apiKey, Struct struct) {
        switch (apiKey) {
            case PRODUCE:
                return new ProduceResponse(struct);
            case FETCH:
                return new FetchResponse(struct);
            case LIST_OFFSETS:
                return new ListOffsetResponse(struct);
            case METADATA:
                return new MetadataResponse(struct);
            case OFFSET_COMMIT:
                return new OffsetCommitResponse(struct);
            case OFFSET_FETCH:
                return new OffsetFetchResponse(struct);
            case JOIN_GROUP:
                return new JoinGroupResponse(struct);
            case HEARTBEAT:
                return new HeartbeatResponse(struct);
            case LEAVE_GROUP:
                return new LeaveGroupResponse(struct);
            case SYNC_GROUP:
                return new SyncGroupResponse(struct);
            case SASL_HANDSHAKE:
                return new SaslHandshakeResponse(struct);
            case API_VERSIONS:
                return new ApiVersionsResponse(struct);
            default:
                throw new AssertionError(String.format("ApiKey %s is not currently handled in `getResponse`, the " +
                        "code should be updated to do so.", apiKey));
        }
    }

    public String toString(short version) {
        return toStruct(version).toString();
    }
}
