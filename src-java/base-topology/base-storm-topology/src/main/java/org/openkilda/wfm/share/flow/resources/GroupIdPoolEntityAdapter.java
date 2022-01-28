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

package org.openkilda.wfm.share.flow.resources;

import org.openkilda.model.GroupId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.wfm.share.utils.PoolEntityAdapter;
import org.openkilda.wfm.share.utils.PoolManager;
import org.openkilda.wfm.share.utils.PoolManager.PoolConfig;

import java.util.Optional;

public class GroupIdPoolEntityAdapter implements PoolEntityAdapter {
    private final MirrorGroupRepository mirrorGroupRepository;

    private final SwitchId switchId;

    private final PoolManager.PoolConfig config;

    public GroupIdPoolEntityAdapter(MirrorGroupRepository mirrorGroupRepository, SwitchId switchId, PoolConfig config) {
        this.mirrorGroupRepository = mirrorGroupRepository;
        this.switchId = switchId;
        this.config = config;
    }

    @Override
    public boolean allocateSpecificId(long entityId) {
        GroupId entity = new GroupId(entityId);
        return ! mirrorGroupRepository.exists(switchId, entity);
    }

    @Override
    public Optional<Long> allocateFirstInRange(long idMinimum, long idMaximum) {
        GroupId first = new GroupId((idMinimum));
        GroupId last = new GroupId(idMaximum);
        return mirrorGroupRepository.findFirstUnassignedGroupId(switchId, first, last)
                .map(GroupId::getValue);
    }

    @Override
    public String formatResourceNotAvailableMessage() {
        return String.format(
                "Unable to find any unassigned GroupId for switch %s in range from %d to %d",
                switchId, config.getIdMinimum(), config.getIdMaximum());
    }
}
