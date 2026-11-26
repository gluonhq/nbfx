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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** How the changed files are arranged as rows: as a list, and under their folders. */
public class StatusTreeTest {

    private static final VcsStatus STATUS = new VcsStatus(List.of(
            unstaged("build.xml", VcsChangeKind.MODIFIED),
            unstaged("module/nbproject/project.xml", VcsChangeKind.MODIFIED),
            unstaged("module/src/com/example/app/App.java", VcsChangeKind.DELETED),
            new VcsFileStatus("module/src/com/example/app/New.java", null, VcsChangeKind.ADDED, null, false),
            unstaged("module/src/com/example/Util.java", VcsChangeKind.UNTRACKED),
            unstaged("z.txt", VcsChangeKind.MODIFIED)));

    @Test
    public void listShowsEveryFileOfTheModeWithItsNameAndKind() {
        List<StatusTree.Node> rows = StatusTree.list(STATUS, VcsCompareMode.HEAD_VS_WORKING_TREE);

        assertEquals(List.of(
                "build.xml [MODIFIED]",
                "project.xml [MODIFIED]",
                "Util.java [UNTRACKED]",
                "App.java [DELETED]",
                "New.java [ADDED]",
                "z.txt [MODIFIED]"), outline(rows));
        // In path order, whatever order the status holds them in.
        assertEquals("module/src/com/example/app/App.java", rows.get(3).row().path());
    }

    @Test
    public void treePutsFoldersFirstAndJoinsChainsOfSingleFolders() {
        List<StatusTree.Node> rows = StatusTree.tree(STATUS, VcsCompareMode.HEAD_VS_WORKING_TREE);

        assertEquals(List.of(
                "module/",
                "  nbproject/",
                "    project.xml [MODIFIED]",
                "  src/com/example/",
                "    app/",
                "      App.java [DELETED]",
                "      New.java [ADDED]",
                "    Util.java [UNTRACKED]",
                "build.xml [MODIFIED]",
                "z.txt [MODIFIED]"), outline(rows));
        StatusTree.Node joined = rows.get(0).children().get(1);
        assertEquals("module/src/com/example", joined.row().path());
    }

    @Test
    public void treeHoldsOnlyTheFilesOfTheMode() {
        assertEquals(List.of(
                "module/src/com/example/app/",
                "  New.java [ADDED]"), outline(StatusTree.tree(STATUS, VcsCompareMode.HEAD_VS_INDEX)));
        assertTrue(StatusTree.tree(VcsStatus.CLEAN, VcsCompareMode.HEAD_VS_INDEX).isEmpty());
        assertTrue(StatusTree.list(VcsStatus.CLEAN, VcsCompareMode.HEAD_VS_INDEX).isEmpty());
    }

    @Test
    public void aFileRenamedAndThenDeletedIsARowWhereItWas() {
        VcsStatus status = new VcsStatus(List.of(new VcsFileStatus("new/Moved.java", "old/Original.java",
                VcsChangeKind.RENAMED, VcsChangeKind.DELETED, false)));

        // The last commit has it in old/ and the disk nowhere: that is the file deleted.
        List<StatusTree.Node> rows = StatusTree.tree(status, VcsCompareMode.HEAD_VS_WORKING_TREE);
        assertEquals(List.of("old/", "  Original.java [DELETED]"), outline(rows));
        assertEquals("old/Original.java", rows.get(0).children().get(0).row().path());
        // The index has it in new/.
        assertEquals(List.of("new/", "  Moved.java [RENAMED]"),
                outline(StatusTree.tree(status, VcsCompareMode.HEAD_VS_INDEX)));
        assertEquals("new/Moved.java",
                StatusTree.list(status, VcsCompareMode.INDEX_VS_WORKING_TREE).get(0).row().path());
    }

    private static VcsFileStatus unstaged(String path, VcsChangeKind kind) {
        return new VcsFileStatus(path, null, null, kind, false);
    }

    private static List<String> outline(List<StatusTree.Node> nodes) {
        List<String> lines = new ArrayList<>();
        outline(nodes, "", lines);
        return lines;
    }

    private static void outline(List<StatusTree.Node> nodes, String indent, List<String> lines) {
        for (StatusTree.Node node : nodes) {
            lines.add(indent + switch (node.row()) {
                case StatusRow.Folder folder -> folder.name() + "/";
                case StatusRow.File file -> file.name() + " [" + file.kind() + "]";
            });
            outline(node.children(), indent + "  ", lines);
        }
    }
}
