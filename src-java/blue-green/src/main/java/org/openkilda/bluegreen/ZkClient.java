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

package org.openkilda.bluegreen;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.nio.file.Paths;

@Slf4j
@SuperBuilder
public abstract class ZkClient implements Watcher {
    private static final String ROOT = "/";
    private static final int DEFAULT_SESSION_TIMEOUT = 30000;

    protected String id;
    protected String serviceName;
    protected ZooKeeper zookeeper;
    protected final String connectionString;
    protected boolean nodesValidated;
    private final int sessionTimeout;

    public ZkClient(String id, String serviceName, String connectionString, int sessionTimeout) {
        if (sessionTimeout == 0) {
            sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        }
        this.nodesValidated = false;
        this.serviceName = serviceName;
        this.id = id;
        this.connectionString = connectionString;
        this.sessionTimeout = sessionTimeout;
    }

    public abstract void init();

    String getPaths(String... paths) {
        return Paths.get(ROOT, paths).toString();
    }

    boolean refreshConnection(KeeperState state) throws IOException {
        if (state == KeeperState.Disconnected || state == KeeperState.Expired) {
            initZk();
            init();
            return true;
        }
        return false;
    }

    protected void initZk() throws IOException {
        nodesValidated = false;
        zookeeper = getZk();
    }

    @VisibleForTesting
    ZooKeeper getZk() throws IOException {
        return new ZooKeeper(connectionString, sessionTimeout, this);
    }

    protected void ensureZNode(String... path) throws KeeperException, InterruptedException, IOException {
        ensureZNode(new byte[0], path); // by default we set empty value for zookeeper node
    }

    protected void ensureZNode(byte[] value, String... path) throws KeeperException, InterruptedException, IOException {
        String nodePath = getPaths(path);
        if (zookeeper == null) {
            initZk();
        }
        if (zookeeper.exists(nodePath, false) == null) {
            try {
                zookeeper.create(nodePath, value,
                        Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (Exception e) {
                log.error("Failed to ensure node: {}", nodePath);
            }
        }
        if (zookeeper.exists(nodePath, false) == null) {
            String message = String.format("Zk node %s still does not exists", nodePath);
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    void validateNodes() throws KeeperException, InterruptedException, IOException {
        ensureZNode(serviceName);
        ensureZNode(serviceName, id);
    }

    /**
     * Checks that connection to zookeeper is alive and nodes were validated.
     */
    public boolean isConnectionAlive() {
        if (zookeeper == null || !nodesValidated) {
            return false;
        }
        return zookeeper.getState().isAlive();
    }
}
