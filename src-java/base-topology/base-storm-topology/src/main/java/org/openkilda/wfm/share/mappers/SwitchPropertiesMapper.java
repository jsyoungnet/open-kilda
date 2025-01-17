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

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.model.SwitchPropertiesDto;
import org.openkilda.model.SwitchProperties;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Convert {@link SwitchProperties} to {@link SwitchPropertiesDto} and back.
 */
@Mapper
public interface SwitchPropertiesMapper {

    SwitchPropertiesMapper INSTANCE = Mappers.getMapper(SwitchPropertiesMapper.class);

    @Mapping(target = "switchId", source = "switchProperties.switchId")
    SwitchPropertiesDto map(SwitchProperties switchProperties);

    @Mapping(target = "switchObj", ignore = true)
    @Mapping(target = "inboundTelescopePort", ignore = true)
    @Mapping(target = "outboundTelescopePort", ignore = true)
    @Mapping(target = "telescopeIngressVlan", ignore = true)
    @Mapping(target = "telescopeEgressVlan", ignore = true)
    SwitchProperties map(SwitchPropertiesDto switchProperties);
}
