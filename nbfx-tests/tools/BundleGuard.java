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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bundle guard: every literal key a module passes to {@code NbBundle.getMessage(X.class, "key")}
 * must exist in the {@code Bundle.properties} of {@code X}'s package. The failure this catches
 * only shows at run time, as a {@code MissingResourceException} when the message is first needed:
 * typically after a class moved to another module or package and its keys stayed behind.
 * <p>
 * Checked: direct calls with a literal key, and calls through a one-line helper such as
 * {@code private static String message(String key) { return NbBundle.getMessage(X.class, key); }}
 * (with or without trailing {@code Object... args}). Keys declared with
 * {@code @NbBundle.Messages("key=...")} count as defined in that package. Not checked: computed
 * keys ({@code "TabMenu." + key}) and keys passed through other indirections; a bundle look-up
 * whose key is built at run time still needs a test.
 */
public final class BundleGuard {

    /** {@code NbBundle.getMessage(Some.class, "literal"} followed by the end of that argument. */
    private static final Pattern DIRECT = Pattern.compile(
            "NbBundle\\.getMessage\\(\\s*([\\w.]+)\\.class\\s*,\\s*\"([^\"\\\\]*)\"\\s*[,)]");
    /** A one-line helper forwarding its {@code String key} parameter to {@code NbBundle.getMessage}. */
    private static final Pattern HELPER = Pattern.compile(
            "String\\s+(\\w+)\\s*\\(\\s*String\\s+(\\w+)\\s*(?:,\\s*Object\\.\\.\\.\\s*\\w+\\s*)?\\)\\s*\\{\\s*"
            + "return\\s+NbBundle\\.getMessage\\(\\s*([\\w.]+)\\.class\\s*,\\s*\\2\\b");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+)\\.(\\w+)\\s*;", Pattern.MULTILINE);
    private static final Pattern MESSAGES_KEY = Pattern.compile("@(?:NbBundle\\.)?Messages\\s*\\(([^)]*)\\)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\=]+)=");

    private BundleGuard() {
    }

    private static boolean isMainSource(Path path) {
        String text = path.toString();
        return text.endsWith(".java")
                && text.contains("/src/")
                && !text.contains("/test/")
                && !text.contains("/build/");
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Paths.get(System.getProperty("user.dir")) : Paths.get(args[0]);
        List<Path> modules = new ArrayList<>();
        try (Stream<Path> clusters = Files.list(root)) {
            for (Path cluster : (Iterable<Path>) clusters
                    .filter(p -> p.getFileName().toString().startsWith("nbfx-") && Files.isDirectory(p))
                    .sorted()::iterator) {
                try (Stream<Path> dirs = Files.list(cluster)) {
                    dirs.filter(d -> Files.isDirectory(d.resolve("src"))).sorted().forEach(modules::add);
                }
            }
        }
        // module -> package (dotted) -> keys of its Bundle.properties, plus @Messages declarations
        Map<Path, Map<String, Set<String>>> keysByModule = new LinkedHashMap<>();
        // key -> "cluster/module package" of every bundle that defines it, for the hint
        Map<String, Set<String>> definedIn = new HashMap<>();
        for (Path module : modules) {
            keysByModule.put(module, collect(root, module, definedIn));
        }
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (Path module : modules) {
            checked += check(root, module, keysByModule.get(module), definedIn, violations);
        }
        if (violations.isEmpty()) {
            System.out.println("[bundles] OK: " + checked + " literal bundle key reference(s) resolve in "
                    + modules.size() + " module(s)");
            return;
        }
        for (String violation : violations) {
            System.out.println("[bundles] " + violation);
        }
        System.exit(1);
    }

    /** The keys each package of {@code module} defines, in its bundles and {@code @Messages}. */
    private static Map<String, Set<String>> collect(Path root, Path module, Map<String, Set<String>> definedIn)
            throws IOException {
        Path src = module.resolve("src");
        String where = root.relativize(module).toString();
        Map<String, Set<String>> keysByPackage = new HashMap<>();
        try (Stream<Path> files = Files.walk(src)) {
            for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                if (file.getFileName().toString().equals("Bundle.properties")) {
                    String pkg = src.relativize(file.getParent()).toString().replace('/', '.').replace('\\', '.');
                    for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
                        String trimmed = line.trim();
                        int eq = trimmed.indexOf('=');
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 0) {
                            continue;
                        }
                        define(keysByPackage, definedIn, where, pkg, trimmed.substring(0, eq).trim());
                    }
                } else if (isMainSource(file)) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher pkgMatcher = PACKAGE.matcher(text);
                    String pkg = pkgMatcher.find() ? pkgMatcher.group(1) : "";
                    Matcher messages = MESSAGES_KEY.matcher(text);
                    while (messages.find()) {
                        Matcher literal = STRING_LITERAL.matcher(messages.group(1));
                        while (literal.find()) {
                            define(keysByPackage, definedIn, where, pkg, literal.group(1));
                        }
                    }
                }
            }
        }
        return keysByPackage;
    }

    private static void define(Map<String, Set<String>> keysByPackage, Map<String, Set<String>> definedIn,
            String module, String pkg, String key) {
        keysByPackage.computeIfAbsent(pkg, k -> new HashSet<>()).add(key);
        definedIn.computeIfAbsent(key, k -> new TreeSet<>()).add(module + " " + pkg);
    }

    /** Checks one module's sources; returns the number of key references examined. */
    private static int check(Path root, Path module, Map<String, Set<String>> keysByPackage,
            Map<String, Set<String>> definedIn, List<String> violations) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(module.resolve("src"))) {
            files.filter(BundleGuard::isMainSource).sorted().forEach(sources::add);
        }
        int checked = 0;
        for (Path file : sources) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("NbBundle")) {
                continue;
            }
            Matcher pkgMatcher = PACKAGE.matcher(text);
            String filePackage = pkgMatcher.find() ? pkgMatcher.group(1) : "";
            Map<String, String> imports = new HashMap<>();
            Matcher importMatcher = IMPORT.matcher(text);
            while (importMatcher.find()) {
                imports.put(importMatcher.group(2), importMatcher.group(1));
            }
            // references: key -> offset, resolved against the bundle package of the class argument
            Matcher direct = DIRECT.matcher(text);
            while (direct.find()) {
                checked++;
                String pkg = packageOf(direct.group(1), filePackage, imports);
                report(root, file, text, direct.start(), direct.group(2), pkg, keysByPackage, definedIn, violations);
            }
            Matcher helper = HELPER.matcher(text);
            while (helper.find()) {
                String name = helper.group(1);
                String pkg = packageOf(helper.group(3), filePackage, imports);
                Matcher call = Pattern.compile("(?<![\\w.])" + Pattern.quote(name)
                        + "\\(\\s*\"([^\"\\\\]*)\"\\s*[,)]").matcher(text);
                while (call.find()) {
                    checked++;
                    report(root, file, text, call.start(), call.group(1), pkg, keysByPackage, definedIn, violations);
                }
            }
        }
        return checked;
    }

    /** The package whose Bundle.properties {@code NbBundle} consults for {@code classRef}. */
    private static String packageOf(String classRef, String filePackage, Map<String, String> imports) {
        int dot = classRef.lastIndexOf('.');
        if (dot >= 0) {
            String qualifier = classRef.substring(0, dot);
            // Outer.Inner: the outer class decides; a fully qualified name carries its package.
            if (Character.isUpperCase(qualifier.charAt(qualifier.lastIndexOf('.') + 1))) {
                return packageOf(qualifier, filePackage, imports);
            }
            return qualifier;
        }
        String imported = imports.get(classRef);
        return imported != null ? imported : filePackage;
    }

    private static void report(Path root, Path file, String text, int offset, String key, String pkg,
            Map<String, Set<String>> keysByPackage, Map<String, Set<String>> definedIn, List<String> violations) {
        Set<String> keys = keysByPackage.get(pkg);
        if (keys != null && keys.contains(key)) {
            return;
        }
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        String bundle = pkg.replace('.', '/') + "/Bundle.properties";
        Set<String> elsewhere = definedIn.get(key);
        String hint = elsewhere == null ? "" : " (defined in " + String.join(", ", elsewhere) + ")";
        violations.add(root.relativize(file) + ":" + line + ": key \"" + key + "\" is not in " + bundle
                + (keys == null ? " (no such bundle)" : "") + hint);
    }
}
