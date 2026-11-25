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
package org.netbeans.api.nbfx.layout;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.netbeans.api.nbfx.resources.ResourceRef;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Shared compiler and sample-layout support for layout processor tests. */
final class LayoutProcessorTestSupport {

    private LayoutProcessorTestSupport() {
    }

    record Compilation(Path layerFile, String layerXml) {
    }

    static Compilation compile(String processors, Map<String, String> sources) throws Exception {
        Result result = compile(processors, sources, true);
        return new Compilation(result.layerFile(), Files.readString(result.layerFile()));
    }

    static String compileFailure(String processors, Map<String, String> sources) throws Exception {
        return compile(processors, sources, false).diagnostics();
    }

    static String sampleLayoutXml(String id) {
        return "<layout id=\"" + id + "\" version=\"1\">"
                + "<areas><area id=\"product:editor\" displayName=\"#editor\"><style>"
                + "<class name=\"editor\"/>"
                + "<stylesheet module=\"org.example.app\" path=\"editor.css\"/>"
                + "</style></area></areas>"
                + "<groups><group id=\"product:editor.main\" area=\"product:editor\"/></groups>"
                + "<tree><group ref=\"product:editor.main\"/></tree>"
                + "</layout>";
    }

    static LayoutSpec sampleLayout(String id) {
        PaneRole editor = PaneRole.of("product:editor");
        DockGroupId group = DockGroupId.of("product:editor.main");
        AreaStyle style = new AreaStyle(Set.of("editor"),
                List.of(ResourceRef.of("org.example.app", "editor.css")), List.of());
        return LayoutBuilder.builder(id, 1)
                .area(new ViewArea(editor, ViewAreaKind.MAIN, "#editor", DockLocation.CENTER, 1.0d,
                        PresentationId.TABBED, Set.of(), "default", style,
                        ViewAreaVisibility.STARTUP, EmptyAreaPolicy.KEEP, 0))
                .group(new DockGroupSpec(group, editor, PaneKind.VIEW, PresentationId.TABBED,
                        ContentCapacity.MANY, "default", GroupLifetime.PERSISTENT, AreaStyle.EMPTY))
                .root(LayoutNode.group(group))
                .build();
    }

    static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    private static Result compile(String processors, Map<String, String> sources, boolean expectSuccess)
            throws Exception {
        Path dir = Files.createTempDirectory("nbfx-layout-processor-test");
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = dir.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
            files.add(file);
        }
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("no system Java compiler", compiler);
        String classpath = System.getProperty("java.class.path");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter output = new StringWriter();
        boolean success;
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<Path> javaFiles = files.stream()
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(javaFiles);
            success = Boolean.TRUE.equals(compiler.getTask(output, fileManager, diagnostics,
                    List.of("-classpath", classpath, "-processorpath", classpath,
                            "-sourcepath", dir.toString(), "-processor", processors, "-d", out.toString()),
                    null, units).call());
        }
        String diagnosticText = output.toString() + diagnostics.getDiagnostics();
        assertTrue("unexpected compiler result: " + diagnosticText, success == expectSuccess);
        Path layer = out.resolve("META-INF/generated-layer.xml");
        if (expectSuccess) {
            assertTrue("generated layer missing", Files.exists(layer));
            assertFalse("unwritten layer builder warning: " + diagnosticText,
                    diagnosticText.contains("was never written"));
        }
        return new Result(layer, diagnosticText);
    }

    private record Result(Path layerFile, String diagnostics) {
    }
}
