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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fails after all module tests have run when a module produced no JUnit XML, or when any
 * testsuite reports failures or errors. {@code ant test} cannot use subant's failonerror
 * directly: that stops after the first module and hides the results of the rest.
 */
public final class TestResults {

    private static final Pattern SUITE = Pattern.compile("<testsuite\\b[^>]*>");
    private static final Pattern ATTR = Pattern.compile("\\b(tests|errors|failures)=\"(\\d+)\"");

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: TestResults <module-list-file>");
            System.exit(2);
        }
        List<Path> modules = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                modules.add(Path.of(line.trim()));
            }
        }

        int expected = 0;
        int actual = 0;
        int tests = 0;
        int failures = 0;
        int errors = 0;
        List<String> problems = new ArrayList<>();
        for (Path module : modules) {
            Path testSrc = module.resolve("test/unit/src");
            if (!Files.isDirectory(testSrc)) {
                continue;
            }
            boolean hasTests;
            try (var files = Files.walk(testSrc)) {
                hasTests = files.anyMatch(p -> p.getFileName().toString().endsWith("Test.java"));
            }
            if (!hasTests) {
                continue;
            }
            expected++;
            Path results = module.resolve("build/test/unit/results");
            List<Path> xml;
            try (var files = Files.list(results)) {
                xml = files.filter(p -> p.getFileName().toString().startsWith("TEST-")
                        && p.getFileName().toString().endsWith(".xml")).toList();
            } catch (IOException ex) {
                problems.add(module.getFileName() + ": no test results (" + ex.getMessage() + ")");
                continue;
            }
            if (xml.isEmpty()) {
                problems.add(module.getFileName() + ": no JUnit XML (test build or run failed)");
                continue;
            }
            actual++;
            for (Path file : xml) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher suite = SUITE.matcher(text);
                if (!suite.find()) {
                    problems.add(module.getFileName() + ": malformed " + file.getFileName());
                    continue;
                }
                Matcher attr = ATTR.matcher(suite.group());
                while (attr.find()) {
                    int value = Integer.parseInt(attr.group(2));
                    switch (attr.group(1)) {
                        case "tests" -> tests += value;
                        case "failures" -> failures += value;
                        case "errors" -> errors += value;
                        default -> {
                        }
                    }
                }
                if (text.contains("<failure") || text.contains("<error")) {
                    // Attributes are authoritative; this is only a defensive second signal.
                    if (failures == 0 && errors == 0) {
                        problems.add(module.getFileName() + ": failed test in " + file.getFileName());
                    }
                }
            }
        }

        System.out.println("[tests] modules " + actual + "/" + expected + ", tests " + tests
                + ", failures " + failures + ", errors " + errors);
        if (failures != 0 || errors != 0) {
            problems.add("test failures=" + failures + ", errors=" + errors);
        }
        if (actual != expected) {
            problems.add("missing test results for " + (expected - actual) + " module(s)");
        }
        if (problems.isEmpty()) {
            return;
        }
        for (String problem : problems) {
            System.out.println("[tests] " + problem);
        }
        System.exit(1);
    }
}
