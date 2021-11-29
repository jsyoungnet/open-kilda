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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.wfm.topology.flowhs.fsm.common.WithHistorySupportFsm;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public abstract class HistoryRecordingAction<T extends WithHistorySupportFsm<T, S, E, C, ?>, S, E, C>
        extends AnonymousAction<T, S, E, C> {
    @Override
    public final void execute(S from, S to, E event, C context, T stateMachine) {
        try {
            perform(from, to, event, context, stateMachine);
        } catch (Exception ex) {
            String errorMessage = format("%s failed: %s", getClass().getSimpleName(), ex.getMessage());
            stateMachine.saveErrorToHistory(errorMessage, ex);
            stateMachine.fireError(errorMessage);
        }
    }

    protected abstract void perform(S from, S to, E event, C context, T stateMachine);
}
