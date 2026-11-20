/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.nbfx.java.maven;

import java.nio.file.Path;
import java.util.List;
import org.netbeans.modules.nbfx.ide.build.BuildProcess;
import org.netbeans.modules.nbfx.ide.build.BuildProcesses;
import org.netbeans.modules.nbfx.java.maven.api.MavenGoals;
import org.netbeans.modules.nbfx.project.ui.api.BuildActionProvider;
import org.netbeans.modules.nbfx.project.ui.api.BuildCommands;
import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.lookup.ServiceProvider;

/** Maven build commands, run as {@code mvn} processes: see {@link MavenActions}. */
@ServiceProvider(service = BuildActionProvider.class)
@ProjectServiceProvider(service = BuildActionProvider.class, projectType = "org-netbeans-modules-maven")
public final class MavenBuildActionProvider implements BuildActionProvider {

    @Override
    public String projectTypeId() {
        return "maven";
    }

    @Override
    public String[] getSupportedActions() {
        return new String[] {
            BuildCommands.BUILD, BuildCommands.CLEAN, BuildCommands.REBUILD,
            BuildCommands.RUN, BuildCommands.TEST, BuildCommands.JAVADOC
        };
    }

    /**
     * The command line of {@code command}: of the last of its {@linkplain MavenActions#forCommand
     * processes}, the command itself. The runner calls this off the user interface thread.
     */
    @Override
    public List<String> commandLine(Path dir, String command) {
        List<BuildProcess> processes = MavenActions.forCommand(dir, command);
        return processes == null || processes.isEmpty() ? null : processes.get(processes.size() - 1).commandLine();
    }

    /**
     * Starts {@code command} as {@code mvn} processes, with what the project's {@code nbactions.xml}
     * maps it to or else its default goals. The project is only read once the build's own thread
     * runs: nothing here blocks the caller.
     */
    @Override
    public BuildExecution start(Path dir, String command, BuildOutput output, BuildProgress progress) {
        if (MavenActions.defaultGoals(command) == null) {
            return null;
        }
        return BuildProcesses.start("maven", () -> MavenActions.forCommand(dir, command), output, progress,
                MavenGoals::lineKind);
    }

    @Override
    public BuildOutput.Kind lineKind(String line) {
        return MavenGoals.lineKind(line);
    }
}
