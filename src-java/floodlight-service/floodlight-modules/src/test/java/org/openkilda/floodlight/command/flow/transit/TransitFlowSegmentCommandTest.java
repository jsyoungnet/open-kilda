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

package org.openkilda.floodlight.command.flow.transit;

import static org.easymock.EasyMock.expect;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;

import org.openkilda.floodlight.command.AbstractSpeakerCommandTest;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowTransitEncapsulation;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;

import java.util.concurrent.CompletableFuture;

abstract class TransitFlowSegmentCommandTest extends AbstractSpeakerCommandTest {
    protected static final FlowTransitEncapsulation encapsulationVlan = new FlowTransitEncapsulation(
            50, FlowEncapsulationType.TRANSIT_VLAN);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        expect(featureDetectorService.detectSwitch(sw)).andStubReturn(Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN));
        expect(featureDetectorService.detectSwitch(swNext)).andStubReturn(Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN));
    }

    @Test
    public void errorOnFlowMod() {
        switchFeaturesSetup(sw, true);
        replayAll();

        TransitFlowSegmentCommand command = makeCommand(encapsulationVlan);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);

        getWriteRecord(0).getFuture()
                .completeExceptionally(new SwitchErrorResponseException(
                        dpIdNext, of.errorMsgs().buildBadRequestErrorMsg().setCode(OFBadRequestCode.BAD_LEN).build()));
        verifyErrorCompletion(result, SwitchOperationException.class);
    }

    protected abstract TransitFlowSegmentCommand makeCommand(FlowTransitEncapsulation encapsulation);
}
