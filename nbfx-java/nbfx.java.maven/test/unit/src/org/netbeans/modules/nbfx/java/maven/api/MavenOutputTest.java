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
package org.netbeans.modules.nbfx.java.maven.api;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;

/** Verifies the kinds given to the lines of the {@code mvn} process. */
public class MavenOutputTest {

    @Test
    public void processLinesFollowTheirLevelPrefix() {
        assertEquals(Kind.ERROR, MavenGoals.lineKind("[ERROR] COMPILATION ERROR : "));
        assertEquals(Kind.ERROR, MavenGoals.lineKind("[ERROR] BUILD SUCCESS is not what this line says"));
        assertEquals(Kind.WARNING, MavenGoals.lineKind("[WARNING] Using platform encoding (UTF-8 actually)"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind("[INFO] Building mavenproject5 1.0-SNAPSHOT"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind("[INFO] ------------------------------------------------------------------------"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind("Hello World!"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind(""));
    }

    @Test
    public void processLinesShowGoalsAndTheResult() {
        assertEquals(Kind.MUTED,
                MavenGoals.lineKind("[INFO] --- resources:3.3.1:resources (default-resources) @ mavenproject5 ---"));
        assertEquals(Kind.SUCCESS, MavenGoals.lineKind("[INFO] BUILD SUCCESS"));
        assertEquals(Kind.ERROR, MavenGoals.lineKind("[INFO] BUILD FAILURE"));
    }
}
