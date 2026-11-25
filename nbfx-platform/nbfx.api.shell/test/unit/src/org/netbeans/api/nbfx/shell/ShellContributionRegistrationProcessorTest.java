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

import java.util.Map;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerPaths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests shell contribution annotation generation and validation. */
public class ShellContributionRegistrationProcessorTest {

    private static final String PROCESSOR =
            "org.netbeans.modules.nbfx.api.shell.processors.ShellContributionRegistrationProcessor";

    @Test
    public void generatesDirectInstanceWithExplicitClassAndMetadata() throws Exception {
        String source = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "import javafx.scene.Node;\n"
                + "@ShellContributionRegistration(region = \"BRAND\", position = 10,\n"
                + "    profiles = {\"main\", \"compact\"})\n"
                + "public final class DemoContribution implements ShellContribution {\n"
                + "    public DemoContribution() {}\n"
                + "    @Override public Node create(ShellContext context) { return null; }\n"
                + "}\n";
        ShellProcessorTestSupport.Compilation compilation = ShellProcessorTestSupport.compile(
                PROCESSOR, Map.of("demo/DemoContribution.java", source));

        String xml = compilation.layerXml();
        assertLayerPath(xml, LayerPaths.SHELL_REGIONS);
        assertTrue(xml, xml.contains("<folder name=\"nbfx:brand\">"));
        assertTrue(xml, xml.contains("name=\"demo-DemoContribution.instance\""));
        assertTrue(xml, xml.contains("stringvalue=\"demo.DemoContribution\""));
        assertTrue(xml, xml.contains("intvalue=\"10\" name=\"position\""));
        assertTrue(xml, xml.contains("stringvalue=\"compact,main\""));
        assertFalse(xml, xml.contains("instanceCreate"));
    }

    @Test
    public void rejectsDockInvalidProfilesConstructorsAndDuplicates() throws Exception {
        String dock = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "import javafx.scene.Node;\n"
                + "@ShellContributionRegistration(region = \"DOCK\")\n"
                + "public final class DemoContribution implements ShellContribution {\n"
                + "    public DemoContribution() {}\n"
                + "    @Override public Node create(ShellContext context) { return null; }\n"
                + "}\n";
        String dockError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoContribution.java", dock));
        assertTrue(dockError, dockError.contains("DOCK is runtime-owned"));

        String profile = dock.replace("region = \"DOCK\"", "region = \"BRAND\", profiles = {\"main,compact\"}");
        String profileError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoContribution.java", profile));
        assertTrue(profileError, profileError.contains("profiles must be atomic tokens"));

        String constructor = profile.replace(", profiles = {\"main,compact\"}", "")
                .replace("public DemoContribution()", "private DemoContribution()");
        String constructorError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoContribution.java", constructor));
        assertTrue(constructorError, constructorError.contains("public no-argument constructor"));

        String duplicate = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "import javafx.scene.Node;\n"
                + "@ShellContributionRegistrations({\n"
                + "  @ShellContributionRegistration(region = \"BRAND\"),\n"
                + "  @ShellContributionRegistration(region = \"nbfx:brand\")\n"
                + "})\n"
                + "public final class DemoContribution implements ShellContribution {\n"
                + "    public DemoContribution() {}\n"
                + "    @Override public Node create(ShellContext context) { return null; }\n"
                + "}\n";
        String duplicateError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoContribution.java", duplicate));
        assertTrue(duplicateError, duplicateError.contains("Duplicate shell contribution registration"));
    }

    @Test
    public void rejectsWrongAbstractAndNonPublicTypes() throws Exception {
        String wrongType = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellContributionRegistration(region = \"BRAND\")\n"
                + "public final class DemoContribution { public DemoContribution() {} }\n";
        String typeError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoContribution.java", wrongType));
        assertTrue(typeError, typeError.contains("must implement ShellContribution"));

        String abstractType = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "@ShellContributionRegistration(region = \"BRAND\")\n"
                + "public abstract class DemoContribution implements ShellContribution {\n"
                + "    public DemoContribution() {}\n"
                + "}\n";
        String abstractError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoContribution.java", abstractType));
        assertTrue(abstractError, abstractError.contains("must not be abstract"));

        String nonPublic = "package demo;\n"
                + "import org.netbeans.api.nbfx.shell.*;\n"
                + "import javafx.scene.Node;\n"
                + "@ShellContributionRegistration(region = \"BRAND\")\n"
                + "class DemoContribution implements ShellContribution {\n"
                + "    public DemoContribution() {}\n"
                + "    @Override public Node create(ShellContext context) { return null; }\n"
                + "}\n";
        String visibilityError = ShellProcessorTestSupport.compileFailure(
                PROCESSOR, Map.of("demo/DemoContribution.java", nonPublic));
        assertTrue(visibilityError, visibilityError.contains("must be public"));
    }

    @Test
    public void processorIsDiscoverableAsService() {
        boolean found = ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> PROCESSOR.equals(provider.type().getName()));
        assertTrue("ShellContributionRegistrationProcessor service metadata missing", found);
    }

    private static void assertLayerPath(String xml, String path) {
        for (String segment : path.split("/")) {
            assertTrue("layer path " + path + " missing " + segment + " in:\n" + xml,
                    xml.contains("name=\"" + segment + "\""));
        }
    }
}
