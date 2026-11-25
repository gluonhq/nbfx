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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.XMLFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Tests dock-group annotation generation, inheritance and runtime reconstruction. */
public class DockGroupRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.layout.processors.DockGroupRegistrationProcessor";
    private static final String PROCESSORS = String.join(",",
            PROCESSOR,
            "org.netbeans.modules.nbfx.api.layout.processors.ViewAreaRegistrationProcessor");

    @Test
    public void generatesMetadataAndReadsEqualGroup() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistration(id = \"product:editor\", displayName = \"Editor\")\n"
                + "@DockGroupRegistration(id = \"product:editor.main\", area = \"product:editor\",\n"
                + "    kind = PaneKind.EDITOR, presentation = \"product:ribbon\", capacity = ContentCapacity.ONE,\n"
                + "    policyId = \"restricted\", lifetime = GroupLifetime.PINNED)\n"
                + "public final class DemoGroups {}\n";
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSORS, Map.of("demo/DemoGroups.java", source));

        String xml = compilation.layerXml();
        assertLayerPath(xml, LayerPaths.DOCK_GROUPS);
        assertTrue(xml, xml.contains("<file name=\"product:editor.main\">"));
        assertTrue(xml, xml.contains("stringvalue=\"product:editor\""));
        assertTrue(xml, xml.contains("stringvalue=\"EDITOR\""));
        assertTrue(xml, xml.contains("stringvalue=\"product:ribbon\""));
        assertTrue(xml, xml.contains("stringvalue=\"ONE\""));
        assertTrue(xml, xml.contains("stringvalue=\"restricted\""));
        assertTrue(xml, xml.contains("stringvalue=\"PINNED\""));
        assertFalse(xml, xml.contains("instanceClass"));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        Map<PaneRole, ViewArea> areas = areaMap(fileSystem);
        List<DockGroupSpec> groups = LayoutRegistry.readGroups(
                fileSystem.findResource(LayerPaths.DOCK_GROUPS), areas);
        assertEquals(List.of(expectedGroup()), groups);
    }

    @Test
    public void omittedPresentationAndPolicyInheritFromArea() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistration(id = \"product:editor\", displayName = \"Editor\",\n"
                + "    presentation = \"product:ribbon\", policyId = \"restricted\")\n"
                + "@DockGroupRegistration(id = \"product:editor.main\", area = \"product:editor\")\n"
                + "public final class DemoGroups {}\n";
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSORS, Map.of("demo/DemoGroups.java", source));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        List<DockGroupSpec> groups = LayoutRegistry.readGroups(
                fileSystem.findResource(LayerPaths.DOCK_GROUPS), areaMap(fileSystem));
        assertEquals(1, groups.size());
        DockGroupSpec group = groups.get(0);
        assertEquals(PresentationId.of("product:ribbon"), group.presentation());
        assertEquals("restricted", group.policyId());
        assertEquals(PaneKind.VIEW, group.kind());
        assertEquals(ContentCapacity.MANY, group.capacity());
        assertEquals(GroupLifetime.PERSISTENT, group.lifetime());
    }

    @Test
    public void supportsRepeatableContainerAndBuiltInAreaCanonicalization() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@ViewAreaRegistration(id = \"main\", displayName = \"Main\")\n"
                + "@DockGroupRegistrations({\n"
                + "  @DockGroupRegistration(id = \"product:g1\", area = \"main\"),\n"
                + "  @DockGroupRegistration(id = \"product:g2\", area = \"main\",\n"
                + "      presentation = \"single\", policyId = \"other\")\n"
                + "})\n"
                + "public final class DemoGroups {}\n";
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSORS, Map.of("demo/DemoGroups.java", source));
        assertTrue(compilation.layerXml(), compilation.layerXml().contains("name=\"nbfx:main\""));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        Map<PaneRole, ViewArea> areas = areaMap(fileSystem);
        assertEquals(PaneRole.MAIN, areas.keySet().iterator().next());
        List<DockGroupSpec> groups = LayoutRegistry.readGroups(
                fileSystem.findResource(LayerPaths.DOCK_GROUPS), areas);
        assertEquals(2, groups.size());
        assertEquals(PaneRole.MAIN, groups.get(0).area());
        assertEquals(PresentationId.SINGLE, groups.get(1).presentation());
        assertEquals("other", groups.get(1).policyId());
    }

    @Test
    public void rejectsInvalidIdsAndDuplicates() throws Exception {
        String invalidId = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@DockGroupRegistration(id = \"main\", area = \"main\")\n"
                + "public final class DemoGroups {}\n";
        String idError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoGroups.java", invalidId));
        assertTrue(idError, idError.contains("Invalid @DockGroupRegistration id"));

        String duplicate = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@DockGroupRegistrations({\n"
                + "  @DockGroupRegistration(id = \"product:g\", area = \"main\"),\n"
                + "  @DockGroupRegistration(id = \"product:g\", area = \"main\")\n"
                + "})\n"
                + "public final class DemoGroups {}\n";
        String duplicateError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoGroups.java", duplicate));
        assertTrue(duplicateError, duplicateError.contains("Duplicate @DockGroupRegistration id"));
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("DockGroupRegistrationProcessor service metadata missing", found);
    }

    private static DockGroupSpec expectedGroup() {
        return new DockGroupSpec(DockGroupId.of("product:editor.main"), PaneRole.of("product:editor"),
                PaneKind.EDITOR, PresentationId.of("product:ribbon"), ContentCapacity.ONE,
                "restricted", GroupLifetime.PINNED, AreaStyle.EMPTY);
    }

    private static Map<PaneRole, ViewArea> areaMap(XMLFileSystem fileSystem) {
        FileObject folder = fileSystem.findResource(LayerPaths.LAYOUT_AREAS);
        assertNotNull(folder);
        Map<PaneRole, ViewArea> result = new HashMap<>();
        for (ViewArea area : LayoutRegistry.readAreas(folder)) {
            result.put(area.id(), area);
        }
        return result;
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }
}
