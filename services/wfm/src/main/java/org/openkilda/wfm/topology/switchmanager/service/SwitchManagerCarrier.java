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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.model.validate.ValidateSwitchReport;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.switchmanager.model.SwitchSyncData;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;

import java.util.List;

public interface SwitchManagerCarrier {
    void response(String key, Message message);

    // FIXME
    void cancelTimeoutCallback(String key);

    CommandContext getCommandContext();

    void runSwitchSync(String key, SwitchValidateRequest request, SwitchSyncData report);

    void speakerFetchSchema(SwitchId switchId, List<ValidateFlowSegmentDescriptor> segmentDescriptors);

    void syncSpeakerMessageRequest(CommandData command);

    void syncSpeakerFlowSegmentRequest(FlowSegmentRequest segmentRequest);
}
