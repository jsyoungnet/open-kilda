/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.model;

import com.google.common.collect.ImmutableList;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
public class Group implements Serializable {
    GroupId id;
    Type type;
    List<PingContext> records;

    public Group(GroupId id, Type type, List<PingContext> records) {
        this.id = id;
        this.type = type;
        this.records = ImmutableList.copyOf(records);
    }

    public enum Type {
        FLOW,
        Y_FLOW
    }
}
