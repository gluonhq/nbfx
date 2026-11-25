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

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.Processor;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.XMLFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Tests logical-area annotation generation and runtime metadata reconstruction. */
public class ViewAreaRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.layout.processors.ViewAreaRegistrationProcessor";

    @Test
    public void generatesMetadataAndReadsEqualArea() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistration(id = \"product:side\", kind = \"SIDE\", displayName = \"Side\",\n"
                + "    location = DockLocation.START, weight = 0.25, presentation = \"product:ribbon\",\n"
                + "    capabilities = {ViewAreaCapability.MOVABLE, ViewAreaCapability.MINIMIZABLE},\n"
                + "    policyId = \"restricted\", visibility = ViewAreaVisibility.ON_DEMAND,\n"
                + "    emptyPolicy = EmptyAreaPolicy.COLLAPSE, order = 5)\n"
                + "public final class DemoAreas {}\n";
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSOR, Map.of("demo/DemoAreas.java", source));

        String xml = compilation.layerXml();
        assertLayerPath(xml, LayerPaths.LAYOUT_AREAS);
        assertTrue(xml, xml.contains("<folder name=\"product:side\">"));
        assertTrue(xml, xml.contains("stringvalue=\"nbfx:side\""));
        assertTrue(xml, xml.contains("stringvalue=\"START\""));
        assertTrue(xml, xml.contains("doublevalue=\"0.25\""));
        assertTrue(xml, xml.contains("stringvalue=\"product:ribbon\""));
        assertTrue(xml, xml.contains("stringvalue=\"MINIMIZABLE MOVABLE\""));
        assertFalse(xml, xml.contains("instanceClass"));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        FileObject folder = fileSystem.findResource(LayerPaths.LAYOUT_AREAS);
        assertNotNull(folder);
        assertEquals(List.of(expectedArea()), LayoutRegistry.readAreas(folder));
    }

    @Test
    public void validatesBundleReferences() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistration(id = \"product:side\", displayName = \"#side\")\n"
                + "public final class DemoAreas {}\n";
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSOR, Map.of(
                        "demo/DemoAreas.java", source,
                        "demo/Bundle.properties", "side=Side\n"));
        assertTrue(compilation.layerXml(),
                compilation.layerXml().contains("bundlevalue=\"demo.Bundle#side\""));

        String missing = source.replace("#side", "#missing");
        String error = LayoutProcessorTestSupport.compileFailure(PROCESSOR, Map.of(
                "demo/DemoAreas.java", missing,
                "demo/Bundle.properties", "side=Side\n"));
        assertTrue(error, error.contains("No key 'missing'"));
    }

    @Test
    public void supportsDefaultsBuiltInCanonicalizationAndRepeatableContainer() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistrations({\n"
                + "  @ViewAreaRegistration(id = \"side\", displayName = \"Side\", order = 2),\n"
                + "  @ViewAreaRegistration(id = \"product:bottom\", displayName = \"Bottom\", order = 1)\n"
                + "})\n"
                + "public final class DemoAreas {}\n";
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSOR, Map.of("demo/DemoAreas.java", source));
        assertTrue(compilation.layerXml(), compilation.layerXml().contains("name=\"nbfx:side\""));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        List<ViewArea> areas = LayoutRegistry.readAreas(fileSystem.findResource(LayerPaths.LAYOUT_AREAS));
        assertEquals(2, areas.size());
        assertEquals(PaneRole.of("product:bottom"), areas.get(0).id());
        assertEquals(PaneRole.SIDE, areas.get(1).id());
        assertEquals(ViewAreaKind.MAIN, areas.get(1).kind());
        assertEquals(PresentationId.TABBED, areas.get(1).defaultPresentation());
    }

    @Test
    public void rejectsInvalidWeightIdAndDuplicates() throws Exception {
        String invalidWeight = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistration(id = \"product:side\", displayName = \"Side\", weight = Double.NaN)\n"
                + "public final class DemoAreas {}\n";
        String weightError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoAreas.java", invalidWeight));
        assertTrue(weightError, weightError.contains("weight must be within"));

        String invalidId = invalidWeight.replace("product:side", "inspector").replace(", weight = Double.NaN", "");
        String idError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoAreas.java", invalidId));
        assertTrue(idError, idError.contains("Invalid @ViewAreaRegistration id"));

        String duplicate = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistrations({\n"
                + "  @ViewAreaRegistration(id = \"product:side\", displayName = \"Side\"),\n"
                + "  @ViewAreaRegistration(id = \"product:side\", displayName = \"Side again\")\n"
                + "})\n"
                + "public final class DemoAreas {}\n";
        String duplicateError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoAreas.java", duplicate));
        assertTrue(duplicateError, duplicateError.contains("Duplicate @ViewAreaRegistration id"));
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("ViewAreaRegistrationProcessor service metadata missing", found);
    }

    private static ViewArea expectedArea() {
        return new ViewArea(PaneRole.of("product:side"), ViewAreaKind.SIDE, "Side",
                DockLocation.START, 0.25d, PresentationId.of("product:ribbon"),
                Set.of(ViewAreaCapability.MOVABLE, ViewAreaCapability.MINIMIZABLE), "restricted",
                AreaStyle.EMPTY, ViewAreaVisibility.ON_DEMAND, EmptyAreaPolicy.COLLAPSE, 5);
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }
}
