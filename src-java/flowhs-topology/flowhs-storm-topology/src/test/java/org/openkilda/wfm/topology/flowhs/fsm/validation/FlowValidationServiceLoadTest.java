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

package org.openkilda.wfm.topology.flowhs.fsm.validation;

//import static java.util.Collections.emptyList;
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertTrue;
//
//import org.openkilda.messaging.info.flow.FlowValidationResponse;
//import org.openkilda.messaging.info.flow.PathDiscrepancyEntity;
//import org.openkilda.messaging.info.meter.SwitchMeterEntries;
//import org.openkilda.messaging.info.rule.FlowEntry;
//import org.openkilda.messaging.info.rule.SwitchFlowEntries;
//import org.openkilda.model.FlowPathDirection;
//import org.openkilda.model.SwitchId;
//import org.openkilda.model.cookie.FlowSegmentCookie;
//import org.openkilda.wfm.error.FlowNotFoundException;
//import org.openkilda.wfm.error.SwitchNotFoundException;
//import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
//
//import org.junit.BeforeClass;
//import org.junit.Test;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.Collectors;

public class FlowValidationServiceLoadTest extends FlowValidationTestBase {

//
//    static int FLOW_COUNT = 1000;
//    static long[] FORWARD_COOKIES = new long[FLOW_COUNT];
//    static long[] REVERSE_COOKIES = new long[FLOW_COUNT];
//    static long[] ENCAPSULATION = new long[FLOW_COUNT];
//
//    static {
//        for (int i = 0; i < FLOW_COUNT; i++) {
//            FORWARD_COOKIES[i] = new FlowSegmentCookie(FlowPathDirection.FORWARD, i + 1).getValue();
//            REVERSE_COOKIES[i] = new FlowSegmentCookie(FlowPathDirection.REVERSE, i + 1).getValue();
//            ENCAPSULATION[i] = i + 1;
//        }
//
//    }
//
//    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithTransitVlan() {
//        List<SwitchFlowEntries> switchEntries = new ArrayList<>();
//
//        List<FlowEntry> entries = new ArrayList<>();
//        for (int i = 0; i < FLOW_COUNT; i++) {
//
//        }
//        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
//                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
//                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
//                        (long) FLOW_A_FORWARD_METER_ID, false),
//                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID,
//                        String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false)));
//
//        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
//                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, FLOW_A_ENCAP_ID,
//                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null, null, false),
//                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_ENCAP_ID,
//                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), 0, null, null, false)));
//
//        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
//                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
//                        String.valueOf(FLOW_A_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false),
//                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
//                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
//                        (long) FLOW_A_REVERSE_METER_ID, false),
//                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
//                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_DST_PORT), 0,
//                        getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false)));
//
//        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_E,
//                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED,
//                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 0,
//                        null, null, false),
//                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
//                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED), 0,
//                        null, null, false)));
//
//        return switchEntries;
//    }
//
//    private static FlowValidationService service;
//
//    @BeforeClass
//    public static void setUpOnce() {
//        FlowValidationTestBase.setUpOnce();
//        FlowResourcesManager flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
//        service = new FlowValidationService(persistenceManager, flowResourcesManager,
//                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);
//    }
//
//    @Test
//    public void shouldGetSwitchIdListByFlowId() {
//        buildTransitVlanFlow("");
//        List<SwitchId> switchIds = service.getSwitchIdListByFlowId(TEST_FLOW_ID_A);
//        assertEquals(4, switchIds.size());
//
//        buildOneSwitchPortFlow();
//        switchIds = service.getSwitchIdListByFlowId(TEST_FLOW_ID_B);
//        assertEquals(1, switchIds.size());
//    }
//
//    @Test
//    public void shouldValidateFlowWithTransitVlanEncapsulation() throws FlowNotFoundException,
//    SwitchNotFoundException {
//        buildTransitVlanFlow("");
//        validateFlow(true);
//    }
//
//    @Test
//    public void shouldValidateFlowWithVxlanEncapsulation() throws FlowNotFoundException, SwitchNotFoundException {
//        buildVxlanFlow();
//        validateFlow(false);
//    }
//
//    private void validateFlow(boolean isTransitVlan) throws FlowNotFoundException, SwitchNotFoundException {
//        List<SwitchFlowEntries> flowEntries =
//                isTransitVlan ? getSwitchFlowEntriesWithTransitVlan() : getSwitchFlowEntriesWithVxlan();
//        List<SwitchMeterEntries> meterEntries = getSwitchMeterEntries();
//        List<FlowValidationResponse> result = service.validateFlow(TEST_FLOW_ID_A, flowEntries, meterEntries,
//                emptyList());
//        assertEquals(4, result.size());
//        assertEquals(0, result.get(0).getDiscrepancies().size());
//        assertEquals(0, result.get(1).getDiscrepancies().size());
//        assertEquals(0, result.get(2).getDiscrepancies().size());
//        assertEquals(0, result.get(3).getDiscrepancies().size());
//        assertEquals(3, (int) result.get(0).getFlowRulesTotal());
//        assertEquals(3, (int) result.get(1).getFlowRulesTotal());
//        assertEquals(2, (int) result.get(2).getFlowRulesTotal());
//        assertEquals(2, (int) result.get(3).getFlowRulesTotal());
//        assertEquals(10, (int) result.get(0).getSwitchRulesTotal());
//        assertEquals(10, (int) result.get(1).getSwitchRulesTotal());
//        assertEquals(10, (int) result.get(2).getSwitchRulesTotal());
//        assertEquals(10, (int) result.get(3).getSwitchRulesTotal());
//
//        flowEntries =
//                isTransitVlan ? getWrongSwitchFlowEntriesWithTransitVlan() : getWrongSwitchFlowEntriesWithVxlan();
//        meterEntries = getWrongSwitchMeterEntries();
//        result = service.validateFlow(TEST_FLOW_ID_A, flowEntries, meterEntries, emptyList());
//        assertEquals(4, result.size());
//        assertEquals(6, result.get(0).getDiscrepancies().size());
//        assertEquals(3, result.get(1).getDiscrepancies().size());
//        assertEquals(2, result.get(2).getDiscrepancies().size());
//        assertEquals(2, result.get(3).getDiscrepancies().size());
//
//        List<String> forwardDiscrepancies = result.get(0).getDiscrepancies().stream()
//                .map(PathDiscrepancyEntity::getField)
//                .collect(Collectors.toList());
//        assertTrue(forwardDiscrepancies.contains("cookie"));
//        assertTrue(forwardDiscrepancies.contains(isTransitVlan ? "inVlan" : "tunnelId"));
//        assertTrue(forwardDiscrepancies.contains("outVlan"));
//        assertTrue(forwardDiscrepancies.contains("meterRate"));
//        assertTrue(forwardDiscrepancies.contains("meterBurstSize"));
//        assertTrue(forwardDiscrepancies.contains("meterFlags"));
//
//        List<String> reverseDiscrepancies = result.get(1).getDiscrepancies().stream()
//                .map(PathDiscrepancyEntity::getField)
//                .collect(Collectors.toList());
//        assertTrue(reverseDiscrepancies.contains("inPort"));
//        assertTrue(reverseDiscrepancies.contains("outPort"));
//        assertTrue(reverseDiscrepancies.contains("meterId"));
//
//        List<String> protectedForwardDiscrepancies = result.get(2).getDiscrepancies().stream()
//                .map(PathDiscrepancyEntity::getField)
//                .collect(Collectors.toList());
//        assertTrue(protectedForwardDiscrepancies.contains(isTransitVlan ? "inVlan" : "tunnelId"));
//        assertTrue(protectedForwardDiscrepancies.contains("outVlan"));
//
//        List<String> protectedReverseDiscrepancies = result.get(3).getDiscrepancies().stream()
//                .map(PathDiscrepancyEntity::getField)
//                .collect(Collectors.toList());
//        assertTrue(protectedReverseDiscrepancies.contains("outPort"));
//        assertTrue(protectedReverseDiscrepancies.contains("inPort"));
//    }
//
//    @Test
//    public void shouldValidateOneSwitchFlow() throws FlowNotFoundException, SwitchNotFoundException {
//        buildOneSwitchPortFlow();
//        List<SwitchFlowEntries> switchEntries = getSwitchFlowEntriesOneSwitchFlow();
//        List<SwitchMeterEntries> meterEntries = getSwitchMeterEntriesOneSwitchFlow();
//        List<FlowValidationResponse> result = service.validateFlow(TEST_FLOW_ID_B, switchEntries, meterEntries,
//                emptyList());
//        assertEquals(2, result.size());
//        assertEquals(0, result.get(0).getDiscrepancies().size());
//        assertEquals(0, result.get(1).getDiscrepancies().size());
//    }
//
//    @Test(expected = FlowNotFoundException.class)
//    public void shouldValidateFlowUsingNotExistingFlow() throws FlowNotFoundException, SwitchNotFoundException {
//        service.validateFlow("test",  emptyList(),  emptyList(),  emptyList());
//    }
//
//    @Test
//    public void shouldValidateFlowWithTransitVlanEncapsulationESwitch()
//            throws FlowNotFoundException, SwitchNotFoundException {
//        buildTransitVlanFlow("E");
//
//        List<SwitchFlowEntries> flowEntries = getSwitchFlowEntriesWithTransitVlan();
//        List<SwitchMeterEntries> meterEntries = getSwitchMeterEntriesWithESwitch();
//        List<FlowValidationResponse> result = service.validateFlow(TEST_FLOW_ID_A, flowEntries, meterEntries,
//                emptyList());
//        assertEquals(4, result.size());
//        assertEquals(0, result.get(0).getDiscrepancies().size());
//        assertEquals(0, result.get(1).getDiscrepancies().size());
//        assertEquals(0, result.get(2).getDiscrepancies().size());
//        assertEquals(0, result.get(3).getDiscrepancies().size());
//        assertEquals(3, (int) result.get(0).getFlowRulesTotal());
//        assertEquals(3, (int) result.get(1).getFlowRulesTotal());
//        assertEquals(2, (int) result.get(2).getFlowRulesTotal());
//        assertEquals(2, (int) result.get(3).getFlowRulesTotal());
//        assertEquals(10, (int) result.get(0).getSwitchRulesTotal());
//        assertEquals(10, (int) result.get(1).getSwitchRulesTotal());
//        assertEquals(10, (int) result.get(2).getSwitchRulesTotal());
//        assertEquals(10, (int) result.get(3).getSwitchRulesTotal());
//    }


}
