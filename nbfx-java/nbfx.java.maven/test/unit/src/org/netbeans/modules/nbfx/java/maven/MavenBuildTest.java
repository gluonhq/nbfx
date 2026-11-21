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
import java.util.List;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.nbfx.project.ui.api.BuildActionProvider;
import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;

/**
 * Runs Maven through {@link MavenBuildActionProvider} inside the module system, where NetBeans'
 * project system resolves the project's {@code nbactions.xml}: a real {@code mvn} process builds
 * the project of the folder - the {@code validate} phase, which binds no plugin for a {@code pom}
 * project and so needs no repository -, and a mapping's goals, properties, profiles and disabling
 * are what NetBeans makes of them.
 */
public class MavenBuildTest extends NbTestCase {

    private static final String POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.netbeans.nbfx.test</groupId>
                <artifactId>maven-build-test</artifactId>
                <version>1.0</version>
                <packaging>pom</packaging>
            </project>
            """;

    private static final String ACTIONS = """
            <actions>
                <action>
                    <actionName>build</actionName>
                    <goals>
                        <goal>validate</goal>
                    </goals>
                    <properties>
                        <nbfx.test>mapped</nbfx.test>
                        <Env.NBFX_TEST>from-the-mapping</Env.NBFX_TEST>
                    </properties>
                    <activatedProfiles>
                        <activatedProfile>ide</activatedProfile>
                    </activatedProfiles>
                </action>
                <action>
                    <actionName>test</actionName>
                    <goals/>
                </action>
                <action>
                    <actionName>clean</actionName>
                    <packagings>
                        <packaging>war</packaging>
                    </packagings>
                    <goals>
                        <goal>never-for-a-pom-project</goal>
                    </goals>
                </action>
            </actions>
            """;

    public MavenBuildTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(MavenBuildTest.class)
                .clusters(".*")
                // every module but the GraalVM-only autoload, which cannot be enabled on a plain JDK
                .enableModules("(?!org\\.netbeans\\.libs\\.graalsdk\\.system).*")
                .gui(false)
                .suite();
    }

    public void testBuildRunsTheMappingAsAProcess() throws Exception {
        File dir = project();
        StringBuffer text = new StringBuffer();

        BuildActionProvider provider = new MavenBuildActionProvider();
        BuildExecution execution = provider.start(dir.toPath(), "build", output(text), progress());
        assertNotNull(execution);
        await(text, "[exit ");

        assertTrue("the mapping's goal, property and profile reach mvn: " + text,
                text.indexOf("-Dnbfx.test=mapped") >= 0 && text.indexOf("-Pide") >= 0
                        && text.indexOf(" validate") >= 0);
        assertFalse(text.toString(), text.indexOf("there is no POM in this directory") >= 0);
        assertTrue("mvn built the project of the folder: " + text, text.indexOf("maven-build-test") >= 0);
        assertTrue(text.toString(), text.indexOf("BUILD SUCCESS") >= 0);
        assertTrue(text.toString(), text.indexOf("[exit 0]") >= 0);
    }

    public void testNetBeansResolvesTheMapping() throws Exception {
        File dir = project();

        List<org.netbeans.modules.nbfx.ide.build.BuildProcess> build = MavenActions.forCommand(dir.toPath(), "build");

        assertEquals(1, build.size());
        assertEquals("from-the-mapping", build.get(0).environment().get("NBFX_TEST"));
        assertFalse("an environment entry is not a property",
                build.get(0).commandLine().stream().anyMatch(argument -> argument.contains("NBFX_TEST")));
        assertEquals("a mapping without goals disables the command",
                List.of(), MavenActions.forCommand(dir.toPath(), "test"));
        List<String> clean = MavenActions.forCommand(dir.toPath(), "clean").get(0).commandLine();
        assertEquals("a mapping for another packaging does not apply: the default goal runs",
                "clean", clean.get(clean.size() - 1));
        assertEquals("a pom project has nothing to run", List.of(), MavenActions.forCommand(dir.toPath(), "run"));
    }

    public void testADisabledCommandRunsNothing() throws Exception {
        File dir = project();
        StringBuffer text = new StringBuffer();

        new MavenBuildActionProvider().start(dir.toPath(), "test", output(text), progress());
        await(text, "not available");

        assertFalse("nothing was started: " + text, text.indexOf("$ ") >= 0);
    }

    private static File project() throws Exception {
        File dir = Files.createTempDirectory("nbfx-maven").toFile();
        Files.writeString(dir.toPath().resolve("pom.xml"), POM);
        Files.writeString(dir.toPath().resolve("nbactions.xml"), ACTIONS);
        return dir;
    }

    private static void await(StringBuffer text, String marker) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline && text.indexOf(marker) < 0) {
            Thread.sleep(100);
        }
        assertTrue("no \"" + marker + "\" in: " + text, text.indexOf(marker) >= 0);
    }

    private static BuildOutput output(StringBuffer text) {
        return new BuildOutput() {
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
    }

    private static BuildProgress progress() {
        return new BuildProgress() {
            @Override
            public void start(String name, Runnable cancel) {
            }

            @Override
            public void finish() {
            }
        };
    }
}
