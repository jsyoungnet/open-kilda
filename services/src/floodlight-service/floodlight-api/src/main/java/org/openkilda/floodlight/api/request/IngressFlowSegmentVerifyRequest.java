/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.api.request;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class IngressFlowSegmentVerifyRequest extends IngressFlowSegmentBlankRequest {
    @JsonCreator
    @Builder(toBuilder = true)
    public IngressFlowSegmentVerifyRequest(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("flowid") String flowId,
            @JsonProperty("cookie") Cookie cookie,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("meter_config") MeterConfig meterConfig,
            @JsonProperty("islPort") Integer islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation) {
        super(messageContext, commandId, flowId, cookie, endpoint, meterConfig, islPort, encapsulation);
    }

    public IngressFlowSegmentVerifyRequest(IngressFlowSegmentBlankRequest other) {
        super(other);
    }
}