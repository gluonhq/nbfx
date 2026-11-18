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

import org.netbeans.modules.nbfx.project.ui.api.BuildActionProvider;
import org.netbeans.modules.nbfx.project.ui.api.BuildCommands;
import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;
import org.netbeans.modules.nbfx.java.maven.api.MavenGoals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.lookup.ServiceProvider;

/** Maven build commands, run in-process via {@link MavenBuild} (CLI fallback via mvn/mvnw). */
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

    @Override
    public List<String> commandLine(Path dir, String command) {
        MavenActions.Invocation invocation = MavenActions.forCommand(dir, command);
        if (invocation == null) {
            return null;
        }
        List<String> line = new ArrayList<>();
        line.add(executable(dir, "mvnw", "mvn"));
        if (!invocation.profiles().isEmpty()) {
            line.add("-P" + String.join(",", invocation.profiles()));
        }
        invocation.properties().forEach((name, value) -> line.add("-D" + name + "=" + value));
        line.addAll(invocation.goals());
        return List.copyOf(line);
    }

    /** Runs what the project's {@code nbactions.xml} maps {@code command} to, or else its default goals. */
    @Override
    public BuildExecution start(Path dir, String command, BuildOutput output, BuildProgress progress) {
        MavenActions.Invocation invocation = MavenActions.forCommand(dir, command);
        if (invocation == null) {
            return null;
        }
        return MavenGoals.start(dir, invocation.goals(), invocation.properties(), invocation.profiles(),
                output, progress);
    }

    private static String executable(Path dir, String wrapper, String fallback) {
        Path unix = dir.resolve(wrapper);
        if (Files.isRegularFile(unix)) {
            return unix.toString();
        }
        Path windows = dir.resolve(wrapper + ".cmd");
        return Files.isRegularFile(windows) ? windows.toString() : fallback;
    }

    @Override
    public BuildOutput.Kind lineKind(String line) {
        return MavenGoals.lineKind(line);
    }
}
