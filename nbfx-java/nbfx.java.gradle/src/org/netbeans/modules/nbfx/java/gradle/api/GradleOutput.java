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
package org.netbeans.modules.nbfx.java.gradle.api;

import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;

/**
 * What a line of Gradle's standard output is, for the colour the Output window gives it: the line
 * announcing a task or the configuration of a project muted, {@code BUILD SUCCESSFUL} a success,
 * a failure an error. Gradle writes its errors, and the compiler's messages, to its standard
 * error, which is shown as errors without asking here.
 */
final class GradleOutput {

    private GradleOutput() {
    }

    static Kind lineKind(String line) {
        if (line.startsWith("> Task ") || line.startsWith("> Configure project ")) {
            return Kind.MUTED;
        }
        if (line.startsWith("BUILD SUCCESSFUL")) {
            return Kind.SUCCESS;
        }
        if (line.startsWith("BUILD FAILED") || line.startsWith("FAILURE: ")) {
            return Kind.ERROR;
        }
        return Kind.NORMAL;
    }
}
