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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cluster-layer guard.
 *
 * <p>With {@code --layers} it rejects upward nbfx module edges: {@code nbfx-libs} &lt;-
 * {@code nbfx-platform} &lt;- {@code nbfx-ide} &lt;- {@code nbfx-java}/{@code nbfx-javafx}, with the
 * declared {@code java -> javafx} sibling order. With {@code --api} it rejects an API module
 * ({@code nbfx.api.*} or {@code nbfx.java.elements}) that depends on an implementation module.</p>
 *
 * <p>Upstream NetBeans modules are not layer edges and are ignored.</p>
 */
public final class LayerGuard {

    private static final Pattern CODE_NAME = Pattern.compile(
            "<code-name-base>([^<]+)</code-name-base>");
    private static final Pattern MODULE_DEPS = Pattern.compile(
            "<module-dependencies>(.*?)</module-dependencies>", Pattern.DOTALL);

    private static final Map<String, Integer> RANK = Map.of(
            "nbfx-libs", 0,
            "nbfx-platform", 1,
            "nbfx-ide", 2,
            "nbfx-java", 3,
            "nbfx-javafx", 4);

    private LayerGuard() {
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Paths.get(System.getProperty("user.dir")) : Paths.get(args[0]);
        boolean api = args.length > 1 && "--api".equals(args[1]);
        Map<String, Module> modules = modules(root);
        List<String> violations = new ArrayList<>();
        for (Module module : modules.values()) {
            for (String dependency : module.dependencies) {
                Module target = modules.get(dependency);
                if (target == null) {
                    continue; // upstream module: not an nbfx layer edge
                }
                if (api) {
                    if (isApi(module) && !isApi(target) && !"nbfx-libs".equals(target.cluster)) {
                        violations.add(module.path + " -> " + target.path
                                + ": API module depends on an implementation module");
                    }
                } else if (isUpward(module.cluster, target.cluster)) {
                    violations.add(module.path + " -> " + target.path
                            + ": upward cluster edge " + module.cluster + " -> " + target.cluster);
                }
            }
        }
        String prefix = api ? "[api] " : "[layers] ";
        if (violations.isEmpty()) {
            System.out.println(prefix + "OK");
            return;
        }
        for (String violation : violations) {
            System.out.println(prefix + violation);
        }
        System.exit(1);
    }

    private static Map<String, Module> modules(Path root) throws IOException {
        Map<String, Module> modules = new HashMap<>();
        try (DirectoryStream<Path> clusters = Files.newDirectoryStream(root, "nbfx-*")) {
            for (Path cluster : clusters) {
                if (!Files.isDirectory(cluster)) {
                    continue;
                }
                try (DirectoryStream<Path> children = Files.newDirectoryStream(cluster)) {
                    for (Path module : children) {
                        Path project = module.resolve("nbproject/project.xml");
                        if (!Files.isRegularFile(project)) {
                            continue;
                        }
                        String text = Files.readString(project, StandardCharsets.UTF_8);
                        Matcher codeName = CODE_NAME.matcher(text);
                        if (!codeName.find()) {
                            continue;
                        }
                        String name = codeName.group(1);
                        modules.put(name, new Module(module.getFileName().toString(),
                                cluster.getFileName().toString(), root.relativize(module).toString(),
                                dependencies(text)));
                    }
                }
            }
        }
        return modules;
    }

    private static List<String> dependencies(String projectXml) {
        List<String> deps = new ArrayList<>();
        Matcher block = MODULE_DEPS.matcher(projectXml);
        if (!block.find()) {
            return deps;
        }
        Matcher codeName = CODE_NAME.matcher(block.group(1));
        while (codeName.find()) {
            deps.add(codeName.group(1));
        }
        return deps;
    }

    private static boolean isApi(Module module) {
        return module.name.startsWith("nbfx.api.") || module.name.equals("nbfx.java.elements");
    }

    private static boolean isUpward(String source, String target) {
        Integer from = RANK.get(source);
        Integer to = RANK.get(target);
        if (from == null || to == null) {
            return false;
        }
        if ("nbfx-java".equals(source) && "nbfx-javafx".equals(target)) {
            return false; // the declared sibling order
        }
        return to > from;
    }

    private record Module(String name, String cluster, String path, List<String> dependencies) {
    }
}
