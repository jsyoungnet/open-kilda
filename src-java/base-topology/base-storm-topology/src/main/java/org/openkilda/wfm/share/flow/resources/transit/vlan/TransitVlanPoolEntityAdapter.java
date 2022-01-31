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

package org.openkilda.wfm.share.flow.resources.transit.vlan;

import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.share.utils.PoolEntityAdapter;
import org.openkilda.wfm.share.utils.PoolManager;
import org.openkilda.wfm.share.utils.PoolManager.PoolConfig;

import java.util.Optional;

public class TransitVlanPoolEntityAdapter implements PoolEntityAdapter {
    private final PoolManager.PoolConfig config;

    private final TransitVlanRepository transitVlanRepository;

    public TransitVlanPoolEntityAdapter(TransitVlanRepository transitVlanRepository, PoolConfig config) {
        this.config = config;
        this.transitVlanRepository = transitVlanRepository;
    }

    @Override
    public boolean allocateSpecificId(long entityId) {
        return ! transitVlanRepository.exists((int) entityId);
    }

    @Override
    public Optional<Long> allocateFirstInRange(long idMinimum, long idMaximum) {
        return transitVlanRepository.findFirstUnassignedVlan((int) idMinimum, (int) idMaximum)
                .map(entity -> (long) entity);
    }

    @Override
    public String formatResourceNotAvailableMessage() {
        return String.format(
                "Unable to find any unassigned transit VLAN tag in range from %d to %d",
                config.getIdMinimum(), config.getIdMaximum());
    }
}
