/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class OnSubFlowAllocatedAction extends
        NbTrackableWithHistorySupportAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    private final YFlowRepository yFlowRepository;

    public OnSubFlowAllocatedAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowUpdateContext context,
                                                    YFlowUpdateFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isUpdatingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        String yFlowId = stateMachine.getYFlowId();
        stateMachine.saveActionToHistory("Updating a sub-flow",
                format("Allocated resources for sub-flow %s of y-flow %s", subFlowId, yFlowId));

        stateMachine.addAllocatedSubFlow(subFlowId);

        SubFlowDto subFlowDto = stateMachine.getTargetFlow().getSubFlows().stream()
                .filter(f -> f.getFlowId().equals(subFlowId))
                .findAny()
                .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                        format("Can't find definition of updated sub-flow %s", subFlowId)));
        SubFlowSharedEndpointEncapsulation sharedEndpoint = subFlowDto.getSharedEndpoint();
        FlowEndpoint endpoint = subFlowDto.getEndpoint();

        log.debug("Start updating sub-flow references from {} to y-flow {}", subFlowId,
                stateMachine.getYFlowId());

        YFlow result = transactionManager.doInTransaction(() -> {
            YFlow yFlow = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                            format("Y-flow %s not found", yFlowId)));
            Flow flow = flowRepository.findById(subFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                            format("Flow %s not found", subFlowId)));
            YSubFlow subFlow = YSubFlow.builder()
                    .yFlow(yFlow)
                    .flow(flow)
                    .sharedEndpointVlan(sharedEndpoint.getVlanId())
                    .sharedEndpointInnerVlan(sharedEndpoint.getInnerVlanId())
                    .endpointSwitchId(endpoint.getSwitchId())
                    .endpointPort(endpoint.getPortNumber())
                    .endpointVlan(endpoint.getOuterVlanId())
                    .endpointInnerVlan(endpoint.getInnerVlanId())
                    .build();
            yFlow.updateSubFlow(subFlow);
            return yFlow;
        });

        if (stateMachine.getAllocatedSubFlows().size() == stateMachine.getSubFlows().size()) {
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
        return "Could not update y-flow";
    }
}
