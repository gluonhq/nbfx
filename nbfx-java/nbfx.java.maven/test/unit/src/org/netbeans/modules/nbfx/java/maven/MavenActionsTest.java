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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.netbeans.modules.nbfx.ide.build.BuildProcess;
import org.netbeans.modules.nbfx.java.maven.MavenActions.Invocation;
import org.netbeans.modules.nbfx.project.ui.api.BuildCommands;

/**
 * Verifies what a build command runs without a project system: the {@code mvn} command line of an
 * invocation, the default goals, <em>Run</em> from the plugins of the POM, and which of the
 * project's action files map a command. What NetBeans makes of a mapping needs its project system:
 * {@link MavenBuildTest} covers it.
 */
public class MavenActionsTest {

    private static final String ACTIONS = """
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
                    <actionName>test</actionName>
                    <goals/>
                </action>
            </actions>
            """;

    @Test
    public void aPlainInvocationIsItsGoalsInBatchMode() {
        Path dir = Path.of("project").toAbsolutePath();

        BuildProcess process = Invocation.plain(dir, List.of("clean", "package"), Map.of("skipTests", "true"))
                .process("mvn");

        assertEquals(List.of("mvn", "-DskipTests=true", "--batch-mode", "clean", "package"), process.commandLine());
        assertEquals(dir, process.directory());
        assertEquals("Maven runs on the JDK of the IDE", System.getProperty("java.home"),
                process.environment().get("JAVA_HOME"));
    }

    /** Everything a NetBeans run configuration can ask of {@code mvn}, in NetBeans' order. */
    @Test
    public void anInvocationCarriesItsOptionsFlagsAndEnvironment() {
        Path dir = Path.of("reactor").toAbsolutePath();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("exec.args", "-Xmx1g");
        properties.put("Env.DISPLAY", ":1");
        properties.put("Env.JAVA_HOME", "/opt/jdk");
        properties.put("NbIde.internal", "x");
        Map<String, String> options = new LinkedHashMap<>();
        options.put("fail-at-end", "");
        options.put("threads", "2");
        Invocation invocation = new Invocation(dir, List.of("install"), properties, List.of("ide", "fast"), options,
                false, true, true, true, true, "also-make", "app");

        assertEquals(List.of("--fail-at-end", "--threads", "2", "-Dexec.args=-Xmx1g", "--offline", "--batch-mode",
                "--non-recursive", "--debug", "--errors", "--update-snapshots", "--also-make", "--projects", "app",
                "-Pide,fast", "install"), invocation.arguments());
        assertEquals(":1", invocation.environment().get("DISPLAY"));
        assertEquals("the mapping names the JDK", "/opt/jdk", invocation.environment().get("JAVA_HOME"));
        assertFalse(invocation.environment().containsKey("Env.DISPLAY"));
    }

    @Test
    public void aReactorFlagAboutTheFolderItselfIsDropped() {
        Invocation invocation = new Invocation(Path.of("p").toAbsolutePath(), List.of("install"), Map.of(), List.of(),
                Map.of(), true, false, false, false, false, "also-make", ".");

        assertEquals(List.of("--batch-mode", "install"), invocation.arguments());
    }

    @Test
    public void withoutAMappingTheDefaultGoalsRun() throws Exception {
        Path dir = project(null, pomWith("", ""));

        assertEquals(List.of("package"), goalsOf(MavenActions.forCommand(dir, BuildCommands.BUILD)));
        assertEquals(List.of("clean", "package"), goalsOf(MavenActions.forCommand(dir, BuildCommands.REBUILD)));
        assertEquals(List.of("compile", "exec:java"), goalsOf(MavenActions.forCommand(dir, BuildCommands.RUN)));
        assertNull(MavenActions.forCommand(dir, "no-such-command"));
        assertNull(new MavenBuildActionProvider().commandLine(dir, "no-such-command"));
    }

    /** Without a mapping, Run is the goal of the plugin the POM builds with to run that kind of application. */
    @Test
    public void runFollowsTheBuildPluginsOfThePom() throws Exception {
        Path javafx = project(null, pomWith("", """
                <build><plugins>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId></plugin>
                    <plugin><groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId><version>0.0.8</version></plugin>
                </plugins></build>
                """));
        Path gluon = project(null, pomWith("", """
                <build><plugins>
                    <plugin><groupId>com.gluonhq</groupId><artifactId>gluonfx-maven-plugin</artifactId></plugin>
                    <plugin><groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId></plugin>
                </plugins></build>
                """));

        assertEquals(List.of("javafx:run"), goalsOf(MavenActions.forCommand(javafx, BuildCommands.RUN)));
        assertEquals("the JavaFX plugin runs a Gluon project on the desktop",
                List.of("javafx:run"), goalsOf(MavenActions.forCommand(gluon, BuildCommands.RUN)));
        assertEquals("only Run depends on the plugins",
                List.of("package"), goalsOf(MavenActions.forCommand(javafx, BuildCommands.BUILD)));
        assertEquals(List.of("spring-boot:run"),
                MavenActions.runGoals(Set.of("org.springframework.boot:spring-boot-maven-plugin")));
    }

    /** A plugin an aggregator only manages for its modules is not one it runs. */
    @Test
    public void aManagedPluginIsNotARunner() throws Exception {
        Path managed = project(null, pomWith("", """
                <build><pluginManagement><plugins>
                    <plugin><groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId></plugin>
                </plugins></pluginManagement></build>
                """));

        assertEquals(Set.of(), MavenActions.pluginsOf(managed));
        assertEquals(List.of("compile", "exec:java"), goalsOf(MavenActions.forCommand(managed, BuildCommands.RUN)));
    }

    /** A parent or an aggregator has nothing to run; its other commands are untouched. */
    @Test
    public void aPomProjectHasNothingToRun() throws Exception {
        Path parent = project(null, pomWith("<packaging>pom</packaging>", """
                <build><plugins>
                    <plugin><groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId></plugin>
                </plugins></build>
                """));

        assertEquals("pom", MavenActions.packagingOf(parent));
        assertEquals(List.of(), MavenActions.forCommand(parent, BuildCommands.RUN));
        assertEquals(List.of("package"), goalsOf(MavenActions.forCommand(parent, BuildCommands.BUILD)));
    }

    /** Only whether, and to which goals, the project's action files map a command is read here. */
    @Test
    public void theActionFilesOfTheProjectSayWhichCommandsItMaps() throws Exception {
        Path dir = project(ACTIONS, pomWith("", ""));
        Files.writeString(dir.resolve("nbactions-release.xml"), """
                <actions>
                    <action>
                        <actionName>run</actionName>
                        <goals><goal>verify</goal></goals>
                    </action>
                </actions>
                """);

        List<List<String>> run = MavenActions.mappedGoals(dir, "run");
        assertEquals(2, run.size());
        assertTrue(run.contains(List.of("clean", "javafx:run")));
        assertTrue("the mappings of the project's configurations count", run.contains(List.of("verify")));
        assertEquals(List.of(List.of()), MavenActions.mappedGoals(dir, "test"));
        assertEquals(List.of(), MavenActions.mappedGoals(dir, "build"));
    }

    /** A mapping without goals disables the command, as in NetBeans: nothing runs, the default goals included. */
    @Test
    public void aMappingWithoutGoalsDisablesTheCommand() throws Exception {
        Path dir = project(ACTIONS, pomWith("", ""));

        assertEquals(List.of(), MavenActions.forCommand(dir, BuildCommands.TEST));
        assertNull("a disabled command has no command line", new MavenBuildActionProvider().commandLine(dir, BuildCommands.TEST));
    }

    @Test
    public void anUnreadableActionFileMapsNothing() throws Exception {
        Path dir = project("<actions><action><actionName>run</actionName>", pomWith("", ""));

        assertEquals(List.of(), MavenActions.mappedGoals(dir, "run"));
        assertEquals(List.of("compile", "exec:java"), goalsOf(MavenActions.forCommand(dir, BuildCommands.RUN)));
    }

    /** The goals of the one process {@code processes} is: what follows {@code --batch-mode} on its command line. */
    private static List<String> goalsOf(List<BuildProcess> processes) {
        assertEquals(1, processes.size());
        List<String> line = processes.get(0).commandLine();
        return line.subList(line.indexOf("--batch-mode") + 1, line.size());
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

    private static String pomWith(String packaging, String build) {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.netbeans.nbfx.test</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                """ + packaging + build + "</project>\n";
    }
}
