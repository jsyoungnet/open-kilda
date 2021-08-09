/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.error;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;

import lombok.Getter;

@Getter
public class LagPortNotFoundException extends SwitchManagerException {
    public LagPortNotFoundException(SwitchId switchId, Integer logicalPortNumber) {
        super(ErrorType.NOT_FOUND, makeMessage(switchId, logicalPortNumber));
    }

    private static String makeMessage(SwitchId switchId, Integer logicalPortNumber) {
        return String.format("LAG port %s on switch %s not found", logicalPortNumber, switchId);
    }
}
