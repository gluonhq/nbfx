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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Namespace guard: module identities, directory names and package roots.
 *
 * <p>PLAN §3.1: module code names are {@code org.netbeans.modules.nbfx.*} (libraries
 * {@code org.netbeans.libs.nbfx.*}), directories are {@code nbfx.*} (libraries
 * {@code libs.nbfx.*}), API packages are {@code org.netbeans.api.nbfx.*}, SPI packages are
 * {@code org.netbeans.spi.nbfx.*}, and implementation packages are
 * {@code org.netbeans.modules.nbfx.*}.</p>
 */
public final class NamespaceGuard {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+);");
    private static final Pattern CODE_NAME = Pattern.compile(
            "<code-name-base>(org\\.netbeans\\.(?:modules|libs)\\.nbfx\\.[^<]+)</code-name-base>");
    private static final Pattern PUBLIC_PACKAGES = Pattern.compile(
            "<public-packages>(.*?)</public-packages>", Pattern.DOTALL);
    private static final Pattern PACKAGE_ELEMENT = Pattern.compile(
            "<(?:package|subpackages)>([^<]+)</(?:package|subpackages)>");

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Paths.get(System.getProperty("user.dir")) : Paths.get(args[0]);
        List<String> violations = new ArrayList<>();
        try (DirectoryStream<Path> clusters = Files.newDirectoryStream(root, "nbfx-*")) {
            for (Path cluster : clusters) {
                if (!Files.isDirectory(cluster)) {
                    continue;
                }
                try (DirectoryStream<Path> modules = Files.newDirectoryStream(cluster)) {
                    for (Path module : modules) {
                        if (!Files.isDirectory(module)) {
                            continue;
                        }
                        Path project = module.resolve("nbproject/project.xml");
                        Path src = module.resolve("src");
                        if (!Files.exists(project) || !Files.isDirectory(src)) {
                            continue;
                        }
                        checkModule(root, cluster, module, project, src, violations);
                    }
                }
            }
        }
        if (violations.isEmpty()) {
            System.out.println("[namespace] OK: org.netbeans nbfx namespace");
            return;
        }
        for (String violation : violations) {
            System.out.println("[namespace] " + violation);
        }
        System.exit(1);
    }

    private static void checkModule(Path root, Path cluster, Path module, Path project, Path src,
            List<String> violations) throws IOException {
        String moduleName = module.getFileName().toString();
        boolean library = cluster.getFileName().toString().equals("nbfx-libs");
        String expectedDirPrefix = library ? "libs.nbfx." : "nbfx.";
        if (!moduleName.startsWith(expectedDirPrefix)) {
            violations.add(moduleName + ": module directory must start with " + expectedDirPrefix);
        }

        String xml = Files.readString(project, StandardCharsets.UTF_8);
        Matcher codeName = CODE_NAME.matcher(xml);
        if (!codeName.find()) {
            violations.add(moduleName + ": code name is not under org.netbeans.modules.nbfx.* or "
                    + "org.netbeans.libs.nbfx.*");
        }

        Matcher publicPackages = PUBLIC_PACKAGES.matcher(xml);
        if (publicPackages.find() && cluster.getFileName().toString().equals("nbfx-api")) {
            Matcher pkg = PACKAGE_ELEMENT.matcher(publicPackages.group(1));
            while (pkg.find()) {
                String name = pkg.group(1).trim();
                if (!name.startsWith("org.netbeans.api.nbfx.")
                        && !name.startsWith("org.netbeans.spi.nbfx.")) {
                    violations.add(moduleName + ": exported package is not API/SPI: " + name);
                }
            }
        }

        try (Stream<Path> files = Files.walk(src)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher pkg = PACKAGE.matcher(text);
                if (!pkg.find()) {
                    continue;
                }
                String name = pkg.group(1);
                if (!name.startsWith("org.netbeans.api.nbfx.")
                        && !name.startsWith("org.netbeans.spi.nbfx.")
                        && !name.startsWith("org.netbeans.modules.nbfx.")
                        && !name.startsWith("org.netbeans.libs.nbfx.")) {
                    violations.add(moduleName + ": package outside the org.netbeans.nbfx namespace: "
                            + name + " (" + root.relativize(file) + ")");
                }
            }
        }
    }
}
