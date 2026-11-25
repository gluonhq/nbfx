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
package org.netbeans.api.nbfx.actions;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Verifies that action references are read from a layer folder and ordered by position then id, and
 * that malformed or unrelated entries are ignored.
 *
 * @since 1.0
 */
public class ActionLayerReaderTest {

    @Test
    public void readsAndOrdersReferences() throws IOException {
        FileObject menu = menuFolder();

        addRef(menu, "run", "run", 30, false);
        addRef(menu, "save", "save", 10, false);
        addRef(menu, "saveAs", "save-as", 20, true);
        // Unrelated data file without a .ref extension.
        menu.createData("notes", "txt");
        // A .ref file without an actionId is ignored.
        menu.createData("broken", "ref");

        List<ActionRef> refs = ActionLayerReader.read(menu);

        assertEquals(List.of("save", "save-as", "run"),
                refs.stream().map(ActionRef::actionId).toList());
        assertFalse(refs.get(0).separatorBefore());
        assertTrue(refs.get(1).separatorBefore());
    }

    @Test
    public void breaksPositionTiesById() throws IOException {
        FileObject menu = menuFolder();
        addRef(menu, "z", "z", 1, false);
        addRef(menu, "a", "a", 1, false);

        List<ActionRef> refs = ActionLayerReader.read(menu);

        assertEquals(List.of("a", "z"), refs.stream().map(ActionRef::actionId).toList());
    }

    @Test
    public void nullFolderYieldsEmptyList() {
        assertEquals(List.of(), ActionLayerReader.read((FileObject) null));
        assertEquals(List.of(), ActionLayerReader.readSubmenus((FileObject) null));
    }

    @Test
    public void readsNestedSubmenuFolders() throws IOException {
        FileObject menu = menuFolder();
        FileObject ideTools = FileUtil.createFolder(menu, "IDE Tools");
        ideTools.setAttribute("position", 40);
        addRef(ideTools, "properties", "select-properties", 10, false);
        FileObject help = FileUtil.createFolder(menu, "Help");
        help.setAttribute("position", 10);
        FileObject documentation = FileUtil.createFolder(help, "Documentation");
        documentation.setAttribute("position", 10);
        addRef(documentation, "guide", "show-guide", 10, false);
        // Empty folders are not submenus.
        FileUtil.createFolder(menu, "Empty");

        List<ActionLayerReader.Submenu> submenus = ActionLayerReader.readSubmenus(menu);

        assertEquals(2, submenus.size());
        assertEquals("Help", submenus.get(0).displayName());
        assertEquals("IDE Tools", submenus.get(1).displayName());
        assertEquals(40, submenus.get(1).position());
    }

    @Test
    public void submenuTakesDisplayNameOverrideFromItsAttribute() throws IOException {
        FileObject menu = menuFolder();
        FileObject ideTools = FileUtil.createFolder(menu, "IDE Tools");
        ideTools.setAttribute("displayName", "IDE Tools & Properties");
        addRef(ideTools, "properties", "select-properties", 10, false);

        List<ActionLayerReader.Submenu> submenus = ActionLayerReader.readSubmenus(menu);

        assertEquals("IDE Tools & Properties", submenus.get(0).displayName());
    }

    @Test
    public void filtersReferencesByMimeType() throws IOException {
        FileObject menu = menuFolder();
        addRef(menu, "any", "any", 10, false);
        addRefWithMime(menu, "java", "java-only", 20, false, "text/x-java");
        addRefWithMime(menu, "xml", "xml-only", 30, false, "text/x-xml");

        List<ActionRef> java = ActionLayerReader.readForMime(menu, "text/x-java");
        assertEquals(List.of("any", "java-only"), java.stream().map(ActionRef::actionId).toList());

        List<ActionRef> other = ActionLayerReader.readForMime(menu, "text/plain");
        assertEquals(List.of("any"), other.stream().map(ActionRef::actionId).toList());

        List<ActionRef> noMime = ActionLayerReader.readForMime(menu, null);
        assertEquals(List.of("any"), noMime.stream().map(ActionRef::actionId).toList());
    }

    @Test
    public void filtersByContextMimeLanguageAndProfileAndRetainsMetadata() throws IOException {
        FileObject menu = menuFolder();
        addRef(menu, "any", "any", 10, false);
        addFilteredRef(menu, "match", "match", 20);
        addFilteredRef(menu, "other-profile", "other-profile", 30);
        FileObject otherProfile = menu.getFileObject("other-profile", "ref");
        otherProfile.setAttribute("profiles", "compact");
        Lookup context = Lookups.fixed(new ActionContextMetadata(
                "editor", "text/x-java", "java", "main"));

        List<ActionRef> refs = ActionLayerReader.readForContext(menu, context);
        assertEquals(List.of("any", "match"), refs.stream().map(ActionRef::actionId).toList());
        ActionRef match = refs.get(1);
        assertEquals("Custom Save", match.displayName());
        assertEquals("save.png", match.iconName());
        assertEquals("editor", match.contextType());
        assertEquals("java", match.languageId());
        assertEquals("text/x-java", match.mimeType());
        assertEquals(Set.of("main"), match.profiles());
        assertEquals(Map.of("iconOnly", "true", "slot", "primary"), match.presentationHints());
        assertThrows(UnsupportedOperationException.class, () -> match.profiles().add("compact"));
        assertThrows(UnsupportedOperationException.class,
                () -> match.presentationHints().put("new", "value"));
    }

    @Test
    public void wrongTypedAttributesAreMalformedRatherThanPermissive() throws IOException {
        FileObject menu = menuFolder();
        FileObject bad = menu.createData("bad", "ref");
        bad.setAttribute("actionId", "bad");
        bad.setAttribute("position", "10");
        assertTrue(ActionLayerReader.read(menu).isEmpty());

        FileObject badProfiles = menu.createData("profiles", "ref");
        badProfiles.setAttribute("actionId", "profiles");
        badProfiles.setAttribute("profiles", 123);
        assertTrue(ActionLayerReader.read(menu).isEmpty());

        FileObject badFilter = menu.createData("filter", "ref");
        badFilter.setAttribute("actionId", "filter");
        badFilter.setAttribute("mimeType", "text/x-java, text/plain");
        assertTrue(ActionLayerReader.read(menu).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new ActionContextMetadata("editor", "bad mime", "java", "main"));
    }

    private static void addFilteredRef(FileObject folder, String name, String actionId, int position)
            throws IOException {
        FileObject file = folder.createData(name, "ref");
        file.setAttribute("actionId", actionId);
        file.setAttribute("position", position);
        file.setAttribute("displayName", "Custom Save");
        file.setAttribute("iconName", "save.png");
        file.setAttribute("contextType", "editor");
        file.setAttribute("languageId", "java");
        file.setAttribute("mimeType", "text/x-java");
        file.setAttribute("profiles", "main");
        file.setAttribute("hint.iconOnly", "true");
        file.setAttribute("hint.slot", "primary");
    }

    private static FileObject menuFolder() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return FileUtil.createFolder(fs.getRoot(), "NbFx/Menus/File");
    }

    private static void addRef(FileObject folder, String name, String actionId, int position,
            boolean separatorBefore) throws IOException {
        FileObject file = folder.createData(name, "ref");
        file.setAttribute("actionId", actionId);
        file.setAttribute("position", position);
        file.setAttribute("separatorBefore", separatorBefore);
    }

    private static void addRefWithMime(FileObject folder, String name, String actionId, int position,
            boolean separatorBefore, String mimeType) throws IOException {
        FileObject file = folder.createData(name, "ref");
        file.setAttribute("actionId", actionId);
        file.setAttribute("position", position);
        file.setAttribute("separatorBefore", separatorBefore);
        file.setAttribute("mimeType", mimeType);
    }
}
