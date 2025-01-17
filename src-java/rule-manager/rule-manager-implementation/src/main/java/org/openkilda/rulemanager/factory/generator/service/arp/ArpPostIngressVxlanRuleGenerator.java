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

package org.openkilda.rulemanager.factory.generator.service.arp;

import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.rulemanager.Constants.ARP_VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;
import static org.openkilda.rulemanager.action.ActionType.POP_VXLAN_NOVIFLOW;
import static org.openkilda.rulemanager.action.ActionType.POP_VXLAN_OVS;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ArpPostIngressVxlanRuleGenerator extends ArpRuleGenerator {

    @Builder
    public ArpPostIngressVxlanRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (!(sw.getFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)
                || sw.getFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN))) {
            return Collections.emptyList();
        }

        RoutingMetadata metadata = buildMetadata(RoutingMetadata.builder().arpFlag(true), sw);
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(ARP_VXLAN_UDP_SRC).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(VXLAN_UDP_DST).build()
        );
        if (sw.getFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            match.add(FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build());
        }

        List<Action> actions = new ArrayList<>();
        if (sw.getFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            actions.add(new PopVxlanAction(POP_VXLAN_NOVIFLOW));
        } else {
            actions.add(new PopVxlanAction(POP_VXLAN_OVS));
        }
        actions.add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        Cookie cookie = new Cookie(ARP_POST_INGRESS_VXLAN_COOKIE);

        return buildCommands(sw, cookie, OfTable.POST_INGRESS, Priority.ARP_POST_INGRESS_VXLAN_PRIORITY,
                match, instructions);
    }
}
