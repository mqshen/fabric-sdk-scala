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

import com.ynet.belink.common.errors.UnsupportedVersionException;
import com.ynet.belink.common.protocol.ApiKeys;
import com.ynet.belink.common.protocol.Errors;
import com.ynet.belink.common.protocol.types.Struct;
import com.ynet.belink.common.utils.Utils;

import java.nio.ByteBuffer;
import java.util.*;

public class ProduceRequest extends AbstractRequest {
    private static final String TRANSACTIONAL_ID_KEY_NAME = "transactional_id";
    private static final String ACKS_KEY_NAME = "acks";
    private static final String TIMEOUT_KEY_NAME = "timeout";
    private static final String TOPIC_DATA_KEY_NAME = "topic_data";

    // topic level field names
    private static final String TOPIC_KEY_NAME = "topic";
    private static final String PARTITION_DATA_KEY_NAME = "data";

    // partition level field names
    private static final String PARTITION_KEY_NAME = "partition";
    private static final String RECORD_SET_KEY_NAME = "record_set";

    public static class Builder extends AbstractRequest.Builder<ProduceRequest> {
        private final byte magic;
        private final short acks;
        private final int timeout;

        public Builder(byte magic,
                       short acks,
                       int timeout) {
            super(ApiKeys.PRODUCE, (short) 3);
            this.magic = magic;
            this.acks = acks;
            this.timeout = timeout;
        }

        @Override
        public ProduceRequest build(short version) {
            if (version < 2)
                throw new UnsupportedVersionException("ProduceRequest versions older than 2 are not supported.");

            return new ProduceRequest(version, acks, timeout);
        }

        @Override
        public String toString() {
            StringBuilder bld = new StringBuilder();
            bld.append("(type=ProduceRequest")
                    .append(", magic=").append(magic)
                    .append(", acks=").append(acks)
                    .append(", timeout=").append(timeout)
                    .append("))");
            return bld.toString();
        }
    }

    private final short acks;
    private final int timeout;
    private final String transactionalId;



    private ProduceRequest(short version, short acks, int timeout) {
        super(version);
        this.acks = acks;
        this.timeout = timeout;

        // TODO: Include transactional id in constructor once transactions are supported
        this.transactionalId = null;

    }


    public ProduceRequest(Struct struct, short version) {
        super(version);
        for (Object topicDataObj : struct.getArray(TOPIC_DATA_KEY_NAME)) {
            Struct topicData = (Struct) topicDataObj;
            String topic = topicData.getString(TOPIC_KEY_NAME);
            for (Object partitionResponseObj : topicData.getArray(PARTITION_DATA_KEY_NAME)) {
                Struct partitionResponse = (Struct) partitionResponseObj;
                int partition = partitionResponse.getInt(PARTITION_KEY_NAME);
            }
        }
        acks = struct.getShort(ACKS_KEY_NAME);
        timeout = struct.getInt(TIMEOUT_KEY_NAME);
        transactionalId = struct.hasField(TRANSACTIONAL_ID_KEY_NAME) ? struct.getString(TRANSACTIONAL_ID_KEY_NAME) : null;
    }


    /**
     * Visible for testing.
     */
    @Override
    public Struct toStruct() {
        // Store it in a local variable to protect against concurrent updates
        short version = version();
        Struct struct = new Struct(ApiKeys.PRODUCE.requestSchema(version));
        struct.set(ACKS_KEY_NAME, acks);
        struct.set(TIMEOUT_KEY_NAME, timeout);

        if (struct.hasField(TRANSACTIONAL_ID_KEY_NAME))
            struct.set(TRANSACTIONAL_ID_KEY_NAME, transactionalId);

        List<Struct> topicDatas = new ArrayList<>();

        struct.set(TOPIC_DATA_KEY_NAME, topicDatas.toArray());
        return struct;
    }

    @Override
    public String toString(boolean verbose) {
        // Use the same format as `Struct.toString()`
        StringBuilder bld = new StringBuilder();
        bld.append("{acks=").append(acks)
                .append(",timeout=").append(timeout);

        bld.append("}");
        return bld.toString();
    }

    @Override
    public AbstractResponse getErrorResponse(Throwable e) {
        /* In case the producer doesn't actually want any response */
        if (acks == 0)
            return null;

        short versionId = version();
        switch (versionId) {
            case 0:
            case 1:
            case 2:
            case 3:
            default:
                throw new IllegalArgumentException(String.format("Version %d is not valid. Valid versions for %s are 0 to %d",
                        versionId, this.getClass().getSimpleName(), ApiKeys.PRODUCE.latestVersion()));
        }
    }

    public short acks() {
        return acks;
    }

    public int timeout() {
        return timeout;
    }

    public String transactionalId() {
        return transactionalId;
    }

    public static ProduceRequest parse(ByteBuffer buffer, short version) {
        return new ProduceRequest(ApiKeys.PRODUCE.parseRequest(version, buffer), version);
    }

}
