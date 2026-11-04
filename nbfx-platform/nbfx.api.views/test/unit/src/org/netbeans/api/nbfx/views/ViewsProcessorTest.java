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
package org.netbeans.api.nbfx.views;

import org.netbeans.api.nbfx.layer.LayerPaths;
import java.io.StringWriter;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Verifies the view, menu and tool bar registration processors and the layer paths their readers
 * use.
 *
 * @since 1.0
 */
public class ViewsProcessorTest {

    private static final String SOURCE =
            "package demo;\n"
            + "import org.netbeans.api.nbfx.views.*;\n"
            + "@ViewRegistration(id = \"demo.view\", displayName = \"Demo View\","
            + " location = ViewLocation.LEFT_BOTTOM, openAtStartup = true)\n"
            + "@MenuRegistration(id = \"Navigate\", position = 40)\n"
            + "@MenuRegistration(id = \"Inspect\", path = \"ContextMenus/Editor\", position = 100,"
            + " separatorBefore = true)\n"
            + "@ToolbarRegistration(id = \"Main\", displayName = \"Main\", position = 10)\n"
            + "public class DemoViews {}\n";

    private static final String PROCESSORS = String.join(",",
            "org.netbeans.modules.nbfx.api.views.processors.ViewRegistrationProcessor",
            "org.netbeans.modules.nbfx.api.views.processors.MenuRegistrationProcessor",
            "org.netbeans.modules.nbfx.api.views.processors.ToolbarRegistrationProcessor");

    @Test
    public void generatesLayerFragments() throws Exception {
        String xml = ProcessorTestSupport.compile(PROCESSORS,
                Map.of("demo/DemoViews.java", SOURCE));

        assertTrue(xml, xml.contains("name=\"demo.view.instance\""));
        assertTrue(xml, xml.contains("location\" stringvalue=\"LEFT_BOTTOM\""));
        assertTrue(xml, xml.contains("boolvalue=\"true\" name=\"openAtStartup\""));
        assertTrue(xml, xml.contains("<folder name=\"Navigate\">"));
        assertTrue(xml, xml.contains("<folder name=\"ContextMenus\">"));
        assertTrue(xml, xml.contains("<folder name=\"Editor\">"));
        assertTrue(xml, xml.contains("<folder name=\"Inspect\">"));
        assertTrue(xml, xml.contains("boolvalue=\"true\" name=\"separatorBefore\""));
        assertTrue(xml, xml.contains("<folder name=\"Main\">"));
        assertTrue(xml, xml.contains("displayName\" stringvalue=\"Main\""));

        assertLayerPath(xml, LayerPaths.VIEWS);
        assertLayerPath(xml, LayerPaths.MENUS);
        assertLayerPath(xml, LayerPaths.TOOLBARS);
        assertLayerPath(xml, LayerPaths.CONTEXT_MENUS);
    }


    private static final String DYNAMIC_SOURCE =
            "package demo;\n"
            + "import org.netbeans.api.nbfx.views.*;\n"
            + "import org.openide.util.Lookup;\n"
            + "import javafx.collections.FXCollections;\n"
            + "import javafx.collections.ObservableList;\n"
            + "import javafx.scene.control.MenuItem;\n"
            + "@DynamicMenuRegistration(path = \"Menus/File\", id = \"recent\", position = 30,"
            + " displayName = \"Recent\", separatorBefore = true)\n"
            + "public class DemoDynamic implements DynamicMenu.Factory {\n"
            + "    public DynamicMenu create(Lookup context) {\n"
            + "        return new DynamicMenu() {\n"
            + "            public ObservableList<MenuItem> items() {\n"
            + "                return FXCollections.observableArrayList();\n"
            + "            }\n"
            + "        };\n"
            + "    }\n"
            + "}\n";

    @Test
    public void generatesDynamicMenuFragment() throws Exception {
        String xml = ProcessorTestSupport.compile(
                "org.netbeans.modules.nbfx.api.views.processors.DynamicMenuRegistrationProcessor",
                Map.of("demo/DemoDynamic.java", DYNAMIC_SOURCE));

        assertLayerPath(xml, LayerPaths.MENUS);
        assertTrue(xml, xml.contains("name=\"recent.dynamic\""));
        assertTrue(xml, xml.contains("factory\" stringvalue=\"demo.DemoDynamic\""));
        assertTrue(xml, xml.contains("displayName\" stringvalue=\"Recent\""));
        assertTrue(xml, xml.contains("boolvalue=\"true\" name=\"separatorBefore\""));
    }


    private static final String AREA_SOURCE =
            "package demo;\n"
            + "import org.netbeans.api.nbfx.views.*;\n"
            + "import org.netbeans.api.nbfx.view.DockLocation;\n"
            + "@AreaRegistration(id = \"right-sidebar\", displayName = \"Right Sidebar\","
            + " location = DockLocation.RIGHT, weight = 0.25, position = 10)\n"
            + "public class DemoArea {}\n";

    @Test
    public void generatesViewAreaFragment() throws Exception {
        String xml = ProcessorTestSupport.compile(
                "org.netbeans.modules.nbfx.api.views.processors.AreaRegistrationProcessor",
                Map.of("demo/DemoArea.java", AREA_SOURCE));

        assertLayerPath(xml, LayerPaths.VIEW_AREAS);
        assertTrue(xml, xml.contains("name=\"right-sidebar\""));
        assertTrue(xml, xml.contains("displayName\" stringvalue=\"Right Sidebar\""));
        assertTrue(xml, xml.contains("location\" stringvalue=\"RIGHT\""));
        assertTrue(xml, xml.contains("doublevalue=\"0.25\" name=\"weight\""));
    }


    @Test
    public void generatesWindowLayoutFragment() throws Exception {
        String source =
                "package demo;\n"
                + "import org.netbeans.api.nbfx.views.*;\n"
                + "@WindowLayoutRegistration(id = \"default\","
                + " tree = \"H[0.3000](P:NAVIGATOR,P:MAIN)\")\n"
                + "public class DemoLayout {}\n";
        String xml = ProcessorTestSupport.compile(
                "org.netbeans.modules.nbfx.api.views.processors.WindowLayoutRegistrationProcessor",
                Map.of("demo/DemoLayout.java", source));

        assertLayerPath(xml, LayerPaths.WINDOW_LAYOUTS);
        assertTrue(xml, xml.contains("name=\"default\""));
        assertTrue(xml, xml.contains("tree\" stringvalue=\"H[0.3000](P:NAVIGATOR,P:MAIN)\""));
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing segment " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }

    private static final class ProcessorTestSupport {

        private ProcessorTestSupport() {
        }

        static String compile(String processors, Map<String, String> sources) throws Exception {
            Path dir = Files.createTempDirectory("nbfx-views-processor-test");
            List<Path> files = new ArrayList<>();
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path file = dir.resolve(source.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue());
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
                    compiler.getStandardFileManager(diagnostics, null, null)) {
                Iterable<? extends JavaFileObject> units =
                        fileManager.getJavaFileObjectsFromPaths(files);
                success = Boolean.TRUE.equals(compiler.getTask(output, fileManager, diagnostics,
                        List.of("-classpath", classpath, "-processorpath", classpath,
                                "-processor", processors, "-d", out.toString()),
                        null, units).call());
            }
            assertTrue("compilation failed: " + output + diagnostics.getDiagnostics(), success);
            Path layer = out.resolve("META-INF/generated-layer.xml");
            assertTrue("generated layer missing", Files.exists(layer));
            return Files.readString(layer);
        }
    }
}
