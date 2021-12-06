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

package org.openkilda.floodlight.converter.rulemanager;

import static java.lang.String.format;

import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFBucket.Builder;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFGroupDelete;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public abstract class OfFlowModConverter {
    public static final OfFlowModConverter INSTANCE = Mappers.getMapper(OfFlowModConverter.class);


    /**
     * Convert stats reply.
     */
    public List<FlowSpeakerData> convertToFlowSpeakerCommandData(OFFlowStatsReply statsReply) {
        List<FlowSpeakerData> flowSpeakerData = new ArrayList<>();
        for (OFFlowStatsEntry entry : statsReply.getEntries()) {
            FlowSpeakerData speakerData = FlowSpeakerData.builder()
                    .cookie(new Cookie(entry.getCookie().getValue()))
                    .flags(convertToRuleManagerFlags(entry.getFlags()))
                    .priority(entry.getPriority())
                    .table(OfTable.fromInt(entry.getTableId().getValue()))
                    .match(OfMatchConverter.INSTANCE.convertToRuleManagerMatch(entry.getMatch()))
                    .instructions(
                            OfInstructionsConverter.INSTANCE.convertToRuleManagerInstructions(entry.getInstructions()))
                    .build();
            flowSpeakerData.add(speakerData);
        }
        return flowSpeakerData;
    }

    /**
     * Convert stats reply.
     */
    public List<GroupSpeakerData> convertToGroupSpeakerCommandData(OFGroupDescStatsReply statsReply) {
        List<GroupSpeakerData> commandData = new ArrayList<>();
        for (OFGroupDescStatsEntry entry : statsReply.getEntries()) {
        }
        return commandData;
    }

    /**
     * Convert group command data into OfGroupMod representation.
     */
    public OFGroupAdd convertInstallGroupCommand(GroupSpeakerData commandData, OFFactory ofFactory) {
        List<OFBucket> buckets = commandData.getBuckets().stream()
                .map(x -> toOfBucket(ofFactory, x)).collect(Collectors.toList());
        return ofFactory.buildGroupAdd()
                .setGroup(OFGroup.of((int) commandData.getGroupId().getValue()))
                .setGroupType(toOfGroupType(commandData.getType()))
                .setBuckets(buckets)
                .build();

    }

    /**
     * Convert group command data into OfGroupMod representation.
     */
    public OFGroupDelete convertDeleteGroupCommand(GroupSpeakerData commandData, OFFactory ofFactory) {
        return ofFactory.buildGroupDelete()
                .setGroup(OFGroup.of((int) commandData.getGroupId().getValue()))
                .setGroupType(toOfGroupType(commandData.getType()))
                .build();
    }

    private OFBucket toOfBucket(OFFactory ofFactory, Bucket bucket) {
        List<OFAction> ofActions = OfInstructionsConverter.INSTANCE.convertApplyActions(bucket.getWriteActions(),
                ofFactory);
        Builder builder = ofFactory.buildBucket()
                .setActions(ofActions)
                .setWatchGroup(toOfGroup(bucket.getWatchGroup()));
        if (bucket.getWatchPort() != null) {
            builder.setWatchPort(toOfWatchPort(bucket.getWatchPort()));
        }
        return builder.build();
    }

    private OFPort toOfWatchPort(WatchPort watchPort) {
        switch (watchPort) {
            case ANY:
                return OFPort.ANY;
            default:
                throw new IllegalArgumentException("Unknown watch port");
        }
    }

    private OFPort fromOfPort(OFPort watchPort) {
        if (watchPort.equals(OFGroup.ANY)) {
            return OFPort.ANY;
        } else {
            throw new IllegalArgumentException("Unknown watch group");
        }
    }

    private OFGroup toOfGroup(WatchGroup watchGroup) {
        switch (watchGroup) {
            case ANY:
                return OFGroup.ANY;
            case ALL:
                return OFGroup.ALL;
            default:
                throw new IllegalArgumentException("Unknown watch group");
        }
    }

    private WatchGroup fromOfGroup(OFGroup watchGroup) {
        if (watchGroup.equals(OFGroup.ANY)) {
            return WatchGroup.ANY;
        } else if (watchGroup.equals(OFGroup.ALL)) {
            return WatchGroup.ALL;
        } else {
            throw new IllegalArgumentException("Unknown watch group");
        }
    }

    private OFGroupType toOfGroupType(GroupType groupType) {
        switch (groupType) {
            case ALL:
                return OFGroupType.ALL;
            default:
                throw new IllegalArgumentException("Unknown group type.");
        }
    }

    private GroupType fromOfGroupType(OFGroupType groupType) {
        switch (groupType) {
            case ALL:
                return GroupType.ALL;
            default:
                throw new IllegalArgumentException("Unknown group type.");
        }
    }

    /**
     * Convert flow speaker command data into OfFlowMod representation.
     */
    public OFFlowMod convertInstallFlowCommand(FlowSpeakerData commandData, OFFactory ofFactory) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(commandData.getCookie().getValue()))
                .setTableId(TableId.of(commandData.getTable().getTableId()))
                .setPriority(commandData.getPriority())
                .setMatch(OfMatchConverter.INSTANCE.convertMatch(commandData.getMatch(), ofFactory))
                .setInstructions(
                        OfInstructionsConverter.INSTANCE.convertInstructions(commandData.getInstructions(), ofFactory))
                .setFlags(convertToOfFlags(commandData.getFlags()))
                .build();
    }

    /**
     * Convert Flow Delete Command.
     *
     * @param commandData data
     * @param ofFactory factory
     * @return mod
     */
    public OFFlowMod convertDeleteFlowCommand(FlowSpeakerData commandData, OFFactory ofFactory) {
        return ofFactory.buildFlowDeleteStrict()
                .setCookie(U64.of(commandData.getCookie().getValue()))
                .setTableId(TableId.of(commandData.getTable().getTableId()))
                .setPriority(commandData.getPriority())
                .setMatch(OfMatchConverter.INSTANCE.convertMatch(commandData.getMatch(), ofFactory))
                .build();
    }

    private Set<OFFlowModFlags> convertToOfFlags(Set<OfFlowFlag> flags) {
        Set<OFFlowModFlags> ofFlowModFlags = new HashSet<>();
        for (OfFlowFlag flag : flags) {
            switch (flag) {
                case RESET_COUNTERS:
                    ofFlowModFlags.add(OFFlowModFlags.RESET_COUNTS);
                    break;
                default:
                    throw new IllegalStateException(format("Unknown flow mod flag %s", flag));
            }
        }
        return ofFlowModFlags;
    }

    private Set<OfFlowFlag> convertToRuleManagerFlags(Set<OFFlowModFlags> flags) {
        Set<OfFlowFlag> flowModFlags = new HashSet<>();
        for (OFFlowModFlags flag : flags) {
            switch (flag) {
                case RESET_COUNTS:
                    flowModFlags.add(OfFlowFlag.RESET_COUNTERS);
                    break;
                default:
                    throw new IllegalStateException(format("Unknown flow mod flag %s", flag));
            }
        }
        return flowModFlags;
    }
}
