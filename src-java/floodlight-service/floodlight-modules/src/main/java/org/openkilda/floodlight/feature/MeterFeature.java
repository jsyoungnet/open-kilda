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

package org.openkilda.floodlight.feature;

import org.openkilda.model.SwitchFeature;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.TableFeatures;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropInstructions;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.instructionid.OFInstructionId;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TableId;

import java.util.Objects;
import java.util.Optional;

@Slf4j
public class MeterFeature extends AbstractFeature {
    private final boolean isOvsMetersEnabled;

    public MeterFeature(boolean isOvsMetersEnabled) {
        this.isOvsMetersEnabled  = isOvsMetersEnabled;
    }

    @Override
    public Optional<SwitchFeature> discover(IOFSwitch sw) {
        Optional<SwitchFeature> empty = Optional.empty();
        if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) < 0) {
            return empty;
        }
        if (MANUFACTURER_NICIRA.equals(sw.getSwitchDescription().getManufacturerDescription()) && !isOvsMetersEnabled) {
            return empty;
        }
        if (! checkTableFeatures(sw)) {
            return empty;
        }

        return Optional.of(SwitchFeature.METERS);
    }


    private boolean checkTableFeatures(IOFSwitch sw) {
        boolean haveSupport = false;
        DatapathId swId = sw.getId();
        for (int i = 0; i < sw.getNumTables(); i++) {
            TableFeatures tableFeatures = sw.getTableFeatures(TableId.of(i));
            log.debug("Detect meters support by analysing table features sw={} features={}", swId, tableFeatures);
            haveSupport |= checkTableFeatures(tableFeatures);
        }
        log.debug(
                "Detection result for meters support by analysing tables features for {} is meters are {}", swId,
                haveSupport ? "supported" : "NOT supported");
        return haveSupport;
    }

    private boolean checkTableFeatures(TableFeatures tableFeatures) {
        OFTableFeaturePropInstructions instructions = tableFeatures.getPropInstructions();
        if (instructions == null) {
            return false;
        }

        for (OFInstructionId entry : instructions.getInstructionIds()) {
            if (Objects.equals(entry.getType(), OFInstructionType.METER)) {
                return true;
            }
        }
        return false;
    }
}
