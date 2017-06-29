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

public abstract class AbstractRequest extends AbstractRequestResponse {

    public static abstract class Builder<T extends AbstractRequest> {
        private final ApiKeys apiKey;
        private final Short desiredVersion;

        public Builder(ApiKeys apiKey) {
            this(apiKey, null);
        }

        public Builder(ApiKeys apiKey, Short desiredVersion) {
            this.apiKey = apiKey;
            this.desiredVersion = desiredVersion;
        }

        public ApiKeys apiKey() {
            return apiKey;
        }

        public short desiredOrLatestVersion() {
            return desiredVersion == null ? apiKey.latestVersion() : desiredVersion;
        }

        public Short desiredVersion() {
            return desiredVersion;
        }

        public T build() {
            return build(desiredOrLatestVersion());
        }

        public abstract T build(short version);
    }

    private final short version;

    public AbstractRequest(short version) {
        this.version = version;
    }

    /**
     * Get the version of this AbstractRequest object.
     */
    public short version() {
        return version;
    }

    public Send toSend(String destination, RequestHeader header) {
        return new NetworkSend(destination, serialize(header));
    }

    /**
     * Use with care, typically {@link #toSend(String, RequestHeader)} should be used instead.
     */
    public ByteBuffer serialize(RequestHeader header) {
        return serialize(header.toStruct(), toStruct());
    }

    protected abstract Struct toStruct();

    public String toString(boolean verbose) {
        return toStruct().toString();
    }

    @Override
    public final String toString() {
        return toString(true);
    }

    /**
     * Get an error response for a request
     */
    public abstract AbstractResponse getErrorResponse(Throwable e);

    /**
     * Factory method for getting a request object based on ApiKey ID and a buffer
     */
    public static RequestAndSize getRequest(int requestId, short version, ByteBuffer buffer) {
        ApiKeys apiKey = ApiKeys.forId(requestId);
        Struct struct = apiKey.parseRequest(version, buffer);
        AbstractRequest request = null;
        switch (apiKey) {
            case PRODUCE:
                request = new ProduceRequest(struct, version);
                break;
            default:
                throw new AssertionError(String.format("ApiKey %s is not currently handled in `getRequest`, the " +
                        "code should be updated to do so.", apiKey));
        }
        return new RequestAndSize(request, struct.sizeOf());
    }
}
