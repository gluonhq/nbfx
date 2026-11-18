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
package org.netbeans.api.nbfx.nodes;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The property slice of the API-only downstream fixture: a provider written with the
 * {@code api.nodes} imports alone compiles without {@code platform.nodes}.
 */
public class PropertiesApiFixtureTest {

    private static final String SOURCE =
            "package fixture;\n"
            + "import java.util.List;\n"
            + "import org.netbeans.api.nbfx.nodes.*;\n"
            + "public class FixtureProvider implements PropertiesProvider {\n"
            + "    public List<PropertySetModel> getPropertySets(Object context) {\n"
            + "        return List.of(new SimplePropertySetModel(\"general\", \"General\",\n"
            + "                List.of(new SimplePropertyModel(\"name\", \"Name\", String.class, \"value\"))));\n"
            + "    }\n"
            + "}\n";

    @Test
    public void providerCompilesAgainstTheApiOnly() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-nodes-api-fixture");
        Path source = dir.resolve("fixture/FixtureProvider.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, SOURCE);
        Path out = dir.resolve("out");
        Files.createDirectories(out);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("no system Java compiler", compiler);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = new ArrayList<>(List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", out.toString()));
            Iterable<? extends JavaFileObject> units =
                    files.getJavaFileObjectsFromPaths(List.of(source));
            StringWriter output = new StringWriter();
            boolean success = compiler.getTask(output, files, diagnostics, options, null, units).call();
            assertTrue("fixture did not compile:\n" + output + "\n" + diagnostics.getDiagnostics(), success);
        }

        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.netbeans.modules.nbfx.platform.nodes.PropertySheet"));
    }
}
