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
package org.netbeans.modules.nbfx.java.j2seproject;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;

/** Verifies the kinds the Ant provider gives the lines of an {@code ant} process. */
public class AntBuildActionProviderTest {

    private final AntBuildActionProvider provider = new AntBuildActionProvider();

    @Test
    public void targetNamesAreMuted() {
        assertEquals(Kind.MUTED, provider.lineKind("run:"));
        assertEquals(Kind.MUTED, provider.lineKind("-deps-jar-init:"));
        assertEquals(Kind.MUTED, provider.lineKind("-do-compile:"));
    }

    @Test
    public void theResultIsASuccessOrAnError() {
        assertEquals(Kind.SUCCESS, provider.lineKind("BUILD SUCCESSFUL"));
        assertEquals(Kind.SUCCESS, provider.lineKind("BUILD SUCCESSFUL (total time: 0 seconds)"));
        assertEquals(Kind.ERROR, provider.lineKind("BUILD FAILED"));
    }

    @Test
    public void taskOutputIsNormal() {
        assertEquals(Kind.NORMAL, provider.lineKind("   [delete] Deleting: /tmp/built-jar.properties"));
        assertEquals(Kind.NORMAL, provider.lineKind("     [java] Result:"));
        assertEquals(Kind.NORMAL, provider.lineKind("Total time: 0 seconds"));
        assertEquals(Kind.NORMAL, provider.lineKind("Buildfile: /tmp/build.xml"));
        assertEquals(Kind.NORMAL, provider.lineKind("Hello World:"));
        assertEquals(Kind.NORMAL, provider.lineKind(""));
    }
}
