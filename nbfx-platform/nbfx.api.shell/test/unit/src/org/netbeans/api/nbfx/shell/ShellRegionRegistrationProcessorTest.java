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

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.Processor;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.layout.AreaStyle;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.XMLFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Tests shell-region annotation generation and metadata reconstruction. */
public class ShellRegionRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.shell.processors.ShellRegionRegistrationProcessor";

    @Test
    public void generatesMetadataAndReadsEqualSpec() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellRegionRegistration(id = \"BRAND\", kind = \"BRAND\", position = 10,\n"
                + "    profiles = {\"main\", \"compact\"}, styleClass = \"acme-brand\",\n"
                + "    visibleByDefault = false)\n"
                + "public final class DemoRegions {}\n";
        ShellProcessorTestSupport.Compilation compilation = ShellProcessorTestSupport.compile(
                PROCESSOR, Map.of("demo/DemoRegions.java", source));

        String xml = compilation.layerXml();
        assertLayerPath(xml, LayerPaths.SHELL_REGIONS);
        assertTrue(xml, xml.contains("<folder name=\"nbfx:brand\">"));
        assertTrue(xml, xml.contains("stringvalue=\"nbfx:brand\""));
        assertTrue(xml, xml.contains("stringvalue=\"compact,main\""));
        assertTrue(xml, xml.contains("stringvalue=\"acme-brand\""));
        assertTrue(xml, xml.contains("boolvalue=\"false\" name=\"visibleByDefault\""));
        assertFalse(xml, xml.contains("instanceClass"));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        FileObject folder = fileSystem.findResource(LayerPaths.SHELL_REGIONS);
        assertNotNull(folder);
        ShellRegionSpec expected = new ShellRegionSpec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                10, Set.of("main", "compact"), new AreaStyle(Set.of("acme-brand"), List.of(), List.of()),
                false);
        assertEquals(List.of(expected), ShellRegionRegistry.read(folder));
    }

    @Test
    public void supportsRepeatableContainerAndProductKinds() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellRegionRegistrations({\n"
                + "  @ShellRegionRegistration(id = \"acme:utility\", kind = \"acme:tool\", position = 5),\n"
                + "  @ShellRegionRegistration(id = \"DOCK\", kind = \"DOCK\", position = 50)\n"
                + "})\n"
                + "public final class DemoRegions {}\n";
        ShellProcessorTestSupport.Compilation compilation = ShellProcessorTestSupport.compile(
                PROCESSOR, Map.of("demo/DemoRegions.java", source));
        assertTrue(compilation.layerXml(), compilation.layerXml().contains("name=\"acme:utility\""));
        assertTrue(compilation.layerXml(), compilation.layerXml().contains("name=\"nbfx:dock\""));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        List<ShellRegionSpec> regions = ShellRegionRegistry.read(
                fileSystem.findResource(LayerPaths.SHELL_REGIONS));
        assertEquals(2, regions.size());
        assertEquals(ShellRegionKind.of("acme:tool"), regions.get(0).kind());
        assertEquals(ShellRegionId.DOCK, regions.get(1).id());
    }

    @Test
    public void rejectsInvalidIdsKindsProfilesAndDuplicates() throws Exception {
        String invalidId = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellRegionRegistration(id = \"utility\", kind = \"tool\")\n"
                + "public final class DemoRegions {}\n";
        String idError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoRegions.java", invalidId));
        assertTrue(idError, idError.contains("Invalid @ShellRegionRegistration id/kind"));

        String blankProfile = invalidId.replace("utility", "acme:utility")
                .replace("kind = \"tool\"", "kind = \"acme:tool\", profiles = {\" \"}");
        String profileError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoRegions.java", blankProfile));
        assertTrue(profileError, profileError.contains("profiles must be nonblank atomic tokens"));

        String delimitedProfile = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellRegionRegistration(id = \"acme:utility\", kind = \"acme:tool\",\n"
                + "    profiles = {\"main,compact\"})\n"
                + "public final class DemoRegions {}\n";
        String delimitedError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoRegions.java", delimitedProfile));
        assertTrue(delimitedError, delimitedError.contains("profiles must be nonblank atomic tokens"));

        String invalidStyle = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellRegionRegistration(id = \"acme:utility\", kind = \"acme:tool\",\n"
                + "    styleClass = \"acme brand\")\n"
                + "public final class DemoRegions {}\n";
        String styleError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoRegions.java", invalidStyle));
        assertTrue(styleError, styleError.contains("styleClass must be one CSS class token"));

        String duplicate = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellRegionRegistrations({\n"
                + "  @ShellRegionRegistration(id = \"BRAND\", kind = \"BRAND\"),\n"
                + "  @ShellRegionRegistration(id = \"nbfx:brand\", kind = \"BRAND\")\n"
                + "})\n"
                + "public final class DemoRegions {}\n";
        String duplicateError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoRegions.java", duplicate));
        assertTrue(duplicateError, duplicateError.contains("Duplicate @ShellRegionRegistration id"));
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("ShellRegionRegistrationProcessor service metadata missing", found);
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }
}
