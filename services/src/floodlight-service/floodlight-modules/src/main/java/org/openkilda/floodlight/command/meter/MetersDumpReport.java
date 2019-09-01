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

package org.openkilda.floodlight.command.meter;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.model.of.MeterSchema;
import org.openkilda.floodlight.api.response.SpeakerMetersDumpResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.command.SpeakerRemoteCommandReport;

import java.util.List;

public class MetersDumpReport extends SpeakerRemoteCommandReport {
    private final MetersDumpCommand command;
    private final List<MeterSchema> entries;

    public MetersDumpReport(MetersDumpCommand command, List<MeterSchema> entries) {
        this(command, entries, null);
    }

    public MetersDumpReport(MetersDumpCommand command, Exception error) {
        this(command, null, error);
    }

    private MetersDumpReport(MetersDumpCommand command, List<MeterSchema> entries, Exception error) {
        super(command, error);
        this.command = command;
        this.entries = entries;
    }

    @Override
    protected String getReplyTopic(KafkaChannel kafkaChannel) {
        // TODO(surabujin): migrate to valid FL broadcast topic or make correct reply topic calculation
        return kafkaChannel.getTopoSwitchManagerTopic();
    }

    @Override
    protected SpeakerResponse makeSuccessReply() {
        return SpeakerMetersDumpResponse.builder()
                .messageContext(command.getMessageContext())
                .commandId(command.getCommandId())
                .switchId(command.getSwitchId())
                .entries(entries)
                .build();
    }
}
