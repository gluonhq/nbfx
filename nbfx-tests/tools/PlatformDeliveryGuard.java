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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Platform-delivery guard: the platform-only build must select the platform clusters and produce a
 * platform cluster without nbfx IDE or Java modules.
 */
public final class PlatformDeliveryGuard {

    private static final String CONFIG = "clusters.config.nbfx-platform.list";
    private static final List<String> FORBIDDEN_CLUSTERS = List.of(
            "nb.cluster.nbfx-ide",
            "nb.cluster.nbfx-java");
    private static final List<String> FORBIDDEN_JARS = List.of(
            "org-netbeans-modules-nbfx-ide-",
            "org-netbeans-modules-nbfx-java-");

    private PlatformDeliveryGuard() {
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Paths.get(System.getProperty("user.dir")) : Paths.get(args[0]);
        Path config = root.resolve("build/cluster-platform.properties");
        if (!Files.isRegularFile(config)) {
            System.out.println("[platform] OK (no platform build)");
            return;
        }
        List<String> violations = new ArrayList<>();
        String text = Files.readString(config, StandardCharsets.UTF_8);
        int start = text.indexOf(CONFIG);
        if (start < 0) {
            violations.add(config + ": no " + CONFIG);
        } else {
            String block = text.substring(start);
            for (String cluster : FORBIDDEN_CLUSTERS) {
                if (block.contains(cluster)) {
                    violations.add(config + ": selects " + cluster);
                }
            }
        }
        Path modules = root.resolve("netbeans/nbbuild/netbeans/nbfx-platform/modules");
        if (!Files.isDirectory(modules)) {
            violations.add(modules + ": platform cluster has no modules directory");
        } else {
            try (Stream<Path> jars = Files.list(modules)) {
                for (Path jar : (Iterable<Path>) jars.filter(path -> path.toString().endsWith(".jar"))::iterator) {
                    String name = jar.getFileName().toString();
                    for (String forbidden : FORBIDDEN_JARS) {
                        if (name.startsWith(forbidden)) {
                            violations.add(jar + ": IDE/Java module in the platform cluster");
                        }
                    }
                }
            }
            if (!Files.isRegularFile(modules.resolve("org-netbeans-modules-nbfx-platform-shell.jar"))) {
                violations.add(modules + ": missing the platform shell");
            }
        }
        if (violations.isEmpty()) {
            System.out.println("[platform] OK: platform delivery contains no IDE/Java modules");
            return;
        }
        for (String violation : violations) {
            System.out.println("[platform] " + violation);
        }
        System.exit(1);
    }
}
