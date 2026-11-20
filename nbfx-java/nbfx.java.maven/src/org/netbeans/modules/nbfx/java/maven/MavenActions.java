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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.api.execute.RunConfig;
import org.netbeans.modules.maven.api.execute.RunUtils;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.execute.model.ActionToGoalMapping;
import org.netbeans.modules.maven.execute.model.NetbeansActionMapping;
import org.netbeans.modules.maven.execute.model.io.xpp3.NetbeansBuildActionXpp3Reader;
import org.netbeans.modules.nbfx.ide.build.BuildProcess;
import org.netbeans.modules.nbfx.project.ui.api.BuildCommands;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.BaseUtilities;
import org.openide.util.lookup.Lookups;

/**
 * What runs for a build command of a Maven project: {@code mvn} processes, as NetBeans runs Maven.
 * <p>
 * A project that maps the command in its {@code nbactions.xml} - a JavaFX project maps
 * <em>Run</em> to {@code clean javafx:run} there - gets what NetBeans' own action resolver makes
 * of the mapping: its goals, properties, profiles and options, the active configuration's
 * {@code nbactions-<configuration>.xml}, the folder to run in, the reactor flags, the environment
 * variables, the action to run first. nbfx does not read the mappings' meaning itself; it only
 * looks whether the project has one, so that a project without one gets the nbfx defaults and not
 * NetBeans' - whose <em>Run</em> asks for a main class - and for <em>Run</em> the goal of the
 * plugin the project builds with.
 * <p>
 * Everything here reads the project and its files: it is called on the thread that runs the build,
 * not on the user interface thread.
 */
public final class MavenActions {

    private static final Logger LOG = Logger.getLogger(MavenActions.class.getName());

    /** The file a project keeps its own action mappings in, next to its POM. */
    static final String ACTIONS_FILE = "nbactions.xml";

    /** How a mapping's property names an environment variable of the {@code mvn} process: {@code Env.NAME}. */
    private static final String ENV_PREFIX = "Env.";
    /** Properties NetBeans keeps for itself in a run configuration, not for Maven. */
    private static final String INTERNAL_PREFIX = "NbIde.";

    /**
     * One {@code mvn} invocation, in the terms of NetBeans' run configuration: where, what, and
     * the flags that change how.
     *
     * @param directory       the folder to run in
     * @param properties      the user properties ({@code -Dname=value}); those named
     *                        {@code Env.NAME} are environment variables of the process instead
     * @param options         long command line options by name, with an empty value for a flag
     * @param reactor         {@code also-make} or {@code also-make-dependents}, or {@code null}
     * @param reactorProject  the project the reactor flag is about, relative to {@code directory}
     */
    record Invocation(Path directory, List<String> goals, Map<String, String> properties, List<String> profiles,
            Map<String, String> options, boolean recursive, boolean offline, boolean updateSnapshots,
            boolean debug, boolean errors, String reactor, String reactorProject) {

        Invocation {
            goals = List.copyOf(goals);
            properties = new LinkedHashMap<>(properties);
            profiles = List.copyOf(profiles);
            options = new LinkedHashMap<>(options);
        }

        /** Goals and properties in {@code directory}, with nothing else changed. */
        static Invocation plain(Path directory, List<String> goals, Map<String, String> properties) {
            return new Invocation(directory, goals, properties, List.of(), Map.of(),
                    true, false, false, false, false, null, null);
        }

        /** The arguments of {@code mvn}, as NetBeans orders them; the goals come last. */
        List<String> arguments() {
            List<String> arguments = new ArrayList<>();
            options.forEach((name, value) -> {
                arguments.add("--" + name);
                if (value != null && !value.isBlank()) {
                    arguments.add(value);
                }
            });
            properties.forEach((name, value) -> {
                if (!name.startsWith(ENV_PREFIX) && !name.startsWith(INTERNAL_PREFIX)) {
                    arguments.add("-D" + name + "=" + value);
                }
            });
            if (offline) {
                arguments.add("--offline");
            }
            // Nobody answers a prompt in the Output window, and the transfer progress is noise there.
            arguments.add("--batch-mode");
            if (!recursive) {
                arguments.add("--non-recursive");
            }
            if (debug) {
                arguments.add("--debug");
            }
            if (errors) {
                arguments.add("--errors");
            }
            if (updateSnapshots) {
                arguments.add("--update-snapshots");
            }
            if (reactor != null && reactorProject != null && !".".equals(reactorProject)) {
                arguments.add("--" + reactor);
                arguments.add("--projects");
                arguments.add(reactorProject);
            }
            if (!profiles.isEmpty()) {
                arguments.add("-P" + String.join(",", profiles));
            }
            arguments.addAll(goals);
            return arguments;
        }

        /**
         * The environment of the process: the JDK the IDE runs on as {@code JAVA_HOME}, as NetBeans
         * gives Maven the IDE's default platform, and the mapping's {@code Env.NAME} properties,
         * which may name another one.
         */
        Map<String, String> environment() {
            Map<String, String> environment = new LinkedHashMap<>();
            String javaHome = System.getProperty("java.home");
            if (javaHome != null) {
                environment.put("JAVA_HOME", javaHome);
            }
            properties.forEach((name, value) -> {
                if (name.startsWith(ENV_PREFIX) && name.length() > ENV_PREFIX.length() && value != null) {
                    environment.put(name.substring(ENV_PREFIX.length()), value);
                }
            });
            return environment;
        }

        BuildProcess process(String executable) {
            List<String> commandLine = new ArrayList<>();
            commandLine.add(executable);
            commandLine.addAll(arguments());
            return new BuildProcess(commandLine, directory, environment());
        }
    }

    private MavenActions() {
    }

    /**
     * The processes that run {@code command} in the project at {@code dir}, in order: {@code null}
     * for a command Maven projects do not support, an empty list when the project has the command
     * disabled - a mapping without goals, as NetBeans - or has nothing to run for it.
     */
    public static List<BuildProcess> forCommand(Path dir, String command) {
        if (defaultGoals(command) == null) {
            return null;
        }
        String executable = executable(dir);
        List<List<String>> mapped = mappedGoals(dir, command);
        if (!mapped.isEmpty()) {
            RunConfig config = resolve(dir, command);
            if (config != null) {
                List<String> goals = config.getGoals() == null ? List.of() : config.getGoals();
                if (goals.isEmpty()) {
                    return List.of();
                }
                if (mapped.contains(goals)) {
                    return processes(dir, config, executable);
                }
                // NetBeans answered with a mapping of its own: the project's does not apply here.
            } else if (mapped.stream().anyMatch(List::isEmpty)) {
                return List.of();
            }
        }
        List<String> goals = defaultGoals(command);
        if (BuildCommands.RUN.equals(command)) {
            if ("pom".equals(packagingOf(dir))) {
                // An aggregator or a parent builds nothing to run.
                return List.of();
            }
            goals = runGoals(pluginsOf(dir));
        }
        return List.of(Invocation.plain(dir, goals, Map.of()).process(executable));
    }

    /** The process that runs {@code goals} in the project at {@code dir}, with {@code properties} as user properties. */
    public static BuildProcess forGoals(Path dir, List<String> goals, Map<String, String> properties) {
        return Invocation.plain(dir, goals, properties).process(executable(dir));
    }

    /** The processes of {@code config}: the action it asks to run first, then itself. */
    private static List<BuildProcess> processes(Path dir, RunConfig config, String executable) {
        List<BuildProcess> processes = new ArrayList<>();
        RunConfig first = config.getPreExecution();
        if (first != null && first.getGoals() != null && !first.getGoals().isEmpty()) {
            processes.add(invocation(dir, first).process(executable));
        }
        processes.add(invocation(dir, config).process(executable));
        return processes;
    }

    /** {@code config}, NetBeans' run configuration, as an invocation; it runs in {@code dir} unless it names a folder. */
    static Invocation invocation(Path dir, RunConfig config) {
        File folder = config.getExecutionDirectory();
        Path directory = folder == null ? dir : folder.toPath();
        Map<String, String> properties = new LinkedHashMap<>();
        if (config.getProperties() != null) {
            config.getProperties().forEach((name, value) -> {
                if (name != null && value != null) {
                    properties.put(name, value);
                }
            });
        }
        Map<String, String> options = new LinkedHashMap<>();
        if (config.getOptions() != null) {
            config.getOptions().forEach((name, value) -> {
                if (name != null) {
                    options.put(name, value == null ? "" : value);
                }
            });
        }
        String reactor = switch (config.getReactorStyle() == null ? RunConfig.ReactorStyle.NONE : config.getReactorStyle()) {
            case ALSO_MAKE -> "also-make";
            case ALSO_MAKE_DEPENDENTS -> "also-make-dependents";
            default -> null;
        };
        String reactorProject = null;
        if (reactor != null) {
            try {
                reactorProject = directory.toAbsolutePath().normalize()
                        .relativize(dir.toAbsolutePath().normalize()).toString();
                if (reactorProject.isEmpty()) {
                    reactorProject = ".";
                }
            } catch (IllegalArgumentException ex) {
                reactorProject = null;
            }
        }
        List<String> profiles = config.getActivatedProfiles() == null ? List.of() : config.getActivatedProfiles();
        return new Invocation(directory, config.getGoals(), properties, profiles, options,
                config.isRecursive(), Boolean.TRUE.equals(config.isOffline()), config.isUpdateSnapshots(),
                config.isShowDebug(), config.isShowError(), reactor, reactorProject);
    }

    /**
     * What NetBeans makes of {@code action} for the project at {@code dir}: its run configuration,
     * from the project's mappings in its active configuration; {@code null} without a NetBeans
     * Maven project - a headless use has no project system - or when the action is unavailable.
     */
    private static RunConfig resolve(Path dir, String action) {
        try {
            Project project = projectOf(dir);
            // The map is where NetBeans looks for the tokens to replace in a mapping; there are none here.
            return project == null ? null
                    : RunUtils.createRunConfig(action, project, null, Lookups.fixed(new HashMap<String, String>()));
        } catch (IOException | RuntimeException | LinkageError ex) {
            LOG.log(Level.FINE, "No run configuration for " + action + " in " + dir, ex);
            return null;
        }
    }

    /**
     * The goals of each mapping of {@code action} in the project's own action files,
     * {@code nbactions.xml} and the {@code nbactions-<configuration>.xml} of its configurations:
     * empty when the project does not map the action. Only whether the project maps it, and to
     * which goals, is read here; what a mapping means is NetBeans' to say.
     */
    static List<List<String>> mappedGoals(Path dir, String action) {
        List<List<String>> goals = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "nbactions*.xml")) {
            for (Path file : files) {
                ActionToGoalMapping mappings;
                try (InputStream in = Files.newInputStream(file)) {
                    mappings = new NetbeansBuildActionXpp3Reader().read(in);
                } catch (IOException | XmlPullParserException | RuntimeException ex) {
                    LOG.warning("Cannot read " + file + ": " + ex);
                    continue;
                }
                for (NetbeansActionMapping mapping : mappings.getActions()) {
                    if (action.equals(mapping.getActionName())) {
                        goals.add(mapping.getGoals() == null ? List.of() : List.copyOf(mapping.getGoals()));
                    }
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Cannot list " + dir, ex);
        }
        return goals;
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
     * builds with: the goal of the first plugin of {@link #RUN_PLUGINS} it has, which knows how to
     * run that kind of application - {@code javafx:run} for a JavaFX one -, or else the
     * {@linkplain #defaultGoals default} {@code exec:java}, which needs a main class configured.
     *
     * @param plugins the project's build plugins, as {@code groupId:artifactId}
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
     * The plugins the Maven project at {@code dir} builds with, as {@code groupId:artifactId}:
     * those of the effective model the project system has - with what a parent POM brings -, or
     * else, without a project system, those the {@code pom.xml} itself declares. A plugin that is
     * only managed - configured for the modules of an aggregator to use - does not count: the
     * project does not run it.
     */
    static Set<String> pluginsOf(Path dir) {
        Set<String> plugins = new LinkedHashSet<>();
        Model model = modelOf(dir);
        Build build = model == null ? null : model.getBuild();
        if (build != null) {
            for (Plugin plugin : build.getPlugins()) {
                plugins.add(plugin.getKey());
            }
        }
        return plugins;
    }

    /** The packaging of the Maven project at {@code dir}, or {@code null} when it is not known. */
    static String packagingOf(Path dir) {
        Model model = modelOf(dir);
        return model == null ? null : model.getPackaging();
    }

    /** The effective model of the project at {@code dir}, or else its {@code pom.xml} as written; {@code null} without either. */
    private static Model modelOf(Path dir) {
        try {
            Project project = projectOf(dir);
            NbMavenProject maven = project == null ? null : project.getLookup().lookup(NbMavenProject.class);
            if (maven != null && maven.getMavenProject().getModel() != null) {
                return maven.getMavenProject().getModel();
            }
        } catch (IOException | RuntimeException | LinkageError ex) {
            // No project system (a headless use), or not a project it knows: the POM as written.
            LOG.log(Level.FINE, "No Maven project for " + dir, ex);
        }
        Path pom = dir.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            try (InputStream in = Files.newInputStream(pom)) {
                return new MavenXpp3Reader().read(in);
            } catch (IOException | XmlPullParserException | RuntimeException ex) {
                LOG.log(Level.FINE, "Cannot read " + pom, ex);
            }
        }
        return null;
    }

    private static Project projectOf(Path dir) throws IOException {
        FileObject folder = FileUtil.toFileObject(dir.toFile());
        return folder == null ? null : ProjectManager.getDefault().findProject(folder);
    }

    /**
     * The {@code mvn} to run for the project at {@code dir}: its wrapper, or else the Maven the
     * IDE is set to use - the one it bundles, by default -, or else whatever {@code mvn} the
     * system finds.
     */
    static String executable(Path dir) {
        boolean windows = BaseUtilities.isWindows();
        Path wrapper = dir.resolve(windows ? "mvnw.cmd" : "mvnw");
        if (Files.isRegularFile(wrapper)) {
            return wrapper.toAbsolutePath().toString();
        }
        try {
            File mvn = new File(EmbedderFactory.getEffectiveMavenHome(), windows ? "bin/mvn.cmd" : "bin/mvn");
            if (mvn.isFile()) {
                return mvn.getAbsolutePath();
            }
        } catch (RuntimeException | LinkageError ex) {
            LOG.log(Level.FINE, "No Maven home", ex);
        }
        return "mvn";
    }
}
