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

import org.openkilda.floodlight.api.BatchCommandProcessor;
import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;

public class OfSpeakerService implements BatchCommandProcessor {
    private final FloodlightModuleContext moduleContext;
    private final IOFSwitchService iofSwitchService;
    private final SessionService sessionService;

    public OfSpeakerService(@NonNull FloodlightModuleContext moduleContext) {
        this.moduleContext = moduleContext;
        this.iofSwitchService = moduleContext.getServiceImpl(IOFSwitchService.class);
        this.sessionService = moduleContext.getServiceImpl(SessionService.class);


    }

    @Override
    public void processBatchInstall(InstallSpeakerCommandsRequest request, String key) {
        MessageContext messageContext = request.getMessageContext();

        SwitchId switchId = request.getSwitchId();
        DatapathId dpId = DatapathId.of(switchId.toLong());
        IOFSwitch sw = iofSwitchService.getSwitch(dpId);
        OfBatchHolder builder = new OfBatchHolder(iofSwitchService);
        for (OfCommand data : request.getCommands()) {
            data.buildInstall(builder, switchId);
        }
    }

    @Override
    public void processBatchDelete(DeleteSpeakerCommandsRequest request, String key) {
        SwitchId switchId = request.getSwitchId();
        OfBatchHolder builder = new OfBatchHolder(iofSwitchService);
        for (OfCommand data : request.getCommands()) {
            data.buildDelete(builder, switchId);
        }
    }
}
