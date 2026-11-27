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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.api.nbfx.vcs.VcsFileState;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.LocalFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the status cache over a stand-in for git: what it answers before and after a repository
 * is read, when it reads again, and when it tells its listeners; and the state the annotator makes
 * of a status.
 */
public class GitStatusCacheTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private final AtomicReference<VcsStatus> onDisk = new AtomicReference<>(VcsStatus.CLEAN);
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger changes = new AtomicInteger();
    private volatile CountDownLatch read = new CountDownLatch(1);
    private GitStatusCache cache;
    private Path root;
    private Path source;

    @Before
    public void createWorkingTree() throws Exception {
        root = temp.newFolder("repo").toPath();
        Files.createDirectories(root.resolve(".git"));
        source = Files.createDirectories(root.resolve("src"));
        Files.writeString(source.resolve("Changed.java"), "class Changed {}");
        Files.writeString(source.resolve("Same.java"), "class Same {}");
        cache = new GitStatusCache(asked -> {
            assertEquals(root, asked);
            reads.incrementAndGet();
            VcsStatus status = onDisk.get();
            if (status == null) {
                throw new IOException("not a repository");
            }
            return status;
        });
        cache.addChangeListener(() -> {
            changes.incrementAndGet();
            read.countDown();
        });
    }

    @Test
    public void firstQuestionReadsTheRepositoryOnce() throws Exception {
        onDisk.set(status(modified("src/Changed.java")));

        assertEquals(Optional.empty(), cache.statusOf(source.resolve("Changed.java")));
        assertEquals(Optional.empty(), cache.statusOf(source.resolve("Same.java")));
        awaitRead();

        assertEquals(1, reads.get());
        assertEquals(1, changes.get());
        assertEquals(Optional.of(modified("src/Changed.java")), cache.statusOf(source.resolve("Changed.java")));
        assertEquals(Optional.empty(), cache.statusOf(source.resolve("Same.java")));
        assertEquals(1, reads.get());
    }

    @Test
    public void refreshReadsAgainAndTellsOnlyOfAChange() throws Exception {
        cache.statusOf(source.resolve("Changed.java"));
        awaitRead();
        assertEquals(1, changes.get());

        // Read again with the same result: nothing to repaint.
        cache.read(root);
        assertEquals(1, changes.get());

        onDisk.set(status(modified("src/Changed.java")));
        read = new CountDownLatch(1);
        cache.refresh(source);
        awaitRead();

        assertEquals(2, changes.get());
        assertTrue(cache.statusOf(source.resolve("Changed.java")).isPresent());
    }

    @Test
    public void foldersOutsideAWorkingTreeAreNeverRead() throws Exception {
        Path plain = temp.newFolder("plain").toPath();
        org.junit.Assume.assumeFalse(GitMenus.inWorkingTree(plain.toFile()));

        assertEquals(Optional.empty(), cache.statusOf(plain.resolve("file.txt")));
        cache.refresh(plain);
        // A repository nobody asked about is not read either.
        cache.refresh(source);
        Thread.sleep(600);

        assertEquals(0, reads.get());
        assertFalse(cache.isTracked(root));
    }

    @Test
    public void aFailedReadKeepsTheLastStatus() throws Exception {
        onDisk.set(status(modified("src/Changed.java")));
        cache.statusOf(source.resolve("Changed.java"));
        awaitRead();

        // Git busy, or its index locked: nothing says the changes are gone.
        onDisk.set(null);
        cache.read(root);

        assertTrue(cache.statusOf(source.resolve("Changed.java")).isPresent());
        assertEquals(1, changes.get());

        onDisk.set(VcsStatus.CLEAN);
        cache.read(root);

        assertEquals(Optional.empty(), cache.statusOf(source.resolve("Changed.java")));
        assertEquals(2, changes.get());
    }

    @Test
    public void aFolderNoLongerAWorkingTreeIsForgotten() throws Exception {
        onDisk.set(status(modified("src/Changed.java")));
        cache.statusOf(source.resolve("Changed.java"));
        awaitRead();

        Files.delete(root.resolve(".git"));
        onDisk.set(null);
        cache.read(root);

        assertEquals(2, changes.get());
        assertFalse(cache.isTracked(root));
        assertEquals(Optional.empty(), cache.statusOf(source.resolve("Changed.java")));
        // Not read again for a change in it, nor when the IDE comes back.
        int readSoFar = reads.get();
        cache.refresh(source);
        cache.refreshAll();
        Thread.sleep(900);
        assertEquals(readSoFar, reads.get());
    }

    @Test
    public void aFolderNotAskedAboutYetIsAnsweredFromTheRepositoryAboveIt() throws Exception {
        Path deep = Files.createDirectories(source.resolve("com/example"));
        onDisk.set(status(modified("src/com/example/Deep.java")));
        cache.conflictsWithin(root, true);
        awaitRead();

        // At once: the row of a folder just expanded is drawn with its state.
        assertEquals(Optional.of(false), cache.conflictsWithin(deep, false));
        assertEquals(1, reads.get());
    }

    @Test
    public void foldersHoldTheChangesRightInThemOrBelow() throws Exception {
        Path deep = Files.createDirectories(source.resolve("com/example"));
        Path other = Files.createDirectories(root.resolve("docs"));
        onDisk.set(status(modified("src/com/example/Deep.java"),
                new VcsFileStatus("src/com/example/Gone.java", null, VcsChangeKind.ADDED, VcsChangeKind.DELETED, false)));
        cache.conflictsWithin(root, true);
        awaitRead();

        assertEquals(Optional.of(false), cache.conflictsWithin(deep, false));
        assertEquals(Optional.of(false), cache.conflictsWithin(deep, true));
        // A package is its own files; a folder, and the repository, all below.
        assertEquals(Optional.empty(), cache.conflictsWithin(source, false));
        assertEquals(Optional.of(false), cache.conflictsWithin(source, true));
        assertEquals(Optional.of(false), cache.conflictsWithin(root, true));
        assertEquals(Optional.empty(), cache.conflictsWithin(other, true));

        onDisk.set(status(modified("src/com/example/Deep.java"),
                new VcsFileStatus("src/Clash.java", null, null, null, true)));
        cache.read(root);

        assertEquals(Optional.of(false), cache.conflictsWithin(deep, true));
        assertEquals(Optional.of(true), cache.conflictsWithin(source, false));
        assertEquals(Optional.of(true), cache.conflictsWithin(root, true));
    }

    @Test
    public void annotatorBadgesFoldersNotFiles() throws Exception {
        onDisk.set(status(modified("src/Changed.java")));
        GitAnnotator annotator = new GitAnnotator(cache);
        LocalFileSystem fileSystem = new LocalFileSystem();
        fileSystem.setRootDirectory(root.toFile());
        annotator.stateWithin(fileSystem.getRoot(), true);
        awaitRead();

        assertEquals(Optional.of(VcsFileState.MODIFIED), annotator.stateWithin(fileSystem.getRoot(), true));
        assertEquals(Optional.empty(), annotator.stateWithin(fileSystem.getRoot(), false));
        assertEquals(Optional.of(VcsFileState.MODIFIED), annotator.stateWithin(fileSystem.findResource("src"), false));
        assertEquals(Optional.empty(), annotator.stateWithin(fileSystem.findResource("src/Changed.java"), true));
        assertEquals(Optional.empty(), annotator.stateWithin(null, true));
    }

    @Test
    public void annotatorTellsTheStateSinceTheLastCommit() throws Exception {
        assertEquals(Optional.of(VcsFileState.MODIFIED), GitAnnotator.stateOf(modified("a")));
        assertEquals(Optional.of(VcsFileState.ADDED),
                GitAnnotator.stateOf(new VcsFileStatus("a", null, null, VcsChangeKind.UNTRACKED, false)));
        assertEquals(Optional.of(VcsFileState.ADDED),
                GitAnnotator.stateOf(new VcsFileStatus("a", null, VcsChangeKind.ADDED, VcsChangeKind.MODIFIED, false)));
        assertEquals(Optional.of(VcsFileState.DELETED),
                GitAnnotator.stateOf(new VcsFileStatus("a", null, VcsChangeKind.DELETED, null, false)));
        assertEquals(Optional.of(VcsFileState.CONFLICTED),
                GitAnnotator.stateOf(new VcsFileStatus("a", null, null, null, true)));
        // Added and then deleted again: as if never there.
        assertEquals(Optional.empty(),
                GitAnnotator.stateOf(new VcsFileStatus("a", null, VcsChangeKind.ADDED, VcsChangeKind.DELETED, false)));
    }

    @Test
    public void annotatorAnswersForFilesNotFolders() throws Exception {
        onDisk.set(status(modified("src/Changed.java")));
        GitAnnotator annotator = new GitAnnotator(cache);
        LocalFileSystem fileSystem = new LocalFileSystem();
        fileSystem.setRootDirectory(root.toFile());
        FileObject changed = fileSystem.findResource("src/Changed.java");

        annotator.stateOf(changed);
        awaitRead();

        assertEquals(Optional.of(VcsFileState.MODIFIED), annotator.stateOf(changed));
        assertEquals(Optional.empty(), annotator.stateOf(fileSystem.findResource("src/Same.java")));
        assertEquals(Optional.empty(), annotator.stateOf(fileSystem.findResource("src")));
        assertEquals(Optional.empty(), annotator.stateOf((FileObject) null));
    }

    private void awaitRead() throws InterruptedException {
        assertTrue("the repository was not read", read.await(10, TimeUnit.SECONDS));
    }

    private static VcsStatus status(VcsFileStatus... files) {
        return new VcsStatus(List.of(files));
    }

    private static VcsFileStatus modified(String path) {
        return new VcsFileStatus(path, null, null, VcsChangeKind.MODIFIED, false);
    }
}
