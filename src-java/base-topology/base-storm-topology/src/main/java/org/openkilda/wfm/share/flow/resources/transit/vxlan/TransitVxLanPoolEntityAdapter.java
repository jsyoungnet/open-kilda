/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.share.flow.resources.transit.vxlan;

import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.wfm.share.utils.PoolEntityAdapter;
import org.openkilda.wfm.share.utils.PoolManager.PoolConfig;

import java.util.Optional;

public class TransitVxLanPoolEntityAdapter implements PoolEntityAdapter  {
    private final VxlanRepository vxLanRepository;
    private final PoolConfig config;

    public TransitVxLanPoolEntityAdapter(VxlanRepository vxLanRepository, PoolConfig config) {
        this.vxLanRepository = vxLanRepository;
        this.config = config;
    }

    @Override
    public boolean allocateSpecificId(long entityId) {
        return ! vxLanRepository.exists((int) entityId);
    }

    @Override
    public Optional<Long> allocateFirstInRange(long idMinimum, long idMaximum) {
        return vxLanRepository.findFirstUnassignedVxlan((int) idMinimum, (int) idMaximum)
                .map(Long.class::cast);
    }

    @Override
    public String formatResourceNotAvailableMessage() {
        return String.format(
                "Unable to find any unassigned transit VxVLAN tunnel ID in range from %d to %d",
                config.getIdMinimum(), config.getIdMaximum());
    }
}
