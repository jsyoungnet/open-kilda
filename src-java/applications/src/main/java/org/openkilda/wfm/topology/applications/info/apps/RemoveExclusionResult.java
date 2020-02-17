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

package org.openkilda.wfm.topology.applications.info.apps;

import org.openkilda.wfm.topology.applications.info.ExclusionInfoData;
import org.openkilda.wfm.topology.applications.model.Exclusion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RemoveExclusionResult extends ExclusionInfoData {
    private static final long serialVersionUID = 6521297678278117352L;

    @Builder
    @JsonCreator
    public RemoveExclusionResult(@JsonProperty("flow_id") String flowId,
                                 @JsonProperty("tunnel_id") long tunnelId,
                                 @JsonProperty("switch_id") String switchId,
                                 @JsonProperty("application") String application,
                                 @JsonProperty("exclusion") Exclusion exclusion,
                                 @JsonProperty("success") Boolean success) {
        super(flowId, tunnelId, switchId, application, exclusion, success);
    }
}