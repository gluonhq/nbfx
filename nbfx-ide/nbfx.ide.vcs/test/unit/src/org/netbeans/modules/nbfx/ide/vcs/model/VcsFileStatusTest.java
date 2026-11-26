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
package org.netbeans.modules.nbfx.ide.vcs.model;

import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.ADDED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.CONFLICTED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.COPIED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.DELETED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.MODIFIED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.RENAMED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.TYPE_CHANGED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind.UNTRACKED;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode.HEAD_VS_INDEX;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode.HEAD_VS_WORKING_TREE;
import static org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode.INDEX_VS_WORKING_TREE;

/** What each compare mode shows for a file, from its staged and its unstaged change. */
public class VcsFileStatusTest {

    @Test
    public void stagedChangeShowsAgainstTheIndexOnly() {
        VcsFileStatus file = file(MODIFIED, null);
        assertModes(file, MODIFIED, MODIFIED, null);
    }

    @Test
    public void unstagedChangeShowsAgainstTheWorkingTreeOnly() {
        VcsFileStatus file = file(null, DELETED);
        assertModes(file, DELETED, null, DELETED);
    }

    @Test
    public void untrackedFileIsNotInTheIndex() {
        assertModes(file(null, UNTRACKED), UNTRACKED, null, UNTRACKED);
    }

    @Test
    public void stagedAndUnstagedChangesCombineAgainstTheLastCommit() {
        assertModes(file(MODIFIED, MODIFIED), MODIFIED, MODIFIED, MODIFIED);
        assertModes(file(MODIFIED, DELETED), DELETED, MODIFIED, DELETED);
        assertModes(file(ADDED, MODIFIED), ADDED, ADDED, MODIFIED);
        assertModes(file(RENAMED, MODIFIED), RENAMED, RENAMED, MODIFIED);
        assertModes(file(MODIFIED, TYPE_CHANGED), TYPE_CHANGED, MODIFIED, TYPE_CHANGED);
        assertModes(file(TYPE_CHANGED, MODIFIED), TYPE_CHANGED, TYPE_CHANGED, MODIFIED);
    }

    @Test
    public void fileAddedThenDeletedIsUnchangedAgainstTheLastCommit() {
        // Never committed and no longer on disk: both sides lack it.
        assertModes(file(ADDED, DELETED), null, ADDED, DELETED);
        assertModes(file(COPIED, DELETED), null, COPIED, DELETED);
    }

    @Test
    public void renamedThenDeletedIsDeletedAgainstTheLastCommit() {
        assertModes(file(RENAMED, DELETED), DELETED, RENAMED, DELETED);
    }

    @Test
    public void whatIsDeletedOfAFileRenamedAndGoneIsItsOriginal() {
        VcsFileStatus moved = new VcsFileStatus("new.txt", "old.txt", RENAMED, DELETED, false);
        // The last commit has old.txt, the disk neither: old.txt is what is deleted.
        assertEquals("old.txt", moved.path(HEAD_VS_WORKING_TREE));
        assertEquals("new.txt", moved.path(HEAD_VS_INDEX));
        assertEquals("new.txt", moved.path(INDEX_VS_WORKING_TREE));

        // A rename that stays is of the path it has now, in every mode.
        VcsFileStatus kept = new VcsFileStatus("new.txt", "old.txt", RENAMED, null, false);
        assertEquals("new.txt", kept.path(HEAD_VS_WORKING_TREE));
        assertEquals("a.txt", file(MODIFIED, DELETED).path(HEAD_VS_WORKING_TREE));
    }

    @Test
    public void fileDeletedFromTheIndexAndBackOnDiskIsModified() {
        assertModes(file(DELETED, UNTRACKED), MODIFIED, DELETED, UNTRACKED);
    }

    @Test
    public void conflictShowsInEveryMode() {
        VcsFileStatus file = new VcsFileStatus("a.txt", null, null, null, true);
        assertModes(file, CONFLICTED, CONFLICTED, CONFLICTED);
    }

    @Test
    public void rejectsStatesThatAreNotChanges() {
        assertThrows(IllegalArgumentException.class, () -> file(null, null));
        assertThrows(IllegalArgumentException.class, () -> file(UNTRACKED, null));
        assertThrows(IllegalArgumentException.class, () -> file(CONFLICTED, null));
        assertThrows(IllegalArgumentException.class, () -> file(null, CONFLICTED));
        assertThrows(NullPointerException.class, () -> new VcsFileStatus(null, null, MODIFIED, null, false));
    }

    @Test
    public void statusListsTheFilesOfAMode() {
        VcsFileStatus staged = new VcsFileStatus("a.txt", null, MODIFIED, null, false);
        VcsFileStatus unstaged = new VcsFileStatus("b.txt", null, null, MODIFIED, false);
        VcsFileStatus addedAndGone = new VcsFileStatus("c.txt", null, ADDED, DELETED, false);
        VcsStatus status = new VcsStatus(List.of(staged, unstaged, addedAndGone));

        assertEquals(List.of(staged, unstaged), status.files(HEAD_VS_WORKING_TREE));
        assertEquals(List.of(staged, addedAndGone), status.files(HEAD_VS_INDEX));
        assertEquals(List.of(unstaged, addedAndGone), status.files(INDEX_VS_WORKING_TREE));
        assertFalse(status.isClean());
        assertTrue(VcsStatus.CLEAN.isClean());
        assertTrue(VcsStatus.CLEAN.files(HEAD_VS_WORKING_TREE).isEmpty());
    }

    private static VcsFileStatus file(VcsChangeKind staged, VcsChangeKind unstaged) {
        return new VcsFileStatus("a.txt", null, staged, unstaged, false);
    }

    private static void assertModes(VcsFileStatus file, VcsChangeKind headVsWorkingTree,
            VcsChangeKind headVsIndex, VcsChangeKind indexVsWorkingTree) {
        assertEquals("HEAD vs working tree", Optional.ofNullable(headVsWorkingTree),
                file.change(HEAD_VS_WORKING_TREE));
        assertEquals("HEAD vs index", Optional.ofNullable(headVsIndex), file.change(HEAD_VS_INDEX));
        assertEquals("index vs working tree", Optional.ofNullable(indexVsWorkingTree),
                file.change(INDEX_VS_WORKING_TREE));
    }
}
