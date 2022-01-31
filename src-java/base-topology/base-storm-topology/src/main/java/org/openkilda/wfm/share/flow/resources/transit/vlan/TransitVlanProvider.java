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

import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.share.flow.resources.EncapsulationResourcesProvider;
import org.openkilda.wfm.share.utils.PoolManager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * The resource pool is responsible for transit vlan de-/allocation.
 */
@Slf4j
public class TransitVlanProvider extends EncapsulationResourcesProvider<TransitVlanEncapsulation, TransitVlan> {
    private final TransitVlanRepository transitVlanRepository;

    @Getter(AccessLevel.PROTECTED)
    private final PoolManager<TransitVlan> poolManager;

    public TransitVlanProvider(PersistenceManager persistenceManager, PoolManager.PoolConfig poolConfig) {
        super(persistenceManager);

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();

        TransitVlanPoolEntityAdapter adapter = new TransitVlanPoolEntityAdapter(transitVlanRepository, poolConfig);
        poolManager = new PoolManager<>(poolConfig, adapter);
    }

    /**
     * Get allocated transit vlan(s) of the flow path.
     */
    @Override
    public Optional<TransitVlanEncapsulation> get(PathId pathId, PathId oppositePathId) {
        return transitVlanRepository.findByPathId(pathId, oppositePathId).stream()
                .findAny()
                .map(this::newFlowResourceAdapter);
    }

    @Override
    protected TransitVlan allocateEntity(String flowId, PathId pathId, long vlanTag) {
        TransitVlan entity = newTransitVlan(flowId, pathId, vlanTag);
        transitVlanRepository.add(entity);
        return entity;
    }

    @Override
    protected void deallocateTransaction(PathId pathId) {
        transitVlanRepository.findByPathId(pathId, null)
                .forEach(this::deallocateTransaction);
    }

    private void deallocateTransaction(TransitVlan entity) {
        poolManager.deallocate(() -> {
            transitVlanRepository.remove(entity);
            return (long) entity.getVlan();
        });
    }

    @Override
    protected TransitVlanEncapsulation newFlowResourceAdapter(TransitVlan entity) {
        return TransitVlanEncapsulation.builder()
                .transitVlan(new TransitVlan(entity))
                .build();
    }

    private TransitVlan newTransitVlan(String flowId, PathId pathId, long vlanTag) {
        return TransitVlan.builder()
                .flowId(flowId)
                .pathId(pathId)
                .vlan((int) vlanTag)
                .build();
    }
}
