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
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.LocalFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the menus of a folder outside and inside a Git working tree, and that initializing a
 * repository moves it from one to the other. The repository tests are skipped where git is not
 * installed.
 */
public class GitMenusTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void folderWithoutRepositoryOffersToInitializeOne() throws Exception {
        File project = plainFolder();
        FileObject folder = fileObject(project);

        assertFalse(GitMenus.inWorkingTree(folder));
        Menu menu = contextMenu(folder);
        assertEquals("Versioning", menu.getText());
        assertEquals(List.of("Initialize Git Repository..."), texts(menu));
        assertEquals(List.of("Versioning"), JavaFxTestSupport.onFx(() ->
                new GitMenuContributor().itemsForFolders(List.of(folder)).stream().map(MenuItem::getText).toList()));
    }

    @Test
    public void initializingMakesTheRepositoryAndTheGitMenu() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        File project = plainFolder();
        Files.createDirectories(project.toPath().resolve("src"));
        FileObject source = fileObject(new File(project, "src"));

        assertEquals(0, GitClient.init(project.toPath()).exitCode());

        assertTrue(Files.isDirectory(project.toPath().resolve(".git")));
        assertTrue(GitClient.isRepository(project.toPath()));
        assertTrue(GitMenus.inWorkingTree(source));
        Menu menu = contextMenu(source);
        assertEquals("Git", menu.getText());
        assertEquals(List.of("Show Changes"), texts(menu));
    }

    @Test
    public void severalFoldersWithoutRepositoryGetNoMenu() throws Exception {
        File project = plainFolder();
        Files.createDirectories(project.toPath().resolve("a"));
        Files.createDirectories(project.toPath().resolve("b"));
        List<FileObject> folders = List.of(fileObject(new File(project, "a")),
                fileObject(new File(project, "b")));

        assertTrue(JavaFxTestSupport.onFx(() -> new GitMenuContributor().itemsForFolders(folders)).isEmpty());
    }

    @Test
    public void teamMenuOffersToInitializeWithoutARepository() throws Exception {
        // No project is selected here, so there is no repository to show the changes of.
        List<MenuItem> items = JavaFxTestSupport.onFx(() -> {
            GitTeamMenuFactory.TeamMenu menu = new GitTeamMenuFactory.TeamMenu();
            List<MenuItem> created = List.copyOf(menu.items());
            menu.dispose();
            return created;
        });

        assertEquals(List.of("Show Changes", "Repository", "Git"), items.stream().map(MenuItem::getText).toList());
        assertFalse(items.get(0).isVisible());
        assertFalse(items.get(1).isVisible());
        assertEquals(List.of("Repository Browser"), texts((Menu) items.get(1)));
        assertTrue(items.get(2).isVisible());
        assertEquals(List.of("Initialize Repository..."), texts((Menu) items.get(2)));
    }

    @Test
    public void proposedRootIsTheFolderOfTheFileWithoutAProject() throws Exception {
        File project = plainFolder();
        File file = new File(project, "readme.txt");
        Files.writeString(file.toPath(), "text");

        Path proposed = GitMenus.proposedRoot(fileObject(file), null);

        assertNotNull(proposed);
        assertTrue(Files.isSameFile(project.toPath(), proposed));
        assertNull(GitMenus.proposedRoot(null, null));
        assertNotNull(GitInitDialog.folder(project.getAbsolutePath()));
        assertNull(GitInitDialog.folder(file.getAbsolutePath()));
        assertNull(GitInitDialog.folder("  "));
    }

    @Test
    public void aFileOutsideItsProjectIsNotGivenTheFolderOfThatProject() throws Exception {
        File project = plainFolder();
        File elsewhere = temp.newFolder("elsewhere");
        File inside = new File(project, "readme.txt");
        File outside = new File(elsewhere, "notes.txt");
        Files.writeString(inside.toPath(), "text");
        Files.writeString(outside.toPath(), "text");

        // In the project: its folder. Outside it: the file's own, not that of a project it is no part of.
        assertEquals(project.toPath(), GitMenus.proposedRoot(fileObject(inside), project.toPath()));
        assertEquals(elsewhere.toPath(), GitMenus.proposedRoot(fileObject(outside), project.toPath()));
    }

    @Test
    public void showChangesIsOfTheProjectOfTheFileOrOfItsWorkingTree() throws Exception {
        File repository = plainFolder();
        File project = new File(repository, "app");
        File loose = new File(repository, "docs");
        Files.createDirectories(project.toPath().resolve("src"));
        Files.createDirectories(loose.toPath());
        Files.createDirectories(repository.toPath().resolve(".git"));
        File source = new File(project, "src/App.java");
        File page = new File(loose, "index.md");
        Files.writeString(source.toPath(), "class App {}");
        Files.writeString(page.toPath(), "text");

        assertEquals(repository.toPath(), GitMenus.workingTreeOf(page));
        // A file of the project: the changes of the project.
        assertEquals(project.toPath(), GitMenus.changesFolder(fileObject(source), project.toPath()));
        // A file of no project, and one outside the project it is said to be of: those of its working tree.
        assertEquals(repository.toPath(), GitMenus.changesFolder(fileObject(page), null));
        assertEquals(repository.toPath(), GitMenus.changesFolder(fileObject(page), project.toPath()));
        // Nothing to go by: nothing, not what was shown before.
        assertNull(GitMenus.changesFolder(null, null));
        assertNull(GitMenus.workingTreeOf(temp.getRoot().getParentFile() == null ? null : new File("/")));
    }

    private File plainFolder() throws Exception {
        File project = temp.newFolder("project");
        Assume.assumeFalse("the temporary folder is inside a Git working tree", GitMenus.inWorkingTree(project));
        return project;
    }

    /** The file as a file object of a local filesystem: the master filesystem is not on the test path. */
    private FileObject fileObject(File file) throws Exception {
        LocalFileSystem fileSystem = new LocalFileSystem();
        fileSystem.setRootDirectory(temp.getRoot());
        FileObject found = fileSystem.findResource(
                temp.getRoot().toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/'));
        assertNotNull(file.toString(), found);
        return found;
    }

    private static Menu contextMenu(FileObject file) throws Exception {
        List<MenuItem> items = JavaFxTestSupport.onFx(() -> new GitMenuContributor().itemsFor(file));
        assertEquals(1, items.size());
        return (Menu) items.getFirst();
    }

    private static List<String> texts(Menu menu) {
        return menu.getItems().stream().map(MenuItem::getText).toList();
    }

    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (java.io.IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
