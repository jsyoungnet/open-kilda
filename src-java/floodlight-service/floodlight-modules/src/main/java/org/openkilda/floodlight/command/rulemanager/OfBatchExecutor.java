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

package org.openkilda.floodlight.command.rulemanager;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.converter.rulemanager.OfFlowModConverter;
import org.openkilda.floodlight.converter.rulemanager.OfMeterConverter;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class OfBatchExecutor {

    private final IOFSwitch iofSwitch;
    private final KafkaUtilityService kafkaUtilityService;
    private final IKafkaProducerService kafkaProducerService;
    private final SessionService sessionService;
    private final MessageContext messageContext;
    private final OfBatchHolder holder;
    private final Set<SwitchFeature> switchFeatures;
    private final String kafkaKey;


    private boolean hasMeters;
    private boolean hasGroups;
    private boolean hasFlows;

    private CompletableFuture<List<OFMeterConfigStatsReply>> meterStats = CompletableFuture.completedFuture(null);
    private CompletableFuture<List<OFGroupDescStatsReply>> groupStats = CompletableFuture.completedFuture(null);
    private CompletableFuture<List<OFFlowStatsReply>> flowStats = CompletableFuture.completedFuture(null);

    public OfBatchExecutor(IOFSwitch iofSwitch, KafkaUtilityService kafkaUtilityService,
                           IKafkaProducerService kafkaProducerService,
                           SessionService sessionService, MessageContext messageContext, OfBatchHolder holder,
                           Set<SwitchFeature> switchFeatures, String kafkaKey) {
        this.iofSwitch = iofSwitch;
        this.kafkaUtilityService = kafkaUtilityService;
        this.kafkaProducerService = kafkaProducerService;
        this.sessionService = sessionService;
        this.messageContext = messageContext;
        this.holder = holder;
        this.switchFeatures = switchFeatures;
        this.kafkaKey = kafkaKey;
    }

    /**
     * Execute current batch of commands.
     */
    public void executeBatch() {
        List<String> stage = holder.getCurrentStage();
        List<OFMessage> ofMessages = new ArrayList<>();
        for (String uuid : stage) {
            BatchData batchData = holder.getByUUid(uuid);
            hasFlows |= batchData.isFlow();
            hasMeters |= batchData.isMeter();
            hasGroups |= batchData.isGroup();
            ofMessages.add(batchData.getMessage());
        }
        List<CompletableFuture<Optional<OFMessage>>> requests = new ArrayList<>();
        try (Session session = sessionService.open(messageContext, iofSwitch)) {
            for (OFMessage message : ofMessages) {
                requests.add(session.write(message));
            }
        }

        CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0]))
                .thenAccept(ignore -> checkOfResponses(requests));
    }


    void checkOfResponses(List<CompletableFuture<Optional<OFMessage>>> waitingMessages) {
        for (CompletableFuture<Optional<OFMessage>> message : waitingMessages) {
            try {
                OFMessage ofMessage = message.get().get();
                String uuid = holder.popAwaitingXid(ofMessage.getXid());
                if (ofMessage instanceof OFErrorMsg) {
                    holder.recordFailedUuid(uuid);
                }
            } catch (InterruptedException e) {
                log.error("Failed to get results for message", e);
            } catch (ExecutionException e) {
                log.error("Failed to get results for message", e);
            }
        }
        if (hasMeters) {
            meterStats = OfUtils.verifyMeters(messageContext, iofSwitch);
        }
        if (hasGroups) {
            groupStats = OfUtils.verifyGroups(messageContext, iofSwitch);
        }
        if (hasFlows) {
            flowStats = OfUtils.verifyFlows(messageContext, iofSwitch);
        }
        CompletableFuture.allOf(meterStats, groupStats, flowStats)
                .thenAccept(ignore -> runVerify());
    }

    void runVerify() {
        verifyFlows();
        verifyMeters();
        verifyGroups();
        if (holder.hasNextStage()) {
            holder.jumpToNextStage();
            meterStats = CompletableFuture.completedFuture(null);
            groupStats = CompletableFuture.completedFuture(null);
            flowStats = CompletableFuture.completedFuture(null);
            hasMeters = false;
            hasGroups = false;
            hasFlows = false;
            executeBatch();
        } else {
            sendResponse(null);
        }

    }

    private void verifyFlows() {
        if (!hasFlows) {
            return;
        }
        try {
            List<OFFlowStatsReply> replies = flowStats.get();
            List<FlowSpeakerData> switchFlows = new ArrayList<>();
            replies.forEach(reply -> switchFlows.addAll(
                    OfFlowModConverter.INSTANCE.convertToFlowSpeakerCommandData(reply)));
            for (FlowSpeakerData switchFlow : switchFlows) {
                FlowSpeakerData expectedFlow = holder.getByCookie(switchFlow.getCookie());
                if (expectedFlow == null) {
                    continue;
                } else if (switchFlow.equals(expectedFlow)) {
                    holder.recordSuccessUuid(expectedFlow.getUuid());
                } else {
                    holder.recordFailedUuid(expectedFlow.getUuid());
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void verifyMeters() {
        if (!hasMeters) {
            return;
        }
        boolean inaccurate = switchFeatures.contains(SwitchFeature.INACCURATE_METER);
        try {
            List<OFMeterConfigStatsReply> replies = meterStats.get();
            List<MeterSpeakerData> switchMeters = new ArrayList<>();
            replies.forEach(reply -> switchMeters.addAll(
                    OfMeterConverter.INSTANCE.convertToMeterSpeakerCommandData(reply, inaccurate)));

            for (MeterSpeakerData switchMeter : switchMeters) {
                MeterSpeakerData expectedMeter = holder.getByMeterId(switchMeter.getMeterId());
                if (expectedMeter == null) {
                    continue;
                } else if (switchMeter.equals(expectedMeter)) {
                    holder.recordSuccessUuid(expectedMeter.getUuid());
                } else {
                    holder.recordFailedUuid(expectedMeter.getUuid());
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

    private void verifyGroups() {
        if (!hasGroups) {
            return;
        }
        try {
            List<OFGroupDescStatsReply> replies = groupStats.get();
            List<GroupSpeakerData> switchGroups = new ArrayList<>();
            replies.forEach(reply -> switchGroups.addAll(
                    OfFlowModConverter.INSTANCE.convertToGroupSpeakerCommandData(reply)));

            for (GroupSpeakerData switchGroup : switchGroups) {
                GroupSpeakerData expectedGroup = holder.getByGroupId(switchGroup.getGroupId());
                if (expectedGroup == null) {
                    continue;
                } else if (switchGroup.equals(expectedGroup)) {
                    holder.recordSuccessUuid(expectedGroup.getUuid());
                } else {
                    holder.recordFailedUuid(expectedGroup.getUuid());
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

    private void sendResponse(SpeakerResponse response) {
        KafkaChannel kafkaChannel = kafkaUtilityService.getKafkaChannel();
        kafkaProducerService.sendMessageAndTrack(kafkaChannel.getSpeakerFlowHsTopic(),
                kafkaKey, response);
    }
}
