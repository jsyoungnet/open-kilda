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

package org.openkilda.wfm;

import lombok.Getter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;

public class CliArguments {
    @Option(name = "--local", usage = "Do not push topology onto storm server, execute it local.")
    @Getter
    private Boolean isLocal = false;

    @Option(name = "--name", usage = "Set topology name.")
    @Getter
    private String topologyName;

    @Option(name = "--local-execution-time", usage = "Work time limit, when started in \"local\" execution mode.")
    @Getter
    private Integer localExecutionTime;

    @Option(name = "--topology-config", metaVar = "CONFIG",
            usage = "Extra configuration file(s) (can accept multiple paths).")
    @Getter
    private File[] extraConfiguration = {};

    @Option(name = "--topology-definition",
            usage = "Topology definition file to override the build-in one.")
    @Getter
    private File topologyDefinitionFile;

    public CliArguments(String[] args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);

        if (topologyName != null && topologyName.isEmpty()) {
            topologyName = null;
        }
    }
}
