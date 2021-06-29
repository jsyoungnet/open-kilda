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

package org.openkilda.server42.control.topology.storm.bolt.isl.command;

import org.openkilda.model.SwitchId;
import org.openkilda.server42.control.topology.storm.bolt.isl.IslHandler;

public class SendIslListOnSwitchCommand extends IslCommand {
    public SendIslListOnSwitchCommand(SwitchId switchId) {
        super(switchId);
    }

    @Override
    public void apply(IslHandler handler) {
        handler.processSendIslPortListOnSwitchCommand(getSwitchId());
    }
}
