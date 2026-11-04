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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lightweight API guard: records the exported packages and public top-level types of the
 * governed modules and fails when they change without updating the snapshot.
 * <p>
 * This is deliberately a type-level snapshot, not a full signature test. It catches package moves,
 * accidental exports and public type additions/removals; method-level compatibility is handled by
 * {@code apichanges.xml} until a sigtest baseline is enabled.
 */
public final class ApiSnapshot {

    private static final Path GOVERNED = Path.of("docs/plan/api-governed.txt");
    private static final Path SNAPSHOT = Path.of("docs/plan/api-snapshot.txt");
    private static final Pattern PACKAGE = Pattern.compile("<package>(.*?)</package>");
    private static final Pattern SUBPACKAGES = Pattern.compile("<subpackages>(.*?)</subpackages>");
    private static final Pattern TYPE = Pattern.compile(
            "(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)?"
            + "(?:class|interface|record|enum|@interface)\\s+(\\w+)");

    public static void main(String[] args) throws IOException {
        boolean update = args.length > 0 && "--update".equals(args[0]);
        String actual = snapshot();
        if (update) {
            Files.writeString(SNAPSHOT, actual);
            System.out.println("wrote " + SNAPSHOT);
            return;
        }
        String expected = Files.exists(SNAPSHOT) ? Files.readString(SNAPSHOT) : "";
        if (!expected.equals(actual)) {
            System.out.println("API snapshot is stale; run: java docs/plan/tools/ApiSnapshot.java --update");
            printDiff(expected, actual);
            System.exit(1);
        }
        System.out.println("api snapshot is up to date");
    }

    private static String snapshot() throws IOException {
        List<String> modules = Files.readAllLines(GOVERNED).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        List<String> lines = new ArrayList<>();
        for (String module : modules) {
            lines.add("# " + module);
            Path project = Path.of(module, "nbproject", "project.xml");
            String xml = Files.readString(project);
            for (String pkg : packages(xml)) {
                Path source = Path.of(module, "src", pkg.replace('.', '/'));
                if (Files.isDirectory(source)) {
                    try (var files = Files.list(source)) {
                        files.filter(path -> path.toString().endsWith(".java"))
                                .sorted()
                                .forEach(path -> types(pkg, path, lines));
                    }
                }
            }
        }
        lines.sort(Comparator.naturalOrder());
        return String.join("\n", lines) + "\n";
    }

    private static List<String> packages(String xml) {
        List<String> packages = new ArrayList<>();
        Matcher single = PACKAGE.matcher(xml);
        while (single.find()) {
            packages.add(single.group(1).trim());
        }
        Matcher sub = SUBPACKAGES.matcher(xml);
        while (sub.find()) {
            packages.add(sub.group(1).trim());
        }
        return packages;
    }

    private static void types(String pkg, Path source, List<String> lines) {
        try {
            String text = Files.readString(source);
            Matcher matcher = TYPE.matcher(text);
            while (matcher.find()) {
                lines.add(pkg + "." + matcher.group(1));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + source, ex);
        }
    }

    private static void printDiff(String expected, String actual) {
        List<String> expectedLines = expected.lines().toList();
        List<String> actualLines = actual.lines().toList();
        expectedLines.stream().filter(line -> !actualLines.contains(line))
                .forEach(line -> System.out.println("- " + line));
        actualLines.stream().filter(line -> !expectedLines.contains(line))
                .forEach(line -> System.out.println("+ " + line));
    }

    private ApiSnapshot() {
    }
}
