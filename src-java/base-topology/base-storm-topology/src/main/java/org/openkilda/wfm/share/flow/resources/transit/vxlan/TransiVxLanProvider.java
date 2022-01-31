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

import org.openkilda.model.PathId;
import org.openkilda.model.Vxlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.wfm.share.flow.resources.EncapsulationResourcesProvider;
import org.openkilda.wfm.share.utils.PoolManager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * The resource pool is responsible for vxlan de-/allocation.
 */
@Slf4j
public class TransiVxLanProvider extends EncapsulationResourcesProvider<VxlanEncapsulation, Vxlan> {
    private final VxlanRepository vxlanRepository;

    @Getter(AccessLevel.PROTECTED)
    private final PoolManager<Vxlan> poolManager;

    public TransiVxLanProvider(PersistenceManager persistenceManager, PoolManager.PoolConfig poolConfig) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        vxlanRepository = repositoryFactory.createVxlanRepository();

        TransitVxLanPoolEntityAdapter adapter = new TransitVxLanPoolEntityAdapter(vxlanRepository, poolConfig);
        poolManager = new PoolManager<>(poolConfig, adapter);
    }

    /**
     * Get allocated vxlan(s) of the flow path.
     */
    @Override
    public Optional<VxlanEncapsulation> get(PathId pathId, PathId oppositePathId) {
        return vxlanRepository.findByPathId(pathId, oppositePathId).stream()
                .findAny()
                .map(this::newFlowResourceAdapter);
    }

    @Override
    protected Vxlan allocateEntity(String flowId, PathId pathId, long entityId) {
        Vxlan entity = newTransitVxLan(flowId, pathId, entityId);
        vxlanRepository.add(entity);
        return entity;
    }

    @Override
    protected void deallocateTransaction(PathId pathId) {
        vxlanRepository.findByPathId(pathId, null)
                .forEach(this::deallocateTransaction);
    }

    private void deallocateTransaction(Vxlan entity) {
        poolManager.deallocate(() -> {
            vxlanRepository.remove(entity);
            return (long) entity.getVni();
        });
    }

    @Override
    protected VxlanEncapsulation newFlowResourceAdapter(Vxlan entity) {
        return VxlanEncapsulation.builder()
                .vxlan(entity)
                .build();
    }

    private Vxlan newTransitVxLan(String flowId, PathId pathId, long vxLanVni) {
        return Vxlan.builder()
                .flowId(flowId)
                .pathId(pathId)
                .vni((int) vxLanVni)
                .build();
    }
}
