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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Change;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Commit;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.CommitRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.Field;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.FileRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.MoreRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.Row;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the rows of the History summary: what an opened commit shows, and what the filter leaves. */
public class HistoryRowsTest {

    private static final Change README = new Change(VcsChangeKind.MODIFIED, "README.md", null);
    private static final Change MOVED = new Change(VcsChangeKind.RENAMED, "src/app/New.java", "src/app/Old.java");
    private static final Commit FIX = commit("1111111aaaa", "Ada Lovelace", "ada@example.invalid",
            "Fix the parser\n\nIt read one line too many.", README);
    private static final Commit MOVE = commit("2222222bbbb", "Grace Hopper", "grace@example.invalid",
            "Move the class", MOVED, README);

    @Test
    public void anOpenedCommitIsFollowedByItsFiles() {
        List<Row> rows = HistoryRows.of(List.of(FIX, MOVE), Set.of(MOVE.id()), Commit::changes, Field.ALL, null, false);

        assertEquals(List.of(new CommitRow(FIX, false), new CommitRow(MOVE, true), new FileRow(MOVE, MOVED),
                new FileRow(MOVE, README)), rows);
    }

    @Test
    public void theFilesShownAreThoseAskedFor() {
        // All the files of a commit, in place of those the search listed.
        List<Row> rows = HistoryRows.of(List.of(FIX), Set.of(FIX.id()), commit -> List.of(README, MOVED), Field.ALL,
                " ", false);

        assertEquals(List.of(new CommitRow(FIX, true), new FileRow(FIX, README), new FileRow(FIX, MOVED)), rows);
    }

    @Test
    public void aSearchWithMoreEndsWithTheRowThatAsksForIt() {
        List<Row> rows = HistoryRows.of(List.of(FIX), Set.of(), Commit::changes, Field.ALL, "", true);

        assertEquals(List.of(new CommitRow(FIX, false), new MoreRow()), rows);
        assertEquals(List.of(), HistoryRows.of(List.of(), Set.of(), Commit::changes, Field.ALL, "", false));
    }

    @Test
    public void theFilterLooksInTheFieldChosenWhateverTheCase() {
        assertEquals(List.of(FIX), filtered(Field.MESSAGE, "one LINE"));
        assertEquals(List.of(), filtered(Field.MESSAGE, "ada"));
        assertEquals(List.of(FIX), filtered(Field.AUTHOR, "ada"));
        assertEquals(List.of(MOVE), filtered(Field.AUTHOR, "GRACE@example"));
        assertEquals(List.of(MOVE), filtered(Field.COMMIT, "2222"));
        assertEquals(List.of(FIX, MOVE), filtered(Field.FILE, "readme"));
        // Where a moved file was counts as well as where it is.
        assertEquals(List.of(MOVE), filtered(Field.FILE, "Old.java"));
        assertEquals(List.of(MOVE), filtered(Field.ALL, "old.java"));
        assertEquals(List.of(FIX), filtered(Field.ALL, "parser"));
        assertEquals(List.of(FIX, MOVE), filtered(Field.ALL, "example.invalid"));
        assertEquals(List.of(), filtered(Field.ALL, "nothing has this"));
    }

    @Test
    public void theFilterSeesTheFilesThatAreShown() {
        assertFalse(HistoryRows.matches(FIX, FIX.changes(), Field.FILE, "New.java"));
        assertTrue(HistoryRows.matches(FIX, List.of(README, MOVED), Field.FILE, "New.java"));
    }

    private static List<Commit> filtered(Field field, String contains) {
        return HistoryRows.of(List.of(FIX, MOVE), Set.of(), Commit::changes, field, contains, false).stream()
                .map(row -> ((CommitRow) row).commit()).toList();
    }

    private static Commit commit(String id, String author, String email, String message, Change... changes) {
        return new Commit(id, List.of(), author, email, Instant.ofEpochSecond(1_770_000_000L), message,
                List.of(changes));
    }
}
