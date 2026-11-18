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

import java.io.File;
import java.nio.file.Files;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.nbfx.project.ui.api.BuildActionProvider;
import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;

/**
 * Runs a real Maven build through {@link MavenBuildActionProvider} inside the module system, to
 * verify the embedder builds the project of the folder: a goal that needs a project - here the
 * {@code validate} phase the project's {@code nbactions.xml} maps <em>Build</em> to, which binds
 * no plugin for a {@code pom} project and so downloads nothing - must find the POM.
 */
public class MavenBuildTest extends NbTestCase {

    public MavenBuildTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(MavenBuildTest.class)
                .clusters(".*")
                .enableModules(".*")
                .gui(false)
                .suite();
    }

    public void testBuildFindsThePomAndFollowsTheMapping() throws Exception {
        File dir = Files.createTempDirectory("nbfx-maven").toFile();
        Files.writeString(dir.toPath().resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.netbeans.nbfx.test</groupId>
                    <artifactId>maven-build-test</artifactId>
                    <version>1.0</version>
                    <packaging>pom</packaging>
                </project>
                """);
        Files.writeString(dir.toPath().resolve("nbactions.xml"), """
                <actions>
                    <action>
                        <actionName>build</actionName>
                        <goals>
                            <goal>validate</goal>
                        </goals>
                    </action>
                </actions>
                """);

        StringBuffer text = new StringBuffer();
        BuildOutput output = new BuildOutput() {
            @Override
            public void clear() {
            }

            @Override
            public void show() {
            }

            @Override
            public void append(String delta) {
                text.append(delta);
            }
        };
        BuildProgress progress = new BuildProgress() {
            @Override
            public void start(String name, Runnable cancel) {
            }

            @Override
            public void finish() {
            }
        };

        BuildActionProvider provider = new MavenBuildActionProvider();
        BuildExecution execution = provider.start(dir.toPath(), "build", output, progress);
        assertNotNull(execution);

        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline
                && text.indexOf("BUILD SUCCESSFUL") < 0 && text.indexOf("BUILD FAILED") < 0) {
            Thread.sleep(200);
        }
        assertFalse(text.toString(), text.indexOf("there is no POM in this directory") >= 0);
        assertTrue(text.toString(), text.indexOf("BUILD SUCCESSFUL") >= 0);
        assertTrue("the project being built is announced: " + text,
                text.indexOf("Building maven-build-test 1.0") >= 0);
    }
}
