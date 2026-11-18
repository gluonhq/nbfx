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
import static org.junit.Assert.assertNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.junit.Test;

/**
 * Verifies that a build is pointed at its project: without the POM on the request Maven builds no
 * project, and every goal needing one fails with "there is no POM in this directory".
 */
public class MavenBuildRequestTest {

    @Test
    public void theRequestNamesThePomOfTheProject() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-maven-request").toRealPath();
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();

        MavenBuild.configure(request, dir);

        assertEquals(dir.resolve("pom.xml").toFile(), request.getPom());
        assertEquals(dir.toString(), request.getBaseDirectory());
        assertEquals(dir.toFile(), request.getMultiModuleProjectDirectory());
    }

    @Test
    public void aFolderWithoutAPomGetsNone() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-maven-request").toRealPath();
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();

        MavenBuild.configure(request, dir);

        assertNull(request.getPom());
        assertEquals(new File(dir.toString()), new File(request.getBaseDirectory()));
    }
}
