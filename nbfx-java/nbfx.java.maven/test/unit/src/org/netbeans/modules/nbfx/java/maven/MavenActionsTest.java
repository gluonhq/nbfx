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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.netbeans.modules.nbfx.java.maven.MavenActions.Invocation;
import org.netbeans.modules.nbfx.project.ui.api.BuildCommands;

/** Verifies what a build command runs: the project's {@code nbactions.xml} mapping, or else the default goals. */
public class MavenActionsTest {

    /** The mapping of the OpenJFX samples: Run is {@code clean javafx:run}. */
    private static final String JAVAFX_ACTIONS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <actions>
                <action>
                    <actionName>run</actionName>
                    <packagings>
                        <packaging>jar</packaging>
                    </packagings>
                    <goals>
                        <goal>clean</goal>
                        <goal>javafx:run</goal>
                    </goals>
                </action>
                <action>
                    <actionName>debug</actionName>
                    <goals>
                        <goal>clean</goal>
                        <goal>javafx:run@ide-debug</goal>
                    </goals>
                    <properties>
                        <jpda.listen>true</jpda.listen>
                        <Env.JAVA_TOOL_OPTIONS>-Xmx1g</Env.JAVA_TOOL_OPTIONS>
                    </properties>
                    <activatedProfiles>
                        <activatedProfile>ide</activatedProfile>
                    </activatedProfiles>
                </action>
                <action>
                    <actionName>test</actionName>
                    <goals/>
                </action>
            </actions>
            """;

    @Test
    public void theProjectsMappingWins() throws Exception {
        Path dir = project(JAVAFX_ACTIONS);

        Invocation run = MavenActions.forCommand(dir, BuildCommands.RUN);

        assertEquals(List.of("clean", "javafx:run"), run.goals());
        assertEquals(Map.of(), run.properties());
        assertEquals(List.of(), run.profiles());
    }

    @Test
    public void aMappingCarriesItsPropertiesAndProfiles() throws Exception {
        Path dir = project(JAVAFX_ACTIONS);

        Invocation debug = MavenActions.mapped(dir.resolve(MavenActions.ACTIONS_FILE), "debug", "jar");

        assertEquals(List.of("clean", "javafx:run@ide-debug"), debug.goals());
        assertEquals("environment entries are not properties", Map.of("jpda.listen", "true"), debug.properties());
        assertEquals(List.of("ide"), debug.profiles());
    }

    @Test
    public void aMappingAppliesToItsPackagingsOnly() throws Exception {
        Path actions = project(JAVAFX_ACTIONS).resolve(MavenActions.ACTIONS_FILE);

        assertEquals(List.of("clean", "javafx:run"), MavenActions.mapped(actions, "run", "jar").goals());
        assertNull(MavenActions.mapped(actions, "run", "war"));
        assertEquals("an unknown packaging takes any mapping of the action",
                List.of("clean", "javafx:run"), MavenActions.mapped(actions, "run", null).goals());
    }

    @Test
    public void withoutAMappingTheDefaultGoalsRun() throws Exception {
        Path mapped = project(JAVAFX_ACTIONS);
        Path plain = project(null);

        assertEquals(List.of("package"), MavenActions.forCommand(mapped, BuildCommands.BUILD).goals());
        assertEquals("a mapping without goals is disabled",
                List.of("test"), MavenActions.forCommand(mapped, BuildCommands.TEST).goals());
        assertEquals(List.of("compile", "exec:java"), MavenActions.forCommand(plain, BuildCommands.RUN).goals());
        assertEquals(List.of("clean", "package"), MavenActions.forCommand(plain, BuildCommands.REBUILD).goals());
        assertNull(MavenActions.forCommand(plain, "no-such-command"));
    }

    /** Without a mapping, Run is the goal of the plugin the POM configures to run that kind of application. */
    @Test
    public void runFollowsThePluginsOfThePom() throws Exception {
        Path javafx = project(null, pomWith("""
                <build><plugins>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId></plugin>
                    <plugin><groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId><version>0.0.8</version></plugin>
                </plugins></build>
                """));
        Path gluon = project(null, pomWith("""
                <build><plugins>
                    <plugin><groupId>com.gluonhq</groupId><artifactId>gluonfx-maven-plugin</artifactId></plugin>
                    <plugin><groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId></plugin>
                </plugins></build>
                """));
        Path managed = project(null, pomWith("""
                <build><pluginManagement><plugins>
                    <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
                </plugins></pluginManagement></build>
                """));
        Path plain = project(null, pomWith(""));

        assertEquals(List.of("javafx:run"), MavenActions.forCommand(javafx, BuildCommands.RUN).goals());
        assertEquals("the JavaFX plugin runs a Gluon project on the desktop",
                List.of("javafx:run"), MavenActions.forCommand(gluon, BuildCommands.RUN).goals());
        assertEquals(List.of("spring-boot:run"), MavenActions.forCommand(managed, BuildCommands.RUN).goals());
        assertEquals(List.of("compile", "exec:java"), MavenActions.forCommand(plain, BuildCommands.RUN).goals());
        assertEquals("only Run depends on the plugins",
                List.of("package"), MavenActions.forCommand(javafx, BuildCommands.BUILD).goals());
        assertEquals(List.of("mvn", "javafx:run"),
                new MavenBuildActionProvider().commandLine(javafx, BuildCommands.RUN));
    }

    /** The project's own mapping still wins over what its plugins suggest. */
    @Test
    public void aMappingWinsOverThePlugins() throws Exception {
        Path dir = project(JAVAFX_ACTIONS, pomWith("""
                <build><plugins>
                    <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
                </plugins></build>
                """));

        assertEquals(List.of("clean", "javafx:run"), MavenActions.forCommand(dir, BuildCommands.RUN).goals());
    }

    @Test
    public void anUnreadableMappingFileFallsBackToTheDefaults() throws Exception {
        Path dir = project("<actions><action><actionName>run</actionName>");

        assertEquals(List.of("compile", "exec:java"), MavenActions.forCommand(dir, BuildCommands.RUN).goals());
    }

    @Test
    public void theCommandLineCarriesTheMapping() throws Exception {
        Path dir = project(JAVAFX_ACTIONS);
        MavenBuildActionProvider provider = new MavenBuildActionProvider();

        assertEquals(List.of("mvn", "clean", "javafx:run"), provider.commandLine(dir, BuildCommands.RUN));
        assertEquals(List.of("mvn", "package"), provider.commandLine(dir, BuildCommands.BUILD));
        assertNull(provider.commandLine(dir, "no-such-command"));
    }

    /** A folder with a minimal POM and, unless {@code actions} is {@code null}, that {@code nbactions.xml}. */
    private static Path project(String actions) throws Exception {
        return project(actions, pomWith(""));
    }

    /** A folder with the POM {@code pom} and, unless {@code actions} is {@code null}, that {@code nbactions.xml}. */
    private static Path project(String actions, String pom) throws Exception {
        Path dir = Files.createTempDirectory("nbfx-maven-actions");
        Files.writeString(dir.resolve("pom.xml"), pom);
        if (actions != null) {
            Files.writeString(dir.resolve(MavenActions.ACTIONS_FILE), actions);
        }
        return dir;
    }

    private static String pomWith(String build) {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.netbeans.nbfx.test</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                """ + build + "</project>\n";
    }
}
