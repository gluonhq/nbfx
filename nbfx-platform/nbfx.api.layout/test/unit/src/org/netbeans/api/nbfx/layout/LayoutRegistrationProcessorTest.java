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
import javax.annotation.processing.Processor;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.XMLFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Tests layout annotation generation and equality with the builder/XML authoring paths. */
public class LayoutRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.layout.processors.LayoutRegistrationProcessor";

    @Test
    public void generatesSelfContainedLayerResource() throws Exception {
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSOR, Map.of(
                        "demo/DemoLayout.java", source("product.default", sampleXml()),
                        "demo/Bundle.properties", "editor=Editor\n"));

        String xml = compilation.layerXml();
        assertLayerPath(xml, LayerPaths.LAYOUTS);
        assertTrue(xml, xml.contains("name=\"product.default.xml\""));
        assertTrue(xml, xml.contains("<![CDATA["));
        assertTrue(xml, xml.contains("encoding=\"UTF-8\""));
        assertFalse(xml, xml.contains("instanceClass"));
        assertFalse(xml, xml.contains(".validate-"));

        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        FileObject folder = fileSystem.findResource(LayerPaths.LAYOUTS);
        assertNotNull(folder);
        List<LayoutSpec> layouts = LayoutRegistry.read(folder);
        assertEquals(List.of(LayoutProcessorTestSupport.sampleLayout("product.default")), layouts);
    }

    @Test
    public void supportsRepeatableContainer() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@LayoutRegistrations({\n"
                + "  @LayoutRegistration(id = \"product.default\", xml = "
                + LayoutProcessorTestSupport.javaString(sampleXml()) + "),\n"
                + "  @LayoutRegistration(id = \"product.alternate\", xml = "
                + LayoutProcessorTestSupport.javaString(
                        LayoutProcessorTestSupport.sampleLayoutXml("product.alternate")) + ")\n"
                + "})\n"
                + "public final class DemoLayouts {}\n";
        LayoutProcessorTestSupport.Compilation compilation = LayoutProcessorTestSupport.compile(
                PROCESSOR, Map.of(
                        "demo/DemoLayouts.java", source,
                        "demo/Bundle.properties", "editor=Editor\n"));

        assertTrue(compilation.layerXml(), compilation.layerXml().contains("name=\"product.default.xml\""));
        assertTrue(compilation.layerXml(), compilation.layerXml().contains("name=\"product.alternate.xml\""));
        XMLFileSystem fileSystem = new XMLFileSystem(compilation.layerFile().toUri().toURL());
        assertEquals(List.of(
                LayoutProcessorTestSupport.sampleLayout("product.alternate"),
                LayoutProcessorTestSupport.sampleLayout("product.default")),
                LayoutRegistry.read(fileSystem.findResource(LayerPaths.LAYOUTS)));
    }

    @Test
    public void rejectsInvalidXmlAndIdMismatch() throws Exception {
        String mismatch = source("product.other", sampleXml());
        String mismatchError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoLayout.java", mismatch));
        assertTrue(mismatchError, mismatchError.contains("id must match"));

        String malformed = source("product.default", "<layout>");
        String malformedError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoLayout.java", malformed));
        assertTrue(malformedError, malformedError.contains("invalid layout XML"));

        String unsafe = source("product.default", "<!DOCTYPE layout><layout id=\"product.default\""
                + " version=\"1\"><tree><group ref=\"product:missing\"/></tree></layout>");
        String unsafeError = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoLayout.java", unsafe));
        assertTrue(unsafeError, unsafeError.contains("invalid layout XML"));
    }

    @Test
    public void rejectsMissingBundleKeyAndDuplicateIds() throws Exception {
        String missingKey = source("product.default", sampleXml().replace("#editor", "#missing"));
        String bundleError = LayoutProcessorTestSupport.compileFailure(PROCESSOR, Map.of(
                "demo/DemoLayout.java", missingKey,
                "demo/Bundle.properties", "editor=Editor\n"));
        assertTrue(bundleError, bundleError.contains("No key 'missing'"));

        String duplicate = "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@LayoutRegistrations({\n"
                + "  @LayoutRegistration(id = \"product.default\", xml = "
                + LayoutProcessorTestSupport.javaString(sampleXml()) + "),\n"
                + "  @LayoutRegistration(id = \"product.default\", xml = "
                + LayoutProcessorTestSupport.javaString(sampleXml()) + ")\n"
                + "})\n"
                + "public final class DemoLayouts {}\n";
        String duplicateError = LayoutProcessorTestSupport.compileFailure(PROCESSOR, Map.of(
                "demo/DemoLayouts.java", duplicate,
                "demo/Bundle.properties", "editor=Editor\n"));
        assertTrue(duplicateError, duplicateError.contains("Duplicate @LayoutRegistration id"));
    }

    @Test
    public void rejectsNonUtf8EncodingDeclaration() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + sampleXml();
        String error = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoLayout.java", source("product.default", xml)));
        assertTrue(error, error.contains("must use UTF-8"));
    }

    @Test
    public void rejectsUnsafeLayerFileId() throws Exception {
        String error = LayoutProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoLayout.java", source("../bad", sampleXml())));
        assertTrue(error, error.contains("file-safe id"));
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("LayoutRegistrationProcessor service metadata missing", found);
    }

    private static String sampleXml() {
        return LayoutProcessorTestSupport.sampleLayoutXml("product.default");
    }

    private static String source(String id, String xml) {
        return "package demo;\n"
                + "import org.netbeans.api.nbfx.layout.*;\n"
                + "@LayoutRegistration(id = " + LayoutProcessorTestSupport.javaString(id)
                + ", xml = " + LayoutProcessorTestSupport.javaString(xml) + ")\n"
                + "public final class DemoLayout {}\n";
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }
}
