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
package org.netbeans.modules.nbfx.ide.vcs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.netbeans.modules.nbfx.ide.vcs.GitHistoryView.Scope;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies what Show History opens: the scope it makes of a file, a folder or a project, the
 * document of its tab, and what a later session opens it again from.
 */
public class GitHistoryViewTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path root;
    private File source;

    @Before
    public void createWorkingTree() throws Exception {
        root = temp.newFolder("repo").toPath();
        // Told from its .git entry, as the menus do: no git is run.
        Files.createDirectories(root.resolve(".git"));
        source = Files.createDirectories(root.resolve("ui/src")).resolve("App.java").toFile();
        Files.writeString(source.toPath(), "class App {}");
    }

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void theScopeIsTheWorkingTreeAndWhereTheFileIsInIt() {
        assertEquals(new Scope(root, "ui/src/App.java", "App.java"), GitHistoryView.scopeOf(source, null));
        // A project is called as the IDE calls it.
        Scope project = GitHistoryView.scopeOf(root.resolve("ui").toFile(), "My UI");
        assertEquals(new Scope(root, "ui", "My UI"), project);
        assertEquals(List.of("ui"), project.paths());

        // The working tree itself: the whole repository, no path to search.
        Scope whole = GitHistoryView.scopeOf(root.toFile(), null);
        assertEquals(new Scope(root, "", "repo"), whole);
        assertEquals(List.of(), whole.paths());
    }

    @Test
    public void aFileInNoWorkingTreeHasNoHistory() throws Exception {
        File plain = Files.createTempDirectory("nbfx-no-repository").toFile();
        try {
            org.junit.Assume.assumeFalse(GitMenus.inWorkingTree(plain));

            assertNull(GitHistoryView.scopeOf(plain, null));
            assertNull(GitHistoryView.scopeOf(null, "none"));
            // Opening nothing is nothing, not a failure.
            GitHistoryView.open(null);
        } finally {
            Files.delete(plain.toPath());
        }
    }

    @Test
    public void aScopeIsReadBackFromWhatItWasSavedAs() {
        Scope scope = new Scope(root, "ui/src/App.java", "App.java");

        assertEquals(scope, Scope.ofSaved(scope.saved()));
        Scope whole = new Scope(root, "", "repo");
        assertEquals(whole, Scope.ofSaved(whole.saved()));
        assertNull(Scope.ofSaved(null));
        assertNull(Scope.ofSaved(""));
        assertNull(Scope.ofSaved("only a root"));
        assertNull(Scope.ofSaved("\nui\nname"));
        assertNull(Scope.ofSaved(root + "\nui\n"));

        // The same history, whatever it is called; another one, elsewhere.
        assertTrue(scope.sameAs(new Scope(root, "ui/src/App.java", "other name")));
        assertFalse(scope.sameAs(whole));
    }

    @Test
    public void theDocumentIsATabWithNoFileAndNothingToSave() throws Exception {
        Scope scope = GitHistoryView.scopeOf(root.resolve("ui").toFile(), "My UI");

        GitHistoryDocument document = JavaFxTestSupport.onFx(() -> GitHistoryView.document(scope));

        assertEquals("Show History - My UI", document.getTitle());
        assertNull(document.getFileObject());
        assertSame(document.pane(), document.getNode());
        assertFalse(document.isModified());
        assertFalse(document.editableProperty().get());
        assertFalse(document.canUndoProperty().get());
        assertNull(document.caretInfoProperty().getValue());
        assertEquals(scope, document.scope());
        // The pane searches the path of the scope.
        assertEquals(List.of("ui"), JavaFxTestSupport.onFx(() -> document.pane().criteria().paths()));
        // Opened again with the session, from its scope.
        assertEquals(new SavedDocument("git-history", scope.saved()), document.getSavedState());
        assertEquals("git-history", new GitHistoryView.Restorer().id());
    }

    @Test
    public void restoringWhatIsNoWorkingTreeAnyMoreDoesNothing() throws Exception {
        Scope scope = new Scope(root, "ui", "My UI");
        Files.delete(root.resolve(".git"));

        // Neither does a state that cannot be read; with no editor area, as here, nothing opens either way.
        JavaFxTestSupport.onFx(() -> {
            new GitHistoryView.Restorer().restore(scope.saved());
            new GitHistoryView.Restorer().restore("not a scope");
        });
    }
}
