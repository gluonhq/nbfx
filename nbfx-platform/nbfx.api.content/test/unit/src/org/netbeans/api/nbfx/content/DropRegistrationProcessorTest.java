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
package org.netbeans.api.nbfx.content;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.XMLFileSystem;

import static org.junit.Assert.*;

public class DropRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.content.processors.DropRegistrationProcessor";

    @Test
    public void generatesDropHandlerLayerEntry() throws Exception {
        String source = "package demo;\n"
                + "import java.util.Set;\n"
                + "import org.netbeans.api.nbfx.content.*;\n"
                + "import org.openide.util.Lookup;\n"
                + "@DropRegistration(targetKind = \"group\", targetId = \"nbfx:main\", sources = {\"files\", \"uris\"}, position = 42)\n"
                + "public final class DemoDropHandler implements DropHandler {\n"
                + "  @Override public DropTarget target() { return new DropTarget(DropTargetKind.GROUP, \"nbfx:main\"); }\n"
                + "  @Override public Set<TransferKind> sourceKinds() { return Set.of(TransferKind.FILES, TransferKind.URIS); }\n"
                + "  @Override public DropDecision evaluate(DropRequest request) { return DropDecision.reject(\"no\"); }\n"
                + "  @Override public void perform(DropRequest request, DropDecision accepted) { }\n"
                + "}\n";
        Compilation compilation = compile(PROCESSOR, Map.of("demo/DemoDropHandler.java", source), true);
        String xml = compilation.xml();
        assertLayerPath(xml, LayerPaths.DROP_HANDLERS);
        assertTrue(xml, xml.contains("stringvalue=\"nbfx:group\""));
        assertTrue(xml, xml.contains("stringvalue=\"nbfx:main\""));
        assertTrue(xml, xml.contains("stringvalue=\"nbfx:files,nbfx:uris\""));
        assertTrue(xml, xml.contains("intvalue=\"42\""));
        assertTrue(xml, xml.contains("demo.DemoDropHandler"));
        XMLFileSystem fs = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        FileObject folder = fs.findResource(LayerPaths.DROP_HANDLERS);
        assertNotNull(folder);
        assertTrue(folder.getChildren().length > 0);
    }

    @Test
    public void rejectsInvalidSourceAndDuplicate() throws Exception {
        String badSource = "package demo;\n"
                + "import java.util.Set;\n"
                + "import org.netbeans.api.nbfx.content.*;\n"
                + "import org.openide.util.Lookup;\n"
                + "@DropRegistration(targetKind = \"group\", targetId = \"nbfx:main\", sources = {\"\"})\n"
                + "public final class Bad implements DropHandler {\n"
                + "  @Override public DropTarget target() { return new DropTarget(DropTargetKind.GROUP, \"nbfx:main\"); }\n"
                + "  @Override public Set<TransferKind> sourceKinds() { return Set.of(); }\n"
                + "  @Override public DropDecision evaluate(DropRequest request) { return DropDecision.reject(\"no\"); }\n"
                + "  @Override public void perform(DropRequest request, DropDecision accepted) { }\n"
                + "}\n";
        String err = compile(PROCESSOR, Map.of("demo/Bad.java", badSource), false).diagnostics();
        assertTrue(err, err.contains("Invalid drop source kind") || err.contains("source"));

        String dup = "package demo;\n"
                + "import java.util.Set;\n"
                + "import org.netbeans.api.nbfx.content.*;\n"
                + "import org.openide.util.Lookup;\n"
                + "@DropRegistrations({\n"
                + "  @DropRegistration(targetKind = \"group\", targetId = \"nbfx:main\", sources = {\"files\"}),\n"
                + "  @DropRegistration(targetKind = \"group\", targetId = \"nbfx:main\", sources = {\"files\"})\n"
                + "})\n"
                + "public final class Dup implements DropHandler {\n"
                + "  @Override public DropTarget target() { return new DropTarget(DropTargetKind.GROUP, \"nbfx:main\"); }\n"
                + "  @Override public Set<TransferKind> sourceKinds() { return Set.of(TransferKind.FILES); }\n"
                + "  @Override public DropDecision evaluate(DropRequest request) { return DropDecision.reject(\"no\"); }\n"
                + "  @Override public void perform(DropRequest request, DropDecision accepted) { }\n"
                + "}\n";
        String dupErr = compile(PROCESSOR, Map.of("demo/Dup.java", dup), false).diagnostics();
        assertTrue(dupErr, dupErr.contains("Duplicate drop handler registration"));
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("DropRegistrationProcessor service metadata missing", found);
    }

    private static Compilation compile(String processors, Map<String, String> sources, boolean expectSuccess)
            throws Exception {
        Path dir = Files.createTempDirectory("nbfx-drop-processor-test");
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = dir.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
        }
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("no system Java compiler", compiler);
        String classpath = System.getProperty("java.class.path");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter output = new StringWriter();
        boolean success;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<Path> javaFiles = new ArrayList<>();
            for (Map.Entry<String, String> source : sources.entrySet()) {
                if (source.getKey().endsWith(".java")) {
                    javaFiles.add(dir.resolve(source.getKey()));
                }
            }
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(javaFiles);
            success = Boolean.TRUE.equals(compiler.getTask(output, fm, diagnostics,
                    List.of("-classpath", classpath, "-processorpath", classpath,
                            "-sourcepath", dir.toString(), "-processor", processors, "-d", out.toString()),
                    null, units).call());
        }
        String diag = output + diagnostics.getDiagnostics().toString();
        assertEquals("unexpected compiler result: " + diag, expectSuccess, success);
        Path layer = out.resolve("META-INF/generated-layer.xml");
        if (expectSuccess) {
            assertTrue("generated layer missing", Files.exists(layer));
        }
        return new Compilation(layer, diag);
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }

    private record Compilation(Path layerFile, String diagnostics) {
        String xml() throws Exception {
            return Files.readString(layerFile);
        }
    }
}
