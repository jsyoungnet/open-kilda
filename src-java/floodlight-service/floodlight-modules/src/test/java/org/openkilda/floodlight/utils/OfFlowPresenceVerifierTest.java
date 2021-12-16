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

package org.openkilda.floodlight.utils;

import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class OfFlowPresenceVerifierTest extends EasyMockSupport {
    private static final DatapathId swId = DatapathId.of(1);

    @Mock
    private IOFSwitch sw;

    @Mock
    private IOfFlowDumpProducer dumpProducer;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        EasyMock.expect(sw.getId()).andStubReturn(swId);
        EasyMock.expect(dumpProducer.getSwId()).andStubReturn(swId);
    }

    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    @Test
    public void inaccurateSetFieldVlanVidActionNegative() {
        OfFlowPresenceVerifier presenceVerifier = verifyInaccurateSetFieldVlanVidAction(Collections.emptySet());
        Assert.assertFalse(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void inaccurateSetFieldVlanVidActionPositive() {
        OfFlowPresenceVerifier presenceVerifier = verifyInaccurateSetFieldVlanVidAction(Collections.singleton(
                SwitchFeature.INACCURATE_SET_VLAN_VID_ACTION));
        Assert.assertTrue(presenceVerifier.getMissing().isEmpty());
    }

    private OfFlowPresenceVerifier verifyInaccurateSetFieldVlanVidAction(Set<SwitchFeature> switchFeatures) {
        OFFactory of = new OFFactoryVer13();
        final int priority = 1000;
        final U64 cookie = U64.of(2000);
        final Match match = of.buildMatch().setExact(MatchField.IN_PORT, OFPort.of(1)).build();
        final short vlanId = 512;

        OFFlowStatsEntry switchResponseEntry = of.buildFlowStatsEntry()
                .setTableId(TableId.of(0))
                .setPriority(priority)
                .setCookie(cookie)
                .setMatch(match)
                .setInstructions(Collections.singletonList(of.instructions().applyActions(
                        Collections.singletonList(
                                of.actions().setField(of.oxms().vlanVid(OFVlanVidMatch.ofRawVid(vlanId)))))))
                .build();

        CompletableFuture<List<OFFlowStatsEntry>> switchFlowStatsResponse = CompletableFuture.completedFuture(
                Collections.singletonList(switchResponseEntry));
        EasyMock.expect(dumpProducer.getTableRequests()).andReturn(Collections.singletonList(switchFlowStatsResponse));

        replayAll();

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(priority)
                .setCookie(cookie)
                .setMatch(match)
                .setInstructions(Collections.singletonList(of.instructions().applyActions(
                        Collections.singletonList(OfAdapter.INSTANCE.setVlanIdAction(of, 512)))))
                .build();

        OfFlowPresenceVerifier presenceVerifier = new OfFlowPresenceVerifier(
                dumpProducer, Collections.singletonList(expected), switchFeatures);

        Assert.assertTrue(presenceVerifier.getFinish().isDone());
        return presenceVerifier;
    }

    @Test
    public void testVlanBitInMatchExactMatch() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertTrue(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testVlanBitInMatchMismatch() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1023))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertFalse(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testVlanBitInMatchMissingInRequest() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofRawVid((short) 1024))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertTrue(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testVlanBitInMatchMissingInResponse() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofRawVid((short) 1024))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertTrue(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testVlanBitInMatchMaskedMatch() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofVlan(0xff))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofVlan(0xff))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertTrue(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testVlanBitInMatchMaskedMismatch() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofVlan(0xff))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofVlan(0xfe))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertFalse(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testVlanBitInMatchMissingMaskedInRequest() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofVlan(0xff))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofRawVid((short) 0xff))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertTrue(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testVlanBitInMatchMissingMaskedInResponse() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofRawVid((short) 0xff))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofVlan(0xff))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertTrue(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testMatchComparisonExactVsMasked() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024), OFVlanVidMatch.ofVlan(0xff))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertFalse(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testMatchComparisonMissingField() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertFalse(presenceVerifier.getMissing().isEmpty());
    }

    @Test
    public void testMatchComparisonExtraField() {
        OFFactory of = new OFFactoryVer13();
        Match requestMatch = of.buildMatch()
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();
        Match responseMatch = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(5))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1024))
                .build();

        OfFlowPresenceVerifier presenceVerifier = verifyVlanPresenceBitInMatchInaccuracy(
                of, requestMatch, responseMatch);
        Assert.assertFalse(presenceVerifier.getMissing().isEmpty());
    }

    private OfFlowPresenceVerifier verifyVlanPresenceBitInMatchInaccuracy(OFFactory of, Match expected, Match actual) {
        int priority = 1000;
        U64 cookie = U64.of(2000);
        OFFlowStatsEntry switchResponseEntry = of.buildFlowStatsEntry()
                .setTableId(TableId.of(0))
                .setPriority(priority)
                .setCookie(cookie)
                .setMatch(actual)
                .build();

        CompletableFuture<List<OFFlowStatsEntry>> switchFlowStatsResponse = CompletableFuture.completedFuture(
                Collections.singletonList(switchResponseEntry));
        EasyMock.expect(dumpProducer.getTableRequests()).andReturn(Collections.singletonList(switchFlowStatsResponse));

        OFFlowAdd controllerCommand = of.buildFlowAdd()
                .setPriority(priority)
                .setCookie(cookie)
                .setMatch(expected)
                .build();

        replayAll();

        OfFlowPresenceVerifier presenceVerifier = new OfFlowPresenceVerifier(
                dumpProducer, Collections.singletonList(controllerCommand), new HashSet<>());

        Assert.assertTrue(presenceVerifier.getFinish().isDone());
        return presenceVerifier;
    }
}
