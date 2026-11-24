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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.netbeans.api.nbfx.resources.ResourceRef;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests the standard XML authoring form, diagnostics, includes and secure parsing. */
public class LayoutXmlTest {

    @Test
    public void roundTripsCompleteLayoutDeterministically() throws IOException {
        PaneRole side = PaneRole.of("product:side");
        PaneRole main = PaneRole.of("product:editor");
        DockGroupId left = DockGroupId.of("product:side.main");
        DockGroupId right = DockGroupId.of("product:editor.main");
        AreaStyle sideStyle = new AreaStyle(Set.of("product", "side", "a,b", " padded ", ""),
                List.of(ResourceRef.of("org.example.app", "styles/side.css")),
                List.of(ResourceRef.of("org.example.theme", "overlays/dark.css")));
        AreaStyle editorStyle = new AreaStyle(Set.of("editor"), List.of(), List.of());
        LayoutSpec original = LayoutBuilder.builder("product.default", 1)
                .area(new ViewArea(side, ViewAreaKind.SIDE, "Side <tool>", DockLocation.START, 0.25d,
                        PresentationId.TABBED, Set.of(ViewAreaCapability.MOVABLE), "default",
                        sideStyle, ViewAreaVisibility.STARTUP, EmptyAreaPolicy.KEEP, 0))
                .area(new ViewArea(main, ViewAreaKind.MAIN, "Editor", DockLocation.CENTER, 1.0d,
                        PresentationId.TABBED, Set.of(), "default", AreaStyle.EMPTY,
                        ViewAreaVisibility.ON_DEMAND, EmptyAreaPolicy.COLLAPSE, 1))
                .group(new DockGroupSpec(left, side, PaneKind.VIEW,
                        new PresentationId("NBFX", "TABBED"),
                        ContentCapacity.MANY, "default", GroupLifetime.PERSISTENT, AreaStyle.EMPTY))
                .group(new DockGroupSpec(right, main, PaneKind.EDITOR,
                        PresentationId.of("product:ribbon"), ContentCapacity.ONE, "editor",
                        GroupLifetime.PINNED, editorStyle))
                .metadata("owner", "product & team")
                .metadata("blank", "")
                .split(SplitOrientation.HORIZONTAL, List.of(0.0d, 1.0d),
                        LayoutNode.group(left),
                        LayoutNode.slot("product:side.slot", side, DockSide.START),
                        LayoutNode.group(right))
                .build();

        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        LayoutXml.write(original, first);
        LayoutXml.write(original, second);
        assertArrayEquals(first.toByteArray(), second.toByteArray());

        LayoutSpec restored = LayoutXml.read(input(first.toByteArray()));
        assertEquals(original, restored);
        assertEquals("product & team", restored.metadata().get("owner"));
        assertEquals("", restored.metadata().get("blank"));
    }

    @Test
    public void equalLayoutsWithDifferentDeclarationOrderSerializeIdentically() throws IOException {
        PaneRole side = PaneRole.of("product:side");
        PaneRole main = PaneRole.of("product:editor");
        DockGroupId left = DockGroupId.of("product:side.main");
        DockGroupId right = DockGroupId.of("product:editor.main");
        ViewArea sideArea = area(side, ViewAreaKind.SIDE, "Side", 0);
        ViewArea mainArea = area(main, ViewAreaKind.MAIN, "Editor", 1);
        DockGroupSpec leftGroup = group(left, side);
        DockGroupSpec rightGroup = group(right, main);
        LayoutSpec first = LayoutBuilder.builder("product.default", 1)
                .area(sideArea).area(mainArea).group(leftGroup).group(rightGroup)
                .metadata("a", "one").metadata("b", "two")
                .split(SplitOrientation.HORIZONTAL, List.of(0.25d),
                        LayoutNode.group(left), LayoutNode.group(right))
                .build();
        LayoutSpec second = LayoutBuilder.builder("product.default", 1)
                .area(mainArea).area(sideArea).group(rightGroup).group(leftGroup)
                .metadata("b", "two").metadata("a", "one")
                .split(SplitOrientation.HORIZONTAL, List.of(0.25d),
                        LayoutNode.group(left), LayoutNode.group(right))
                .build();
        assertEquals(first, second);

        ByteArrayOutputStream firstXml = new ByteArrayOutputStream();
        ByteArrayOutputStream secondXml = new ByteArrayOutputStream();
        LayoutXml.write(first, firstXml);
        LayoutXml.write(second, secondXml);
        assertArrayEquals(firstXml.toByteArray(), secondXml.toByteArray());
    }

    @Test
    public void readsCanonicalXml() throws IOException {
        String xml = """
                <layout id="product.default" version="1">
                  <areas>
                    <area id="product:explorer" kind="SIDE" displayName="#explorer"
                          presentation="tabbed" visibility="startup"
                          capabilities="minimizable closable movable detachable">
                      <style>
                        <class name="explorer"/>
                        <stylesheet module="org.example.product" path="styles/explorer.css"/>
                        <themeOverlay module="org.example.theme" path="overlays/dark.css"/>
                      </style>
                    </area>
                    <area id="product:editor" kind="MAIN" displayName="#editor"
                          presentation="tabbed" visibility="startup"
                          capabilities="maximizable splittable detachable reorderable"/>
                  </areas>
                  <groups>
                    <group id="product:editor.main" area="product:editor"
                           capacity="many" presentation="tabbed"/>
                    <group id="product:explorer.main" area="product:explorer"
                           capacity="many" presentation="tabbed"/>
                  </groups>
                  <tree>
                    <split orientation="horizontal" dividers="0.25">
                      <group ref="product:explorer.main"/>
                      <group ref="product:editor.main"/>
                    </split>
                  </tree>
                </layout>
                """;

        LayoutSpec layout = LayoutXml.read(input(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("product.default", layout.id());
        ViewArea explorer = layout.areas().get(PaneRole.of("product:explorer"));
        assertEquals(ViewAreaKind.SIDE, explorer.kind());
        assertEquals("#explorer", explorer.displayName());
        assertEquals(Set.of(ViewAreaCapability.MINIMIZABLE, ViewAreaCapability.CLOSABLE,
                ViewAreaCapability.MOVABLE, ViewAreaCapability.DETACHABLE), explorer.capabilities());
        assertEquals(Set.of("explorer"), explorer.style().styleClasses());
        assertEquals(List.of(0.25d), ((LayoutNode.Split) layout.root()).dividers());
    }

    @Test
    public void groupDefaultsInheritFromArea() throws IOException {
        String xml = """
                <layout id="product.default" version="1">
                  <areas>
                    <area id="product:editor" kind="MAIN" displayName="#editor"
                          presentation="product:ribbon" policyId="product:restricted"/>
                  </areas>
                  <groups>
                    <group id="product:editor.main" area="product:editor"/>
                  </groups>
                  <tree><group ref="product:editor.main"/></tree>
                </layout>
                """;

        LayoutSpec layout = LayoutXml.read(input(xml.getBytes(StandardCharsets.UTF_8)));
        DockGroupSpec group = layout.groups().get(DockGroupId.of("product:editor.main"));
        assertEquals(PresentationId.of("product:ribbon"), group.presentation());
        assertEquals("product:restricted", group.policyId());
    }

    @Test
    public void resolvesClosedCallerOwnedIncludes() throws IOException {
        String main = """
                <layout id="product.default" version="1">
                  <areas>
                    <area id="product:editor" kind="MAIN" displayName="#editor"/>
                    <include path="side.xml"/>
                  </areas>
                  <groups>
                    <group id="product:editor.main" area="product:editor"/>
                    <include path="side-groups.xml"/>
                  </groups>
                  <tree>
                    <split orientation="horizontal" dividers="0.25">
                      <group ref="product:side.main"/>
                      <group ref="product:editor.main"/>
                    </split>
                  </tree>
                </layout>
                """;
        String areas = """
                <areas>
                  <area id="product:side" kind="SIDE" displayName="Side"/>
                </areas>
                """;
        String groups = """
                <groups>
                  <group id="product:side.main" area="product:side"/>
                </groups>
                """;
        Map<String, String> includes = Map.of("side.xml", areas, "side-groups.xml", groups);
        Set<String> closed = new HashSet<>();

        LayoutSpec layout = LayoutXml.read(input(main.getBytes(StandardCharsets.UTF_8)),
                (path, includingPath) -> new ByteArrayInputStream(
                        includes.get(path).getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() throws IOException {
                        closed.add(path);
                        super.close();
                    }
                });
        assertEquals(2, layout.areas().size());
        assertEquals(2, layout.groups().size());
        assertEquals("Side", layout.areas().get(PaneRole.of("product:side")).displayName());
        assertEquals(0, layout.areas().get(PaneRole.of("product:editor")).order());
        assertEquals(1, layout.areas().get(PaneRole.of("product:side")).order());
        assertEquals(includes.keySet(), closed);
    }

    @Test
    public void leavesMainStreamOpenOnSuccessAndFailure() throws IOException {
        String valid = """
                <layout id="product.default" version="1">
                  <areas><area id="product:editor" displayName="#editor"/></areas>
                  <groups><group id="product:editor.main" area="product:editor"/></groups>
                  <tree><group ref="product:editor.main"/></tree>
                </layout>
                """;
        TrackingInputStream success = new TrackingInputStream(valid.getBytes(StandardCharsets.UTF_8));
        LayoutXml.read(success);
        assertFalse(success.closed);

        String invalid = """
                <layout id="bad" version="1">
                  <tree><group ref="product:missing"/></tree>
                </layout>
                """;
        TrackingInputStream semanticFailure = new TrackingInputStream(
                invalid.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> LayoutXml.read(semanticFailure));
        assertFalse(semanticFailure.closed);

        TrackingInputStream malformed = new TrackingInputStream(
                "<layout>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> LayoutXml.read(malformed));
        assertFalse(malformed.closed);
    }

    @Test
    public void boundsIncludeDepthAndClosesOpenedStreams() {
        String main = """
                <layout id="product.default" version="1">
                  <areas><include path="0"/></areas>
                  <tree><group ref="product:missing"/></tree>
                </layout>
                """;
        Set<String> closed = new HashSet<>();
        LayoutXml.IncludeResolver resolver = (path, includingPath) -> {
            int depth = Integer.parseInt(path);
            String xml = depth < 64
                    ? "<areas><include path=\"" + (depth + 1) + "\"/></areas>"
                    : "<areas><area id=\"product:area\" displayName=\"#area\"/></areas>";
            return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public void close() throws IOException {
                    closed.add(path);
                    super.close();
                }
            };
        };

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LayoutXml.read(input(main.getBytes(StandardCharsets.UTF_8)), resolver));
        assertTrue(failure.getMessage(), failure.getMessage().contains("maximum include depth of 64"));
        assertEquals(64, closed.size());
    }

    @Test
    public void rejectsIncludesWithoutResolverAndCycles() {
        String main = """
                <layout id="product.default" version="1">
                  <areas><include path="side.xml"/></areas>
                  <tree><group ref="product:missing"/></tree>
                </layout>
                """;
        assertThrows(IOException.class, () -> LayoutXml.read(input(main.getBytes(StandardCharsets.UTF_8))));

        String loop = "<areas><include path=\"loop.xml\"/></areas>";
        assertFailure("""
                <layout id="product.default" version="1">
                  <areas><include path="loop.xml"/></areas>
                  <tree><group ref="product:missing"/></tree>
                </layout>
                """, "/layout/areas/include[1]/include[1]",
                (path, includingPath) -> input(loop.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void rejectsDuplicateIdsUnknownGrammarAndNestedLeaves() {
        assertFailure("""
                <layout id="bad" version="1" bogus="true">
                  <tree><group ref="product:g"/></tree>
                </layout>
                """, "/layout/@bogus");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas>
                    <area id="product:side" displayName="Side"/>
                    <area id="product:side" displayName="Side again"/>
                  </areas>
                  <tree><group ref="product:g"/></tree>
                </layout>
                """, "/layout/areas/area[2]");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side"/></areas>
                  <groups><group id="product:g" area="product:side"/></groups>
                  <tree><group ref="product:g"><mystery/></group></tree>
                </layout>
                """, "/layout/tree/group[1]/mystery");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas>
                    <area id="product:side" displayName="Side">
                      <style><stylesheet module="org.example" path="side.css"><mystery/></stylesheet></style>
                    </area>
                  </areas>
                  <tree><group ref="product:g"/></tree>
                </layout>
                """, "/layout/areas/area[1]/style/stylesheet[1]/mystery");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side"/></areas>
                  <groups><group id="product:g" area="product:side"/></groups>
                  <tree><group ref="product:g"/></tree>
                  <metadata><entry key="owner" value="one"><mystery/></entry></metadata>
                </layout>
                """, "/layout/metadata/entry[1]/mystery");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side"/></areas>
                  <tree><group ref="product:g"/></tree>
                </layout>
                """, "/layout/areas/area[1]");
        assertFailure("""
                <layout id="bad" version="1">
                  <tree><slot id="product:s" area="product:side"/></tree>
                </layout>
                """, "/layout/tree/slot[1]");
    }

    @Test
    public void reportsExactPathsForInvalidValuesReferencesAndDuplicates() {
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side" kind="NOPE"/></areas>
                  <tree><group ref="product:g"/></tree>
                </layout>
                """, "/layout/areas/area[1]");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side" weight="wide"/></areas>
                  <tree><group ref="product:g"/></tree>
                </layout>
                """, "/layout/areas/area[1]/@weight");
        assertFailure("""
                <layout id="bad" version="1">
                  <tree><group ref="product:missing"/></tree>
                </layout>
                """, "/layout/tree/group[1]");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side"/></areas>
                  <groups><group id="product:g" area="product:side"/></groups>
                  <tree>
                    <split orientation="horizontal" dividers="0.5">
                      <group ref="product:g"/>
                      <group ref="product:g"/>
                    </split>
                  </tree>
                </layout>
                """, "/layout/tree/split[1]/group[2]");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side"/></areas>
                  <tree>
                    <split orientation="horizontal" dividers="0.5">
                      <slot id="product:s" area="product:side"/>
                      <slot id="product:t" area="product:missing"/>
                    </split>
                  </tree>
                </layout>
                """, "/layout/tree/split[1]/slot[2]");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side"/></areas>
                  <groups><group id="product:g" area="product:side"/></groups>
                  <tree>
                    <split orientation="horizontal" dividers="0.5">
                      <slot id="product:s" area="product:side"/>
                      <slot id="product:t" area="product:side"/>
                    </split>
                  </tree>
                </layout>
                """, "/layout/groups/group[1]");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side"/></areas>
                  <tree>
                    <split orientation="horizontal" dividers="0.5">
                      <slot id="product:s" area="product:side"/>
                      <slot id="product:s" area="product:side"/>
                    </split>
                  </tree>
                </layout>
                """, "/layout/tree/split[1]/slot[2]");
        assertFailure("""
                <layout id="bad" version="1">
                  <areas><area id="product:side" displayName="Side"/></areas>
                  <groups><group id="product:g" area="product:side"/></groups>
                  <tree><group ref="product:g"/></tree>
                  <metadata>
                    <entry key="owner" value="one"/>
                    <entry key="owner" value="two"/>
                  </metadata>
                </layout>
                """, "/layout/metadata/entry[2]");
    }

    @Test
    public void rejectsMalformedUnsafeAndUnsupportedVersions() {
        assertFailure("""
                <layout id="bad" version="1">
                  <tree><group ref="product:missing"/></tree>
                </layout>
                """, "/layout/tree/group[1]");
        assertFailure("""
                <layout id="bad" version="999">
                  <tree><group ref="product:missing"/></tree>
                </layout>
                """, "/layout/@version");

        String doctype = "<!DOCTYPE layout [<!ENTITY x \"boom\">]>"
                + "<layout id=\"bad\" version=\"1\">&x;</layout>";
        IOException failure = assertThrows(IOException.class,
                () -> LayoutXml.read(input(doctype.getBytes(StandardCharsets.UTF_8))));
        assertTrue(failure.getMessage().contains("Invalid layout XML"));

        PaneRole area = PaneRole.of("product:editor");
        DockGroupId group = DockGroupId.of("product:editor.main");
        LayoutSpec future = new LayoutSpec("future", 2,
                Map.of(area, area(area, ViewAreaKind.MAIN, "Editor", 0)),
                Map.of(group, group(group, area)), new LayoutNode.Group(group), Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> LayoutXml.write(future, new ByteArrayOutputStream()));
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static ByteArrayInputStream input(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static void assertFailure(String xml, String path) {
        assertFailure(xml, path, (ignored, includingPath) -> {
            throw new IOException("Unexpected include");
        });
    }

    private static void assertFailure(String xml, String path, LayoutXml.IncludeResolver resolver) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LayoutXml.read(input(xml.getBytes(StandardCharsets.UTF_8)), resolver));
        assertTrue(failure.getMessage(), failure.getMessage().startsWith(path + ":"));
    }

    private static ViewArea area(PaneRole role, ViewAreaKind kind, String name, int order) {
        return new ViewArea(role, kind, name, DockLocation.CENTER, 1.0d, PresentationId.TABBED,
                Set.of(), "default", AreaStyle.EMPTY, ViewAreaVisibility.STARTUP,
                EmptyAreaPolicy.KEEP, order);
    }

    private static DockGroupSpec group(DockGroupId id, PaneRole area) {
        return new DockGroupSpec(id, area, PaneKind.VIEW, PresentationId.TABBED,
                ContentCapacity.MANY, "default", GroupLifetime.PERSISTENT, AreaStyle.EMPTY);
    }
}
