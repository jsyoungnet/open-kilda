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

package org.openkilda.messaging.info.switches;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.validate.ValidateSwitchReport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
public class SwitchSyncResponse extends InfoData {
    @JsonProperty("validate_report")
    private final ValidateSwitchReport validateReport;

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("remove_excess_rules")
    private final boolean removeExcessRules;

    @JsonProperty("remove_excess_meters")
    private final boolean removeExcessMeters;

    @JsonCreator
    public SwitchSyncResponse(
            @JsonProperty("validate_report") ValidateSwitchReport validateReport,
            @JsonProperty("success") boolean success,
            @JsonProperty("remove_excess_rules") boolean removeExcessRules,
            @JsonProperty("remove_excess_meters") boolean removeExcessMeters) {
        this.validateReport = validateReport;
        this.success = success;
        this.removeExcessRules = removeExcessRules;
        this.removeExcessMeters = removeExcessMeters;
    }
}
