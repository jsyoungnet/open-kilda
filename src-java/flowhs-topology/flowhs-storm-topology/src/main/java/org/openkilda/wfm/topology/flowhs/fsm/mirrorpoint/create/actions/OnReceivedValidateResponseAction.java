/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedValidateResponseAction
        extends HistoryRecordingAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private  static final String RULE_VALIDATION_FAILED_ACTION = "Rule validation failed";

    private final int speakerCommandRetriesLimit;

    public OnReceivedValidateResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointCreateContext context,
                           FlowMirrorPointCreateFsm stateMachine) {
        SpeakerFlowSegmentResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();
        FlowSegmentRequestFactory command = stateMachine.getCommands().get(commandId);
        if (!stateMachine.getPendingCommands().containsKey(commandId) || command == null) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.getPendingCommands().remove(commandId);
            stateMachine.saveActionToHistory("Rule was validated",
                    format("The installed rule has been validated successfully: switch %s, cookie %s",
                            command.getSwitchId(), command.getCookie()));
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;

            int retries = stateMachine.getRetriedCommands().getOrDefault(commandId, 0);
            if (retries < speakerCommandRetriesLimit
                    && errorResponse.getErrorCode() != FlowErrorResponse.ErrorCode.MISSING_OF_FLOWS) {
                stateMachine.getRetriedCommands().put(commandId, ++retries);

                stateMachine.saveErrorToHistory(RULE_VALIDATION_FAILED_ACTION, format(
                        "Failed to validate non ingress rule: commandId %s, switch %s, cookie %s. Error %s. "
                                + "Retrying (attempt %d)",
                        commandId, errorResponse.getSwitchId(), command.getCookie(), errorResponse, retries));

                stateMachine.getCarrier().sendSpeakerRequest(command.makeVerifyRequest(commandId));
            } else {
                stateMachine.getPendingCommands().remove(commandId);

                stateMachine.saveErrorToHistory(RULE_VALIDATION_FAILED_ACTION,
                        format("Failed to validate non ingress rule: commandId %s, switch %s, cookie %s. Error %s",
                                commandId, errorResponse.getSwitchId(), command.getCookie(), errorResponse));

                stateMachine.getFailedValidationResponses().put(commandId, response);
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedValidationResponses().isEmpty()) {
                log.debug("Installed rules have been validated for flow {}", stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_VALIDATED);
            } else {
                stateMachine.saveErrorToHistory(format(
                        "Found missing rules or received error response(s) on %d validation commands",
                        stateMachine.getFailedValidationResponses().size()));
                stateMachine.fire(Event.MISSING_RULE_FOUND);
            }
        }
    }
}
