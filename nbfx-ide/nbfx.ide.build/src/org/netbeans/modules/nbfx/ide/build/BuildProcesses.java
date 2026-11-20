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
package org.netbeans.modules.nbfx.ide.build;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;

/**
 * Runs a build as processes: what a project type whose tool is run from the command line - Maven's
 * {@code mvn} - starts its build commands with, instead of running the tool inside the IDE. The
 * processes run one after the other in the background, each only if the one before succeeded,
 * their output streamed line by line and their error stream shown as errors; cancelling the build
 * ends the running process and the processes it started.
 */
public final class BuildProcesses {

    private BuildProcesses() {
    }

    /**
     * Starts the processes {@code steps} gives, reporting to {@code progress} from now until the
     * last one ends.
     *
     * @param name     the name of the build, for its progress
     * @param steps    gives the processes to run, in order. It is called on the background thread
     *                 that runs them, so it may resolve the project and read its files; an empty
     *                 list means the project has the command disabled, which the output then says
     * @param kinds    what a line of a process's output stream is, for its colour
     * @return the running build, to cancel it
     */
    public static BuildExecution start(String name, Supplier<List<BuildProcess>> steps, BuildOutput output,
            BuildProgress progress, Function<String, BuildOutput.Kind> kinds) {
        ProcessRunner.Control control = new ProcessRunner.Control();
        progress.start(name, control::stop);
        ProcessRunner.run(steps, output, kinds, control, progress::finish);
        return control::stop;
    }
}
