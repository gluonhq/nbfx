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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Read-only API-reality audit for the nbfx tree.
 *
 * <p>This is the M0 W2a baseline, not a gate. It reports:</p>
 * <ul>
 *   <li>each module's tier, public/friend packages, spec version, arch/apichanges/signature presence;</li>
 *   <li>the type each registration annotation requires and whether that type is in an exported
 *       API/SPI package (the contract-completeness gap);</li>
 *   <li>consumers of implementation-root packages, and friend packages used outside their declared
 *       friends;</li>
 *   <li>modules that are annotation-only, have no tier metadata, or export a subtree.</li>
 * </ul>
 *
 * <p>Run with {@code java docs/plan/tools/ApiAudit.java [--out build/api-audit.txt]}. It always exits
 * 0 for findings; only I/O or argument errors fail.</p>
 *
 * <p>The contract map below is the audit's input, not a runtime rule: update it when a registration
 * annotation gains or changes its implementable type. {@code ActionRegistration} is deliberately not
 * mapped because it has no fixed interface (the annotated class is an action body, for example a
 * {@code Runnable}); {@code ThemeTokenRegistration} is resource-only, and the menu/toolbar/area/pane/
 * layout annotations are metadata-only. Consumer edges are import-based; they do not see fully
 * qualified references or module-dependency metadata.</p>
 */
public final class ApiAudit {

    private static final Pattern CODE_NAME = Pattern.compile(
            "<code-name-base>(org\\.netbeans\\.(?:modules|libs)\\.nbfx\\.[^<]+)</code-name-base>");
    private static final Pattern PUBLIC_BLOCK = Pattern.compile(
            "<public-packages>(.*?)</public-packages>", Pattern.DOTALL);
    private static final Pattern FRIEND_BLOCK = Pattern.compile(
            "<friend-packages>(.*?)</friend-packages>", Pattern.DOTALL);
    private static final Pattern PACKAGE = Pattern.compile("<package>(.*?)</package>");
    private static final Pattern SUBPACKAGES = Pattern.compile("<subpackages>(.*?)</subpackages>");
    private static final Pattern FRIEND = Pattern.compile("<friend>(.*?)</friend>");
    private static final Pattern SPEC = Pattern.compile(
            "(?m)^OpenIDE-Module-Specification-Version:\\s*(\\S+)");
    private static final Pattern TIER = Pattern.compile("(?m)^\\s*tier\\s*=\\s*(\\S+)");
    private static final Pattern PACKAGE_DECL = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+);");
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "(?m)^public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)?"
            + "(?:class|interface|record|enum|@interface)\\s+(\\w+)");
    private static final Pattern PUBLIC_ANNOTATION = Pattern.compile(
            "(?m)^public\\s+@interface\\s+(\\w+)");

    private static final List<ContractSpec> CONTRACTS = List.of(
            new ContractSpec("ViewRegistration", "org.netbeans.api.nbfx.view", "ViewProvider"),
            new ContractSpec("DynamicMenuRegistration", "org.netbeans.api.nbfx.views", "DynamicMenu.Factory"),
            new ContractSpec("OptionsRegistration", "org.netbeans.api.nbfx.options", "OptionsPanelController"),
            new ContractSpec("WizardRegistration", "org.netbeans.modules.nbfx.platform.wizard", "Wizard"),
            new ContractSpec("StatusRegistration", "org.netbeans.modules.nbfx.platform.status", "StatusElementHolder"),
            new ContractSpec("PropertyEditorRegistration", "org.netbeans.modules.nbfx.platform.nodes", "PropertyEditor"),
            new ContractSpec("ProjectTypeRegistration", "org.netbeans.modules.nbfx.project.ui.api", "ProjectKindProvider")
    );

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        Path out = null;
        for (int i = 0; i < args.length; i++) {
            if ("--out".equals(args[i]) && i + 1 < args.length) {
                out = Paths.get(args[++i]).toAbsolutePath().normalize();
            } else if ("--root".equals(args[i]) && i + 1 < args.length) {
                root = Paths.get(args[++i]).toAbsolutePath().normalize();
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                System.out.println("usage: java ApiAudit.java [--root <dir>] [--out <file>]");
                return;
            } else {
                throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }

        List<Module> modules = discover(root);
        Map<String, Module> packageOwners = packageOwners(modules);
        List<Contract> contracts = contracts(modules);
        List<Edge> edges = edges(modules, packageOwners);

        String report = render(root, modules, contracts, edges);
        if (out != null) {
            Path parent = out.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, report, StandardCharsets.UTF_8);
            System.out.println("[api-audit] wrote " + out);
        } else {
            System.out.print(report);
        }
    }

    // ---------------------------------------------------------------- model

    private record Module(String directory, String name, String codeName, String tier, String spec,
            List<String> publicPackages, List<String> publicSubpackages,
            List<String> friendPackages, List<String> friends,
            boolean arch, boolean apichanges, List<String> signatures,
            int sources, int publicTypes, int annotationTypes, int processors,
            List<Path> files) {

        boolean isApiByName() {
            return name.startsWith("nbfx.api.");
        }

        boolean isApiTier() {
            return "T1".equals(tier) || "T2-FX".equals(tier) || "M".equals(tier);
        }

        boolean isApi() {
            return isApiByName() || isApiTier();
        }

        boolean exports(String pkg) {
            return publicPackages.contains(pkg) || publicSubpackages.stream()
                    .anyMatch(sub -> pkg.equals(sub) || pkg.startsWith(sub + "."));
        }

        boolean friendExports(String pkg) {
            return friendPackages.contains(pkg) || friendPackages.stream()
                    .anyMatch(friend -> pkg.equals(friend) || pkg.startsWith(friend + "."));
        }
    }

    private record ContractSpec(String annotation, String pkg, String type) {
    }

    private record Contract(String annotation, String expectedType, String owner, String ownerTier,
            String exportKind, boolean processorChecked, String verdict) {
    }

    private record Export(String pkg, Module owner, boolean isPublic) {
    }

    private record Edge(String pkg, String owner, String ownerTier, boolean publicExport,
            String consumer, boolean friend, boolean violation) {
    }

    private static final class Findings {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
    }

    // ------------------------------------------------------------- discover

    private static List<Module> discover(Path root) throws IOException {
        List<Module> modules = new ArrayList<>();
        try (DirectoryStream<Path> clusters = Files.newDirectoryStream(root, "nbfx-*")) {
            for (Path cluster : clusters) {
                if (!Files.isDirectory(cluster)) {
                    continue;
                }
                try (DirectoryStream<Path> dirs = Files.newDirectoryStream(cluster)) {
                    for (Path dir : dirs) {
                        Path project = dir.resolve("nbproject/project.xml");
                        Path src = dir.resolve("src");
                        if (!Files.isDirectory(src) || !Files.exists(project)) {
                            continue;
                        }
                        modules.add(readModule(root, cluster, dir, project, src));
                    }
                }
            }
        }
        modules.sort(Comparator.comparing(Module::codeName));
        return modules;
    }

    private static Module readModule(Path root, Path cluster, Path dir, Path project, Path src)
            throws IOException {
        String xml = Files.readString(project, StandardCharsets.UTF_8);
        String name = dir.getFileName().toString();
        String codeName = group(CODE_NAME, xml, name);
        String tier = readTier(dir);
        String spec = readSpec(dir);

        List<String> publicPackages = new ArrayList<>();
        List<String> publicSubpackages = new ArrayList<>();
        String publicBlock = group(PUBLIC_BLOCK, xml);
        collect(publicBlock, PACKAGE, publicPackages);
        collect(publicBlock, SUBPACKAGES, publicSubpackages);

        List<String> friendPackages = new ArrayList<>();
        List<String> friends = new ArrayList<>();
        String friendBlock = group(FRIEND_BLOCK, xml);
        collect(friendBlock, PACKAGE, friendPackages);
        collect(friendBlock, FRIEND, friends);

        List<String> signatures = new ArrayList<>();
        Path nbproject = dir.resolve("nbproject");
        if (Files.isDirectory(nbproject)) {
            try (Stream<Path> files = Files.list(nbproject)) {
                files.filter(path -> path.getFileName().toString().endsWith(".sig"))
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .forEach(signatures::add);
            }
        }

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(src)) {
            walk.filter(path -> path.toString().endsWith(".java")).sorted().forEach(files::add);
        }

        int publicTypes = 0;
        int annotationTypes = 0;
        int processors = 0;
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            publicTypes += count(PUBLIC_TYPE, text);
            annotationTypes += count(PUBLIC_ANNOTATION, text);
            if (text.contains("extends LayerGeneratingProcessor")
                    || text.contains("implements Processor")
                    || text.contains("@ServiceProvider(service = Processor.class)")) {
                processors++;
            }
        }

        return new Module(root.relativize(dir).toString(), name, codeName, tier, spec,
                List.copyOf(publicPackages), List.copyOf(publicSubpackages),
                List.copyOf(friendPackages), List.copyOf(friends),
                Files.exists(dir.resolve("arch.xml")), Files.exists(dir.resolve("apichanges.xml")),
                List.copyOf(signatures), files.size(), publicTypes, annotationTypes, processors,
                List.copyOf(files));
    }

    private static String readTier(Path dir) throws IOException {
        Path policy = dir.resolve("nbproject/nbfx-policy.properties");
        if (!Files.exists(policy)) {
            return "unset";
        }
        String text = Files.readString(policy, StandardCharsets.UTF_8);
        String tier = group(TIER, text);
        return tier.isBlank() ? "unset" : tier;
    }

    private static String readSpec(Path dir) throws IOException {
        Path manifest = dir.resolve("manifest.mf");
        if (!Files.exists(manifest)) {
            return "-";
        }
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        String spec = group(SPEC, text);
        return spec.isBlank() ? "-" : spec;
    }

    private static Map<String, Module> packageOwners(List<Module> modules) {
        Map<String, Module> owners = new TreeMap<>();
        for (Module module : modules) {
            for (String pkg : module.publicPackages()) {
                owners.putIfAbsent(pkg, module);
            }
            for (String pkg : module.publicSubpackages()) {
                owners.putIfAbsent(pkg, module);
            }
            for (String pkg : module.friendPackages()) {
                owners.putIfAbsent(pkg, module);
            }
        }
        return owners;
    }

    // -------------------------------------------------------------- contracts

    private static List<Contract> contracts(List<Module> modules) {
        List<Contract> contracts = new ArrayList<>();
        for (ContractSpec spec : CONTRACTS) {
            Module owner = typeOwner(modules, spec.pkg(), spec.type());
            String ownerName = owner == null ? "-" : owner.name();
            String ownerTier = owner == null ? "-" : owner.tier();
            String exportKind = owner == null ? "missing"
                    : owner.exports(spec.pkg()) ? "public"
                    : owner.friendExports(spec.pkg()) ? "friend"
                    : "not-exported";
            boolean checked = processorChecksType(modules, spec);
            String verdict;
            if (owner == null) {
                verdict = "GAP (type not found)";
            } else if ("public".equals(exportKind) && (owner.isApi())) {
                verdict = "OK";
            } else if ("public".equals(exportKind)) {
                verdict = "GAP (T0/unset export)";
            } else if ("friend".equals(exportKind)) {
                verdict = "GAP (friend-only)";
            } else {
                verdict = "GAP (not exported)";
            }
            contracts.add(new Contract(spec.annotation(), spec.pkg() + "." + spec.type(), ownerName,
                    ownerTier, exportKind, checked, verdict));
        }
        return contracts;
    }

    private static Module typeOwner(List<Module> modules, String pkg, String type) {
        String outer = type.contains(".") ? type.substring(0, type.indexOf('.')) : type;
        for (Module module : modules) {
            for (Path file : module.files()) {
                if (!file.getFileName().toString().equals(outer + ".java")) {
                    continue;
                }
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String declared = group(PACKAGE_DECL, text);
                    if (pkg.equals(declared)) {
                        return module;
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Cannot read " + file, ex);
                }
            }
        }
        return null;
    }

    private static boolean processorChecksType(List<Module> modules, ContractSpec spec) {
        for (Module module : modules) {
            for (Path file : module.files()) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith("RegistrationProcessor.java")
                        || !fileName.contains(spec.annotation())) {
                    continue;
                }
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String simple = spec.type().contains(".")
                            ? spec.type().substring(spec.type().indexOf('.') + 1) : spec.type();
                    boolean explicit = text.contains(spec.type() + ".class")
                            || text.contains(simple + ".class");
                    boolean assignable = text.contains("isAssignable") && text.contains("getTypeElement");
                    boolean unchecked = text.contains("(Class<?>) null");
                    return assignable || (explicit && !unchecked);
                } catch (IOException ex) {
                    throw new IllegalStateException("Cannot read " + file, ex);
                }
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- edges

    private static List<Edge> edges(List<Module> modules, Map<String, Module> packageOwners) {
        List<Map.Entry<String, Module>> packages = new ArrayList<>(packageOwners.entrySet());
        packages.sort(Comparator.comparingInt((Map.Entry<String, Module> entry) -> entry.getKey().length())
                .reversed());

        Set<Edge> unique = new LinkedHashSet<>();
        for (Module consumer : modules) {
            for (Path file : consumer.files()) {
                String text;
                try {
                    text = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    throw new IllegalStateException("Cannot read " + file, ex);
                }
                Matcher imports = IMPORT.matcher(text);
                while (imports.find()) {
                    String imported = imports.group(1);
                    Module owner = null;
                    String pkg = null;
                    for (Map.Entry<String, Module> entry : packages) {
                        String candidate = entry.getKey();
                        if (imported.equals(candidate) || imported.startsWith(candidate + ".")) {
                            owner = entry.getValue();
                            pkg = candidate;
                            break;
                        }
                    }
                    if (owner == null || owner == consumer) {
                        continue;
                    }
                    boolean publicExport = owner.exports(pkg);
                    boolean friendExport = owner.friendExports(pkg);
                    if (!publicExport && !friendExport) {
                        continue;
                    }
                    boolean friend = owner.friends().contains(consumer.codeName());
                    boolean implRoot = pkg.startsWith("org.netbeans.modules.nbfx.");
                    boolean violation = (friendExport && !publicExport && !friend)
                            || ("T0".equals(owner.tier()) && publicExport);
                    if (implRoot || violation) {
                        unique.add(new Edge(pkg, owner.name(), owner.tier(), publicExport,
                                consumer.name(), friend, violation));
                    }
                }
            }
        }
        List<Edge> result = new ArrayList<>(unique);
        result.sort(Comparator.comparing(Edge::owner).thenComparing(Edge::pkg)
                .thenComparing(Edge::consumer));
        return result;
    }

    // ---------------------------------------------------------------- render

    private static String render(Path root, List<Module> modules, List<Contract> contracts,
            List<Edge> edges) {
        StringBuilder out = new StringBuilder();
        Findings findings = findings(modules, contracts, edges);

        out.append("nbfx API audit\n");
        out.append("read-only: findings do not fail the build; W2a uses this report\n");
        out.append("root: ").append(root).append('\n');
        out.append("modules: ").append(modules.size()).append('\n');
        out.append("contracts: ").append(contracts.size()).append('\n');
        out.append("edges: ").append(edges.size()).append("\n\n");

        out.append("== Modules ==\n");
        out.append("module\ttier\tspec\tarch\tchanges\tsig\tpublic\tfriend\tsrc\ttypes\tann\tproc\n");
        for (Module module : modules) {
            out.append(module.name()).append('\t')
                    .append(module.tier()).append('\t')
                    .append(module.spec()).append('\t')
                    .append(module.arch() ? "yes" : "-").append('\t')
                    .append(module.apichanges() ? "yes" : "-").append('\t')
                    .append(module.signatures().isEmpty() ? "-" : "yes").append('\t')
                    .append(module.publicPackages().size() + module.publicSubpackages().size()).append('\t')
                    .append(module.friendPackages().size()).append('\t')
                    .append(module.sources()).append('\t')
                    .append(module.publicTypes()).append('\t')
                    .append(module.annotationTypes()).append('\t')
                    .append(module.processors()).append('\n');
        }

        out.append("\n== Package exports ==\n");
        for (Module module : modules) {
            for (String pkg : module.publicPackages()) {
                out.append(module.name()).append(": ").append(pkg).append(" (public)\n");
            }
            for (String pkg : module.publicSubpackages()) {
                out.append(module.name()).append(": ").append(pkg).append(" (subpackages)\n");
            }
            for (String pkg : module.friendPackages()) {
                out.append(module.name()).append(": ").append(pkg).append(" (friend: ")
                        .append(String.join(",", module.friends())).append(")\n");
            }
        }

        out.append("\n== Registration contracts ==\n");
        out.append("# ActionRegistration has no fixed interface; ThemeTokenRegistration is resource-only;\n");
        out.append("# Menu/Toolbar/Area/Pane/WindowLayout registrations are metadata-only.\n");
        out.append("annotation\texpected type\towner\ttier\texport\tprocessor\tverdict\n");
        for (Contract contract : contracts) {
            out.append(contract.annotation()).append('\t')
                    .append(contract.expectedType()).append('\t')
                    .append(contract.owner()).append('\t')
                    .append(contract.ownerTier()).append('\t')
                    .append(contract.exportKind()).append('\t')
                    .append(contract.processorChecked() ? "checked" : "unchecked").append('\t')
                    .append(contract.verdict()).append('\n');
        }

        out.append("\n== Implementation-package consumers and friend violations ==\n");
        if (edges.isEmpty()) {
            out.append("(none)\n");
        } else {
            out.append("package\towner\ttier\tconsumer\tkind\tverdict\n");
            for (Edge edge : edges) {
                out.append(edge.pkg()).append('\t')
                        .append(edge.owner()).append('\t')
                        .append(edge.ownerTier()).append('\t')
                        .append(edge.consumer()).append('\t')
                        .append(edge.publicExport() ? "public" : "friend").append('\t')
                        .append(edge.violation() ? "VIOLATION" : "note").append('\n');
            }
        }

        out.append("\n== Findings ==\n");
        for (String error : findings.errors) {
            out.append("GAP  ").append(error).append('\n');
        }
        for (String warning : findings.warnings) {
            out.append("WARN ").append(warning).append('\n');
        }
        for (String note : findings.notes) {
            out.append("NOTE ").append(note).append('\n');
        }
        out.append("\nSummary: ").append(findings.errors.size()).append(" gap(s), ")
                .append(findings.warnings.size()).append(" warning(s), ")
                .append(findings.notes.size()).append(" note(s)\n");
        return out.toString();
    }

    private static Findings findings(List<Module> modules, List<Contract> contracts, List<Edge> edges) {
        Findings findings = new Findings();
        List<String> noTier = new ArrayList<>();
        List<String> annotationOnly = new ArrayList<>();
        List<String> missingArch = new ArrayList<>();
        List<String> missingChanges = new ArrayList<>();
        List<String> missingSig = new ArrayList<>();
        List<String> subtree = new ArrayList<>();

        for (Module module : modules) {
            if ("unset".equals(module.tier())) {
                noTier.add(module.name());
            }
            if (module.isApi() && module.publicTypes() > 0
                    && module.publicTypes() - module.annotationTypes() - module.processors() <= 0) {
                annotationOnly.add(module.name());
            }
            if (module.isApi() && !module.arch()) {
                missingArch.add(module.name());
            }
            if (module.isApi() && !module.apichanges()) {
                missingChanges.add(module.name());
            }
            if (module.isApi() && module.signatures().isEmpty()) {
                missingSig.add(module.name());
            }
            if (module.isApi() && !module.publicSubpackages().isEmpty()) {
                subtree.add(module.name());
            }
        }

        for (Contract contract : contracts) {
            if (!"OK".equals(contract.verdict())) {
                findings.errors.add("registration " + contract.annotation() + " -> "
                        + contract.expectedType() + " in " + contract.owner() + " ["
                        + contract.ownerTier() + ", " + contract.exportKind() + "]: "
                        + contract.verdict());
            }
            if (!contract.processorChecked()) {
                findings.warnings.add("registration " + contract.annotation()
                        + " processor does not validate the SPI type");
            }
        }
        for (Edge edge : edges) {
            if (edge.violation()) {
                findings.errors.add("consumer " + edge.consumer() + " imports " + edge.pkg()
                        + " from " + edge.owner() + " [" + edge.ownerTier() + "]"
                        + (edge.publicExport() ? " (T0 public export)" : " (undeclared friend)"));
            }
        }

        if (!noTier.isEmpty()) {
            findings.warnings.add(noTier.size() + " module(s) have no tier metadata: "
                    + join(noTier));
        }
        if (!annotationOnly.isEmpty()) {
            findings.errors.add("API module(s) with only annotation types (contract is empty): "
                    + join(annotationOnly));
        }
        if (!missingArch.isEmpty()) {
            findings.errors.add("API module(s) missing arch.xml: " + join(missingArch));
        }
        if (!missingChanges.isEmpty()) {
            findings.errors.add("API module(s) missing apichanges.xml: " + join(missingChanges));
        }
        if (!missingSig.isEmpty()) {
            findings.warnings.add("API module(s) missing a signature baseline: " + join(missingSig));
        }
        if (!subtree.isEmpty()) {
            findings.errors.add("API module(s) exporting a subtree: " + join(subtree));
        }
        if (!edges.isEmpty()) {
            findings.notes.add(edges.size()
                    + " implementation-package/friend consumer edge(s); see the table above");
        }
        return findings;
    }

    // ---------------------------------------------------------------- helpers

    private static String group(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String group(Pattern pattern, String text, String fallback) {
        String value = group(pattern, text);
        return value.isBlank() ? fallback : value;
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void collect(String block, Pattern pattern, List<String> target) {
        if (block == null || block.isBlank()) {
            return;
        }
        Matcher matcher = pattern.matcher(block);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isEmpty()) {
                target.add(value);
            }
        }
    }

    private static String join(List<String> values) {
        return String.join(", ", new TreeSet<>(values));
    }
}
