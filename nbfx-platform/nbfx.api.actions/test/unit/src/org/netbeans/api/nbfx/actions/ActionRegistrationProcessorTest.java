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
package org.netbeans.api.nbfx.actions;

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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Verifies Command registration and repeated action references. */
public class ActionRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.actions.processors.ActionRegistrationProcessor";
    private static final String SOURCE = "package demo;\n"
            + "import org.netbeans.api.nbfx.actions.*;\n"
            + "public class DemoActions {\n"
            + "    @ActionRegistration(id = \"demo.hello\", displayName = \"Hello\","
            + " iconName = \"hello.png\", accelerator = \"Shortcut+H\")\n"
            + "    @ActionReference(id = \"demo.hello\", path = \"Menus/File\", position = 100,\n"
            + "        displayName = \"#hello\", iconName = \"hello.png\", contextType = \"editor\",\n"
            + "        languageId = \"java\", mimeType = \"text/x-java\", profiles = {\"main\"},\n"
            + "        presentationHints = {\"iconOnly=true\", \"slot=primary\"})\n"
            + "    @ActionReference(id = \"demo.hello\", path = \"Toolbars/Main\", position = 10)\n"
            + "    @ActionReference(id = \"demo.hello\", path = \"ContextMenus/Editor\", position = 20)\n"
            + "    @ActionReference(id = \"demo.hello\", path = \"ShellRegions/nbfx:ribbon/Home/Clipboard\")\n"
            + "    @ActionReference(id = \"demo.hello\", path = \"ShellRegions/nbfx:brand\")\n"
            + "    public static class Hello extends RunnableCommand {\n"
            + "        public Hello() { super(\"demo.hello\", \"Hello\", null, () -> {}); }\n"
            + "    }\n"
            + "}\n";

    @Test
    public void generatesOneCommandAndReferencesAcrossSurfaces() throws Exception {
        String xml = ProcessorTestSupport.compile(PROCESSOR, Map.of(
                "demo/DemoActions.java", SOURCE,
                "demo/Bundle.properties", "hello=Hello\n"));

        assertTrue(xml, xml.contains("name=\"demo.hello.instance\""));
        assertTrue(xml, xml.contains("instanceClass\" stringvalue=\"demo.DemoActions$Hello\""));
        assertTrue(xml, xml.contains("displayName\" stringvalue=\"Hello\""));
        assertTrue(xml, xml.contains("iconName\" stringvalue=\"hello.png\""));
        assertTrue(xml, xml.contains("accelerator\" stringvalue=\"Shortcut+H\""));
        assertTrue(xml, xml.contains("actionId\" stringvalue=\"demo.hello\""));
        assertTrue(xml, xml.contains("bundlevalue=\"demo.Bundle#hello\""));
        assertTrue(xml, xml.contains("contextType\" stringvalue=\"editor\""));
        assertTrue(xml, xml.contains("languageId\" stringvalue=\"java\""));
        assertTrue(xml, xml.contains("mimeType\" stringvalue=\"text/x-java\""));
        assertTrue(xml, xml.contains("profiles\" stringvalue=\"main\""));
        assertTrue(xml, xml.contains("hint.iconOnly\" stringvalue=\"true\""));
        assertTrue(xml, xml.contains("hint.slot\" stringvalue=\"primary\""));
        assertLayerPath(xml, LayerPaths.ACTIONS);
        assertLayerPath(xml, LayerPaths.MENUS);
        assertLayerPath(xml, LayerPaths.TOOLBARS);
        assertLayerPath(xml, LayerPaths.CONTEXT_MENUS);
        assertLayerPath(xml, LayerPaths.SHELL_REGIONS);
        assertTrue(xml, xml.contains("name=\"nbfx:ribbon\""));
        assertTrue(xml, xml.contains("name=\"nbfx:brand\""));
        assertFalse(xml, xml.contains("instanceCreate"));
    }

    @Test
    public void rejectsNonCommandAndDuplicateIds() throws Exception {
        String runnable = "package demo;\n"
                + "import org.netbeans.api.nbfx.actions.*;\n"
                + "@ActionRegistration(id = \"demo.bad\")\n"
                + "public final class Bad implements Runnable { public void run() {} }\n";
        String typeError = ProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/Bad.java", runnable));
        assertTrue(typeError, typeError.contains("must implement Command"));

        String duplicate = "package demo;\n"
                + "import org.netbeans.api.nbfx.actions.*;\n"
                + "public class Duplicates {\n"
                + "  @ActionRegistration(id = \"same\") public static class One extends RunnableCommand {\n"
                + "    public One() { super(\"same\", \"One\", null, () -> {}); } }\n"
                + "  @ActionRegistration(id = \"same\") public static class Two extends RunnableCommand {\n"
                + "    public Two() { super(\"same\", \"Two\", null, () -> {}); } }\n"
                + "}\n";
        String duplicateError = ProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/Duplicates.java", duplicate));
        assertTrue(duplicateError, duplicateError.contains("Duplicate @ActionRegistration id"));

        String badProfile = SOURCE.replace("profiles = {\"main\"}", "profiles = {\"main,compact\"}");
        String profileError = ProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoActions.java", badProfile));
        assertTrue(profileError, profileError.contains("profiles must be atomic tokens"));

        String badHint = SOURCE.replace("presentationHints = {\"iconOnly=true\", \"slot=primary\"}",
                "presentationHints = {\"broken\"}");
        String hintError = ProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoActions.java", badHint));
        assertTrue(hintError, hintError.contains("Presentation hints must use key=value"));

        String badPath = SOURCE.replace("path = \"Menus/File\"", "path = \"../Menus\"");
        String pathError = ProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoActions.java", badPath));
        assertTrue(pathError, pathError.contains("relative NbFx surface path"));
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("ActionRegistrationProcessor service metadata missing", found);
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }

    private static final class ProcessorTestSupport {

        private ProcessorTestSupport() {
        }

        static String compile(String processor, Map<String, String> sources) throws Exception {
            return compile(processor, sources, true).layerXml();
        }

        static String compileFailure(String processor, Map<String, String> sources) throws Exception {
            return compile(processor, sources, false).diagnostics();
        }

        private static Result compile(String processor, Map<String, String> sources, boolean expectSuccess)
                throws Exception {
            Path dir = Files.createTempDirectory("nbfx-action-processor-test");
            List<Path> files = new ArrayList<>();
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path file = dir.resolve(source.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
                files.add(file);
            }
            List<Path> javaFiles = files.stream()
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
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
                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(javaFiles);
                success = Boolean.TRUE.equals(compiler.getTask(output, fileManager, diagnostics,
                        List.of("-classpath", classpath, "-processorpath", classpath,
                                "-sourcepath", dir.toString(), "-processor", processor, "-d", out.toString()),
                        null, units).call());
            }
            String diagnosticText = output.toString() + diagnostics.getDiagnostics();
            assertTrue("unexpected compiler result: " + diagnosticText, success == expectSuccess);
            Path layer = out.resolve("META-INF/generated-layer.xml");
            if (expectSuccess) {
                assertTrue("generated layer missing", Files.exists(layer));
            }
            return new Result(Files.exists(layer) ? Files.readString(layer) : "", diagnosticText);
        }

        private record Result(String layerXml, String diagnostics) {
        }
    }
}
