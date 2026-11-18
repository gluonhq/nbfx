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

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;

/** Verifies the kinds given to the lines of Gradle's standard output. */
public class GradleOutputTest {

    @Test
    public void taskAndConfigurationLinesAreMuted() {
        assertEquals(Kind.MUTED, GradleTasks.lineKind("> Task :app:compileJava"));
        assertEquals(Kind.MUTED, GradleTasks.lineKind("> Task :app:processResources NO-SOURCE"));
        assertEquals(Kind.MUTED, GradleTasks.lineKind("> Configure project :app"));
    }

    @Test
    public void theResultIsASuccessOrAnError() {
        assertEquals(Kind.SUCCESS, GradleTasks.lineKind("BUILD SUCCESSFUL in 1s"));
        assertEquals(Kind.ERROR, GradleTasks.lineKind("BUILD FAILED in 2s"));
        assertEquals(Kind.ERROR, GradleTasks.lineKind("FAILURE: Build failed with an exception."));
    }

    @Test
    public void otherLinesAreNormal() {
        assertEquals(Kind.NORMAL, GradleTasks.lineKind("Hello World!"));
        assertEquals(Kind.NORMAL, GradleTasks.lineKind("2 actionable tasks: 2 executed"));
        assertEquals(Kind.NORMAL, GradleTasks.lineKind("  > Task in the middle"));
        assertEquals(Kind.NORMAL, GradleTasks.lineKind(""));
    }
}
