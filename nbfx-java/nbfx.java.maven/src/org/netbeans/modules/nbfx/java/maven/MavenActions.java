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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.execute.model.ActionToGoalMapping;
import org.netbeans.modules.maven.execute.model.NetbeansActionMapping;
import org.netbeans.modules.maven.execute.model.io.xpp3.NetbeansBuildActionXpp3Reader;
import org.netbeans.modules.nbfx.project.ui.api.BuildCommands;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * What Maven runs for a build command of a project: the goals, properties and profiles the
 * project's own {@code nbactions.xml} maps the command to, as NetBeans - a JavaFX project maps
 * <em>Run</em> to {@code clean javafx:run} there -, or else the default goals of the command,
 * which for <em>Run</em> depend on the plugins the project configures.
 */
final class MavenActions {

    private static final Logger LOG = Logger.getLogger(MavenActions.class.getName());

    /** The file a project keeps its own action mappings in, next to its POM. */
    static final String ACTIONS_FILE = "nbactions.xml";

    /** What to run: the goals, the user properties ({@code -Dname=value}) and the profiles to activate. */
    record Invocation(List<String> goals, Map<String, String> properties, List<String> profiles) {

        Invocation {
            goals = List.copyOf(goals);
            properties = Map.copyOf(properties);
            profiles = List.copyOf(profiles);
        }
    }

    private MavenActions() {
    }

    /**
     * What {@code command} runs in the project at {@code dir}; {@code null} for a command Maven
     * projects do not support.
     */
    static Invocation forCommand(Path dir, String command) {
        Invocation mapped = mapped(dir.resolve(ACTIONS_FILE), command, packagingOf(dir));
        if (mapped != null) {
            return mapped;
        }
        List<String> goals = BuildCommands.RUN.equals(command) ? runGoals(pluginsOf(dir)) : defaultGoals(command);
        return goals == null ? null : new Invocation(goals, Map.of(), List.of());
    }

    /**
     * The plugins that run an application, by {@code groupId:artifactId}, with the goal that does
     * it, in the order they are preferred when a POM has several: a Gluon project configures the
     * JavaFX plugin for the desktop next to its own.
     */
    private static final Map<String, String> RUN_PLUGINS = runPlugins();

    private static Map<String, String> runPlugins() {
        Map<String, String> plugins = new LinkedHashMap<>();
        plugins.put("org.openjfx:javafx-maven-plugin", "javafx:run");
        plugins.put("com.gluonhq:gluonfx-maven-plugin", "gluonfx:run");
        plugins.put("org.springframework.boot:spring-boot-maven-plugin", "spring-boot:run");
        plugins.put("io.quarkus:quarkus-maven-plugin", "quarkus:dev");
        plugins.put("io.quarkus.platform:quarkus-maven-plugin", "quarkus:dev");
        return plugins;
    }

    /**
     * What <em>Run</em> runs in a project that does not map it, from the plugins the project
     * configures: the goal of the first plugin of {@link #RUN_PLUGINS} it has, which knows how to
     * run that kind of application - {@code javafx:run} for a JavaFX one -, or else the
     * {@linkplain #defaultGoals default} {@code exec:java}, which needs a main class configured.
     *
     * @param plugins the project's plugins, as {@code groupId:artifactId}
     */
    static List<String> runGoals(Collection<String> plugins) {
        for (Map.Entry<String, String> runner : RUN_PLUGINS.entrySet()) {
            if (plugins.contains(runner.getKey())) {
                return List.of(runner.getValue());
            }
        }
        return defaultGoals(BuildCommands.RUN);
    }

    /**
     * The plugins of the Maven project at {@code dir}, as {@code groupId:artifactId}: those of the
     * effective model the project system has - with what a parent POM brings -, or else, without
     * a project system, those the {@code pom.xml} itself declares. Declared and managed plugins
     * both count: either way the project configures the plugin.
     */
    static Set<String> pluginsOf(Path dir) {
        Set<String> plugins = new LinkedHashSet<>();
        try {
            FileObject folder = FileUtil.toFileObject(dir.toFile());
            Project project = folder == null ? null : ProjectManager.getDefault().findProject(folder);
            NbMavenProject maven = project == null ? null : project.getLookup().lookup(NbMavenProject.class);
            if (maven != null) {
                addPlugins(maven.getMavenProject().getModel().getBuild(), plugins);
            }
        } catch (IOException | RuntimeException | LinkageError ex) {
            LOG.log(Level.FINE, "No Maven project for " + dir, ex);
        }
        Path pom = dir.resolve("pom.xml");
        if (plugins.isEmpty() && Files.isRegularFile(pom)) {
            try (InputStream in = Files.newInputStream(pom)) {
                addPlugins(new MavenXpp3Reader().read(in).getBuild(), plugins);
            } catch (IOException | XmlPullParserException | RuntimeException ex) {
                LOG.log(Level.FINE, "Cannot read " + pom, ex);
            }
        }
        return plugins;
    }

    private static void addPlugins(Build build, Set<String> plugins) {
        if (build == null) {
            return;
        }
        for (Plugin plugin : build.getPlugins()) {
            plugins.add(plugin.getKey());
        }
        if (build.getPluginManagement() != null) {
            for (Plugin plugin : build.getPluginManagement().getPlugins()) {
                plugins.add(plugin.getKey());
            }
        }
    }

    /**
     * What {@code actions}, a project's {@code nbactions.xml}, maps {@code action} to for a project
     * of {@code packaging}; {@code null} without the file, without a mapping of the action that
     * applies to the packaging, or with a mapping without goals, which is how a project disables
     * one. A {@code null} packaging is unknown: any mapping of the action applies.
     */
    static Invocation mapped(Path actions, String action, String packaging) {
        if (!Files.isRegularFile(actions)) {
            return null;
        }
        ActionToGoalMapping mappings;
        try (InputStream in = Files.newInputStream(actions)) {
            mappings = new NetbeansBuildActionXpp3Reader().read(in);
        } catch (IOException | XmlPullParserException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot read " + actions + "; using the default goals", ex);
            return null;
        }
        for (NetbeansActionMapping mapping : mappings.getActions()) {
            if (!action.equals(mapping.getActionName()) || !appliesTo(mapping, packaging)) {
                continue;
            }
            List<String> goals = mapping.getGoals();
            if (goals == null || goals.isEmpty()) {
                return null;
            }
            Map<String, String> properties = new LinkedHashMap<>();
            if (mapping.getProperties() != null) {
                mapping.getProperties().forEach((name, value) -> {
                    // "Env.NAME" entries are environment variables of the mvn process, not properties.
                    if (name != null && value != null && !name.startsWith("Env.")) {
                        properties.put(name, value);
                    }
                });
            }
            List<String> profiles = mapping.getActivatedProfiles();
            return new Invocation(goals, properties, profiles == null ? List.of() : profiles);
        }
        return null;
    }

    private static boolean appliesTo(NetbeansActionMapping mapping, String packaging) {
        List<String> packagings = mapping.getPackagings();
        return packaging == null || packagings == null || packagings.isEmpty()
                || packagings.contains("*") || packagings.contains(packaging);
    }

    /** The goals of {@code command} for a project that does not map it; {@code null} for an unsupported command. */
    static List<String> defaultGoals(String command) {
        return switch (command) {
            case BuildCommands.BUILD -> List.of("package");
            case BuildCommands.CLEAN -> List.of("clean");
            case BuildCommands.REBUILD -> List.of("clean", "package");
            case BuildCommands.TEST -> List.of("test");
            case BuildCommands.RUN -> List.of("compile", "exec:java");
            case BuildCommands.JAVADOC -> List.of("javadoc:javadoc");
            default -> null;
        };
    }

    /** The packaging of the Maven project at {@code dir}, or {@code null} when it is not known. */
    private static String packagingOf(Path dir) {
        try {
            FileObject folder = FileUtil.toFileObject(dir.toFile());
            Project project = folder == null ? null : ProjectManager.getDefault().findProject(folder);
            NbMavenProject maven = project == null ? null : project.getLookup().lookup(NbMavenProject.class);
            return maven == null ? null : maven.getPackagingType();
        } catch (IOException | RuntimeException | LinkageError ex) {
            // No project system (a headless use), or not a project it knows: the packaging is unknown.
            LOG.log(Level.FINE, "No Maven project for " + dir, ex);
            return null;
        }
    }
}
