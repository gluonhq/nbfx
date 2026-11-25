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
import org.openide.filesystems.FileObject;
import org.openide.filesystems.XMLFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Tests shell-profile annotation generation and registry equality. */
public class ShellProfileRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.shell.processors.ShellProfileRegistrationProcessor";

    @Test
    public void generatesRepeatableProfileFolders() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellProfileRegistrations({\n"
                + "  @ShellProfileRegistration(id = \"main\", windowKind = \"MAIN\","
                + " regions = {\"BRAND\", \"DOCK\"}, decoration = \"NATIVE\"),\n"
                + "  @ShellProfileRegistration(id = \"floating-editor\", windowKind = \"FLOATING\","
                + " regions = \"DOCK\", systemMenuBar = false)\n"
                + "})\n"
                + "public final class DemoProfiles {}\n";
        ShellProcessorTestSupport.Compilation compilation = ShellProcessorTestSupport.compile(
                PROCESSOR, Map.of("demo/DemoProfiles.java", source));
        String xml = compilation.layerXml();
        assertTrue(xml, xml.contains("<folder name=\"main\">"));
        assertTrue(xml, xml.contains("stringvalue=\"nbfx:brand,nbfx:dock\""));
        assertTrue(xml, xml.contains("boolvalue=\"false\" name=\"systemMenuBar\""));
        assertFalse(xml, xml.contains("instanceClass"));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        FileObject folder = fileSystem.findResource(LayerPaths.SHELL_PROFILES);
        assertNotNull(folder);
        List<ShellProfileSpec> profiles = ShellProfileRegistry.read(folder);
        assertEquals(List.of("floating-editor", "main"),
                profiles.stream().map(ShellProfileSpec::id).toList());
        assertEquals(Set.of(ShellRegionId.BRAND, ShellRegionId.DOCK), profiles.get(1).regions());
    }

    @Test
    public void rejectsDuplicateAndInvalidProfiles() throws Exception {
        String duplicate = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellProfileRegistrations({"
                + "@ShellProfileRegistration(id = \"main\"),"
                + "@ShellProfileRegistration(id = \"main\")})\n"
                + "public final class DemoProfiles {}\n";
        String duplicateError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoProfiles.java", duplicate));
        assertTrue(duplicateError, duplicateError.contains("Duplicate shell profile id"));

        for (String invalidId : List.of("bad/profile", "bad profile", "bad,profile", ".", "..")) {
            String invalid = duplicate.replace("id = \"main\"", "id = \"" + invalidId + "\"");
            String invalidError = ShellProcessorTestSupport.compileFailure(
                    PROCESSOR, Map.of("demo/DemoProfiles.java", invalid));
            assertTrue(invalidError, invalidError.contains("file-safe atomic token"));
        }
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("ShellProfileRegistrationProcessor service metadata missing", found);
    }
}
