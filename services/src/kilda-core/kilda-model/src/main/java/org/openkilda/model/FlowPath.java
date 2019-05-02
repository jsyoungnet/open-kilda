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

package org.openkilda.model;

import static com.google.common.base.Preconditions.checkArgument;
import static org.neo4j.ogm.annotation.Relationship.INCOMING;
import static org.neo4j.ogm.annotation.Relationship.OUTGOING;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.PostLoad;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a flow path.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "segments"})
@ToString(exclude = {"flow"})
@NodeEntity(label = "flow_path")
public class FlowPath implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "path_id")
    @Index(unique = true)
    @Convert(graphPropertyType = String.class)
    private PathId pathId;

    @NonNull
    @Relationship(type = "source", direction = OUTGOING)
    private Switch srcSwitch;

    @NonNull
    @Relationship(type = "destination", direction = OUTGOING)
    private Switch destSwitch;

    @NonNull
    @Relationship(type = "owns", direction = INCOMING)
    private Flow flow;

    @Convert(graphPropertyType = Long.class)
    private Cookie cookie;

    @Property(name = "meter_id")
    @Convert(graphPropertyType = Long.class)
    private MeterId meterId;

    private long latency;

    private long bandwidth;

    @Property(name = "ignore_bandwidth")
    private boolean ignoreBandwidth;

    @Property(name = "time_create")
    @Convert(InstantStringConverter.class)
    private Instant timeCreate;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    @NonNull
    @Property(name = "status")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private FlowPathStatus status;

    @Relationship(type = "owns", direction = OUTGOING)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<PathSegment> segments = Collections.emptyList();

    @Builder(toBuilder = true)
    public FlowPath(@NonNull PathId pathId, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                    @NonNull Flow flow, Cookie cookie, MeterId meterId,
                    long latency, long bandwidth, boolean ignoreBandwidth,
                    Instant timeCreate, Instant timeModify,
                    FlowPathStatus status, List<PathSegment> segments) {
        this.pathId = pathId;
        this.srcSwitch = srcSwitch;
        this.destSwitch = destSwitch;
        this.flow = flow;
        this.cookie = cookie;
        this.meterId = meterId;
        this.latency = latency;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.timeCreate = timeCreate;
        this.timeModify = timeModify;
        this.status = status;
        setSegments(segments != null ? segments : Collections.emptyList());
    }

    /**
     * Checks whether the flow path goes through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isOneSwitchFlow() {
        return srcSwitch.getSwitchId().equals(destSwitch.getSwitchId());
    }

    public List<PathSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * Set the segments.
     */
    public final void setSegments(List<PathSegment> segments) {
        segments.forEach(segment -> checkArgument(Objects.equals(segment.getPath(), this),
                "Segment %s and the path %s don't reference each other, but expected.",
                segment, this));

        for (int idx = 0; idx < segments.size(); idx++) {
            PathSegment segment = segments.get(idx);
            segment.setSeqId(idx);
        }

        this.segments = segments;
    }

    @PostLoad
    private void sortSegmentsOnLoad() {
        if (segments != null) {
            segments = segments.stream()
                    .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                    .collect(Collectors.toList());
        }
    }
}
