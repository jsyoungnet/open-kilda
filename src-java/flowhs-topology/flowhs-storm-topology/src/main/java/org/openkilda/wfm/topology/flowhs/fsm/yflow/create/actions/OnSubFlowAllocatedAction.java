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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowMapper;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateService;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class OnSubFlowAllocatedAction extends
        NbTrackableWithHistorySupportAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final FlowCreateService flowCreateService;
    private final YFlowRepository yFlowRepository;

    public OnSubFlowAllocatedAction(FlowCreateService flowCreateService, PersistenceManager persistenceManager) {
        super(persistenceManager);
        this.flowCreateService = flowCreateService;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowCreateContext context,
                                                    YFlowCreateFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isCreatingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        String yFlowId = stateMachine.getYFlowId();
        stateMachine.saveActionToHistory("Creating a sub-flow",
                format("Allocated resources for sub-flow %s of y-flow %s", subFlowId, yFlowId));

        stateMachine.addAllocatedSubFlow(subFlowId);

        if (subFlowId.equals(stateMachine.getMainAffinityFlowId())) {
            stateMachine.getRequestedFlows().forEach(requestedFlow -> {
                String requestedFlowId = requestedFlow.getFlowId();
                if (!requestedFlowId.equals(subFlowId)) {
                    stateMachine.addSubFlow(requestedFlowId);
                    stateMachine.addCreatingSubFlow(requestedFlowId);
                    stateMachine.notifyEventListeners(listener ->
                            listener.onSubFlowProcessingStart(yFlowId, requestedFlowId));
                    if (!requestedFlow.getSrcSwitch().equals(requestedFlow.getDestSwitch())) {
                        // One-switch flow can't be added to an affinity group.
                        requestedFlow.setAffinityFlowId(stateMachine.getMainAffinityFlowId());
                    }
                    requestedFlow.setDiverseFlowId(stateMachine.getDiverseFlowId());
                    requestedFlow.setYFlowId(stateMachine.getYFlowId());
                    CommandContext flowContext = stateMachine.getCommandContext().fork(requestedFlowId);
                    flowCreateService.startFlowCreation(flowContext, requestedFlow, yFlowId);
                }
            });
        }

        if (stateMachine.getAllocatedSubFlows().size() == stateMachine.getSubFlows().size()) {
            YFlow result = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                            format("Y-flow %s not found", yFlowId)));
            return Optional.of(buildResponseMessage(result, stateMachine.getCommandContext()));
        } else {
            return Optional.empty();
        }
    }

    private Message buildResponseMessage(YFlow yFlow, CommandContext commandContext) {
        YFlowResponse response = YFlowResponse.builder()
                .yFlow(YFlowMapper.INSTANCE.toYFlowDto(yFlow, flowRepository))
                .build();
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create y-flow";
    }
}
