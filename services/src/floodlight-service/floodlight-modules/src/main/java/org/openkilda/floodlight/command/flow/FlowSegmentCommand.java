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

package org.openkilda.floodlight.command.flow;

import org.openkilda.model.of.FlowSegmentSchema;
import org.openkilda.model.of.MeterSchema;
import org.openkilda.floodlight.command.SpeakerRemoteCommand;
import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.converter.FlowSegmentSchemaMapper;
import org.openkilda.floodlight.error.SwitchMissingFlowsException;
import org.openkilda.floodlight.utils.OfFlowDumpProducer;
import org.openkilda.floodlight.utils.OfFlowPresenceVerifier;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public abstract class FlowSegmentCommand extends SpeakerRemoteCommand<FlowSegmentReport> {
    protected static final long FLOW_COOKIE_MASK = 0x7FFFFFFFFFFFFFFFL;
    public static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;

    // payload
    protected final String flowId;
    protected final Cookie cookie;

    public FlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie) {
        super(messageContext, switchId, commandId);
        this.flowId = flowId;
        this.cookie = cookie;
    }

    protected CompletableFuture<FlowSegmentReport> makeVerifyPlan(List<OFFlowMod> expected) {
        OfFlowDumpProducer dumper = new OfFlowDumpProducer(messageContext, getSw(), expected);
        OfFlowPresenceVerifier verifier = new OfFlowPresenceVerifier(dumper, expected);
        return verifier.getFinish()
                .thenApply(verifyResults -> handleVerifyResponse(expected, verifyResults));
    }

    protected CompletableFuture<FlowSegmentReport> makeSchemaPlan(List<OFFlowMod> requests) {
        return makeSchemaPlan(null, requests);
    }

    protected CompletableFuture<FlowSegmentReport> makeSchemaPlan(MeterReport meterReport, List<OFFlowMod> requests) {
        List<MeterSchema> meters = meterReport != null ? ImmutableList.of(meterReport.getSchema()) : null;
        FlowSegmentSchema schema = FlowSegmentSchemaMapper.INSTANCE.toFlowSegmentSchema(getSw(), meters, requests);
        return CompletableFuture.completedFuture(new FlowSegmentSchemaReport(this, schema));
    }

    protected FlowSegmentReport makeReport(Exception error) {
        return new FlowSegmentReport(this, error);
    }

    protected FlowSegmentReport makeSuccessReport() {
        return new FlowSegmentReport(this);
    }

    protected abstract OFFlowMod.Builder makeFlowModBuilder(OFFactory of);

    protected OFFlowAdd.Builder makeFlowAddBuilder(OFFactory of) {
        return of.buildFlowAdd()
                .setPriority(FLOW_PRIORITY)
                .setCookie(U64.of(cookie.getValue()));
    }

    protected OFFlowDeleteStrict.Builder makeFlowDelBuilder(OFFactory of) {
        return of.buildFlowDeleteStrict()
                .setPriority(FLOW_PRIORITY)
                .setCookie(U64.of(cookie.getValue()));
    }

    private FlowSegmentReport handleVerifyResponse(List<OFFlowMod> expected, OfFlowPresenceVerifier verifier) {
        List<OFFlowMod> missing = verifier.getMissing();
        if (missing.isEmpty()) {
            return makeSuccessReport();
        }

        return new FlowSegmentReport(
                this, new SwitchMissingFlowsException(
                        getSw().getId(), getFlowId(), getCookie(), expected, missing));
    }
}
