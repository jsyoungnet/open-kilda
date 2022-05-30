/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.UUID;

@Data
public class MessageContext implements Serializable {
    @JsonProperty("correlation_id")
    private final String correlationId;

    @JsonProperty("create_time")
    private final long createTime;

    @JsonProperty("worker_send_time")
    public long workerSendTime;

    @JsonProperty("worker_receive_time")
    public long workerReceiveTime;

    public MessageContext() {
        this(UUID.randomUUID().toString());
    }

    public MessageContext(Message message) {
        this(message.getCorrelationId(), message.getTimestamp(), 0, 0);
    }

    public MessageContext(String correlationId) {
        this(correlationId, System.currentTimeMillis(), 0, 0);
    }

    public MessageContext(String operationId, String correlationId) {
        this(StringUtils.joinWith(" : ", operationId, correlationId), System.currentTimeMillis(), 0, 0);
    }

    @JsonCreator
    public MessageContext(
            @JsonProperty("correlation_id") @NonNull String correlationId,
            @JsonProperty("create_time") long createTime,
            @JsonProperty("worker_send_time") long workerSendTime,
            @JsonProperty("worker_receive_time") long workerReceiveTime) {
        this.correlationId = correlationId;
        this.createTime = createTime;
        this.workerSendTime = workerSendTime;
        this.workerReceiveTime = workerReceiveTime;
    }

    /**
     * Create new {@link MessageContext} object using data from current one. Produced object receive extended/nested
     * correlation ID i.e. it contain original correlation ID plus part passed in argument.
     */
    public MessageContext fork(String correlationIdExtension) {
        return new MessageContext(correlationIdExtension + " : " + correlationId);
    }
}
