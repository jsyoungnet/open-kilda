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

package org.openkilda.wfm.share.flow.resources;

import org.openkilda.model.Flow;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionRequired;
import org.openkilda.wfm.share.utils.PoolManager;

import java.util.Optional;

public abstract class EncapsulationResourcesProvider<A extends EncapsulationResources, E> {
    protected final TransactionManager transactionManager;

    public EncapsulationResourcesProvider(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
    }

    /**
     * Allocates flow encapsulation resources for the flow path.
     */
    public A allocate(Flow flow, PathId pathId, PathId oppositePathId) throws ResourceNotAvailableException {
        return get(oppositePathId, null)
                .orElseGet(() -> allocate(flow, pathId));
    }

    @TransactionRequired
    private A allocate(Flow flow, PathId pathId) {
        E entity = getPoolManager().allocate(
                entityId -> allocateEntity(flow.getFlowId(), pathId, entityId));
        return newFlowResourceAdapter(entity);
    }

    /**
     * Get allocated encapsulation resources of the flow path.
     */
    public abstract Optional<A> get(PathId pathId, PathId oppositePathId);

    /**
     * Deallocates flow encapsulation resources of the path.
     */
    public void deallocate(PathId pathId) {
        transactionManager.doInTransaction(() -> deallocateTransaction(pathId));
    }

    protected abstract E allocateEntity(String flowId, PathId pathId, long entityId);

    protected abstract void deallocateTransaction(PathId pathId);

    protected abstract A newFlowResourceAdapter(E entity);

    protected abstract PoolManager<E> getPoolManager();
}
