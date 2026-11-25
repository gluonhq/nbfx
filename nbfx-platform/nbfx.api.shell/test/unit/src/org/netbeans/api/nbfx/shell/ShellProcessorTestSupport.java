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
package org.netbeans.api.nbfx.shell;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Shared compiler support for shell-region processor tests. */
final class ShellProcessorTestSupport {

    private ShellProcessorTestSupport() {
    }

    record Compilation(Path layerFile, String layerXml) {
    }

    static Compilation compile(String processor, Map<String, String> sources) throws Exception {
        Result result = compile(processor, sources, true);
        return new Compilation(result.layerFile(), Files.readString(result.layerFile()));
    }

    static String compileFailure(String processor, Map<String, String> sources) throws Exception {
        return compile(processor, sources, false).diagnostics();
    }

    private static Result compile(String processor, Map<String, String> sources, boolean expectSuccess)
            throws Exception {
        Path dir = Files.createTempDirectory("nbfx-shell-region-processor-test");
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
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(files);
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
            assertFalse("unwritten layer builder warning: " + diagnosticText,
                    diagnosticText.contains("was never written"));
        }
        return new Result(layer, diagnosticText);
    }

    private record Result(Path layerFile, String diagnostics) {
    }
}
