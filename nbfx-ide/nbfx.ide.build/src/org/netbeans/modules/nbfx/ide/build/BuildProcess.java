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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One process of a build run from the command line: what to run, where, and what it finds in its
 * environment on top of the IDE's own.
 *
 * @param commandLine the executable and its arguments
 * @param directory   the working directory of the process
 * @param environment the environment variables to set, by name; never {@code null}
 */
public record BuildProcess(List<String> commandLine, Path directory, Map<String, String> environment) {

    public BuildProcess {
        commandLine = List.copyOf(commandLine);
        Objects.requireNonNull(directory, "directory");
        environment = Map.copyOf(environment);
    }

    /** A process with the IDE's environment. */
    public BuildProcess(List<String> commandLine, Path directory) {
        this(commandLine, directory, Map.of());
    }
}
