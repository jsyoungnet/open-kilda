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

package org.openkilda.rulemanager;

import org.openkilda.floodlight.api.OfSpeaker;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.GroupId;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@EqualsAndHashCode(callSuper = true)
@Value
@JsonSerialize
@SuperBuilder
public class GroupSpeakerCommandData extends SpeakerCommandData {

    GroupId groupId;
    GroupType type;
    List<Bucket> buckets;

    @Override
    public CompletableFuture<MessageContext> execute(OfSpeaker speaker) {
        throw new NotImplementedException(String.format("%s.execute(OfSpeaker speaker)", getClass().getName()));
    }
}
