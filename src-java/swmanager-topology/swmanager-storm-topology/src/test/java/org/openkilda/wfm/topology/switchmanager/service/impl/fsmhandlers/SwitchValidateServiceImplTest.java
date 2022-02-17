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

package org.openkilda.wfm.topology.switchmanager.service.impl.fsmhandlers;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.LAG;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.impl.ValidationServiceImpl;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class SwitchValidateServiceImplTest {

    private static final SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static final SwitchId SWITCH_ID_MISSING = new SwitchId(0x0000000000000002L);
    private static final Switch SWITCH_1 = Switch.builder().switchId(SWITCH_ID).features(Sets.newHashSet(LAG)).build();
    private static final String KEY = "KEY";

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private ValidationServiceImpl validationService;

    @Mock
    private RuleManager ruleManager;

    @Mock
    private SwitchManagerCarrier carrier;

    private SwitchValidateServiceImpl service;
    private SwitchValidateRequest request;

    private FlowSpeakerData flowSpeakerData;
    private MeterSpeakerData meterSpeakerData;

    @Before
    public void setUp() {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);
        FlowPathRepository flowPathRepository = Mockito.mock(FlowPathRepository.class);
        SwitchRepository switchRepository = Mockito.mock(SwitchRepository.class);
        when(switchRepository.findById(eq(SWITCH_ID))).thenReturn(Optional.of(SWITCH_1));
        when(switchRepository.findById(eq(SWITCH_ID_MISSING))).thenReturn(Optional.empty());
        LagLogicalPortRepository lagLogicalPortRepository = Mockito.mock(LagLogicalPortRepository.class);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        service = new SwitchValidateServiceImpl(carrier, persistenceManager, validationService, ruleManager);

        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).processMeters(true).build();
        flowSpeakerData = FlowSpeakerData.builder()
                .ofVersion(OfVersion.OF_13)
                .cookie(new Cookie(1L))
                .table(OfTable.INPUT)
                .priority(10)
                .match(emptySet())
                .instructions(Instructions.builder().build())
                .flags(emptySet())
                .build();
        meterSpeakerData = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(10000)
                .burst(10500)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();

        when(validationService.validateRules(any(), any(), any()))
                .thenReturn(new ValidateRulesResult(newHashSet(flowSpeakerData.getCookie().getValue()), emptySet(),
                        emptySet(), emptySet()));
        when(validationService.validateMeters(any(), any(), any()))
                .thenReturn(new ValidateMetersResult(emptyList(), emptyList(), emptyList(), emptyList()));
    }

    @Test
    public void smokeHandleRequest() {
        handleRequestAndInitDataReceive();
    }

    @Test
    public void receiveOnlyRules() {
        handleRequestAndInitDataReceive();

        service.handleFlowEntriesResponse(KEY, new FlowDumpResponse(SWITCH_ID, singletonList(flowSpeakerData)));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void receiveTaskTimeout() {
        handleRequestAndInitDataReceive();

        service.handleFlowEntriesResponse(KEY, new FlowDumpResponse(SWITCH_ID, singletonList(flowSpeakerData)));
        service.handleTaskTimeout(KEY);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).errorResponse(eq(KEY), eq(ErrorType.OPERATION_TIMED_OUT), any(String.class), any(String.class));
        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void receiveTaskError() {
        handleRequestAndInitDataReceive();

        service.handleFlowEntriesResponse(KEY, new FlowDumpResponse(SWITCH_ID, singletonList(flowSpeakerData)));
        ErrorMessage errorMessage = getErrorMessage();
        service.handleTaskError(KEY, errorMessage);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).errorResponse(eq(KEY), eq(errorMessage.getData().getErrorType()), any(String.class),
                any(String.class));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationSuccess() {
        handleRequestAndInitDataReceive();
        handleDataReceiveAndValidate();

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());
        SwitchValidationResponse response = (SwitchValidationResponse) responseCaptor.getValue().getData();
        assertEquals(singletonList(flowSpeakerData.getCookie().getValue()), response.getRules().getMissing());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationWithoutMetersSuccess() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).build();

        service.handleSwitchValidateRequest(KEY, request);
        verify(carrier, times(2)).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        service.handleFlowEntriesResponse(KEY, new FlowDumpResponse(SWITCH_ID, singletonList(flowSpeakerData)));
        service.handleGroupEntriesResponse(KEY, new GroupDumpResponse(SWITCH_ID, emptyList()));
        verify(validationService).validateRules(eq(SWITCH_ID), any(), any());
        verify(validationService).validateGroups(eq(SWITCH_ID), any(), any());
        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());

        SwitchValidationResponse response = (SwitchValidationResponse) responseCaptor.getValue().getData();
        assertEquals(singletonList(flowSpeakerData.getCookie().getValue()), response.getRules().getMissing());
        assertNull(response.getMeters());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationSuccessWithUnsupportedMeters() {
        handleRequestAndInitDataReceive();
        service.handleFlowEntriesResponse(KEY, new FlowDumpResponse(SWITCH_ID, singletonList(flowSpeakerData)));
        service.handleMetersUnsupportedResponse(KEY);
        service.handleGroupEntriesResponse(KEY, new GroupDumpResponse(SWITCH_ID, emptyList()));

        verify(validationService).validateRules(eq(SWITCH_ID), any(), any());
        verify(validationService).validateGroups(eq(SWITCH_ID), any(), any());
        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());

        SwitchValidationResponse response = (SwitchValidationResponse) responseCaptor.getValue().getData();
        assertEquals(singletonList(flowSpeakerData.getCookie().getValue()), response.getRules().getMissing());
        assertNull(response.getMeters());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void exceptionWhileValidation() {
        handleRequestAndInitDataReceive();

        String errorMessage = "test error";
        when(validationService.validateGroups(any(), any(), any()))
                .thenThrow(new IllegalArgumentException(errorMessage));
        handleDataReceiveAndValidate();

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(carrier).errorResponse(eq(KEY), eq(ErrorType.INTERNAL_ERROR), errorCaptor.capture(), any(String.class));
        assertEquals(errorMessage, errorCaptor.getValue());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void doNothingWhenFsmNotFound() {
        service.handleFlowEntriesResponse(KEY, new FlowDumpResponse(SWITCH_ID, singletonList(flowSpeakerData)));

        verifyZeroInteractions(carrier);
        verifyZeroInteractions(validationService);
    }

    @Test
    public void validationPerformSync() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).processMeters(true).build();

        handleRequestAndInitDataReceive();
        handleDataReceiveAndValidate();

        verify(carrier).runSwitchSync(eq(KEY), eq(request), any(ValidationResult.class));
        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void errorResponseOnSwitchNotFound() {
        request = SwitchValidateRequest
                .builder().switchId(SWITCH_ID_MISSING).performSync(true).processMeters(true).build();
        service.handleSwitchValidateRequest(KEY, request);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).errorResponse(
                eq(KEY), eq(ErrorType.NOT_FOUND), eq(String.format("Switch '%s' not found", request.getSwitchId())),
                any(String.class));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    private void handleRequestAndInitDataReceive() {
        service.handleSwitchValidateRequest(KEY, request);

        verify(carrier, times(3)).sendCommandToSpeaker(eq(KEY), any(CommandData.class));
        verifyNoMoreInteractions(carrier);
    }

    private void handleDataReceiveAndValidate() {
        service.handleFlowEntriesResponse(KEY, new FlowDumpResponse(SWITCH_ID, singletonList(flowSpeakerData)));
        service.handleMeterEntriesResponse(KEY, new MeterDumpResponse(SWITCH_ID, singletonList(meterSpeakerData)));
        service.handleGroupEntriesResponse(KEY, new GroupDumpResponse(SWITCH_ID, emptyList()));

        verify(validationService).validateRules(eq(SWITCH_ID), any(), any());
        verify(validationService).validateMeters(eq(SWITCH_ID), any(), any());
        verify(validationService).validateGroups(eq(SWITCH_ID), any(), any());
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }
}
