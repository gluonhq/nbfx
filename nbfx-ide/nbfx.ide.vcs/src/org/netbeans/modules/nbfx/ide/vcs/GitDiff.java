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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Node;
import org.netbeans.api.nbfx.editor.FileIconProvider;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.modules.nbfx.ide.diff.DiffSource;
import org.netbeans.modules.nbfx.ide.diff.DiffView;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Git &#9656; Diff: shows what changed in a file since the last commit, what {@code HEAD} has of
 * it on the left and what the working tree has on the right.
 * <p>
 * Both sides are read off the FX thread and by the same rules - no more than {@link #MOST_BYTES},
 * no binary file, no bytes that are no text in the charset of the file - into the snapshots the
 * diff view shows and compares. A file that is on one side only is missing on the other, which is
 * not the same as empty; a file that was renamed is compared with what it was called in the
 * commit; a side that cannot be compared says why in the diff itself.
 *
 * @since 1.4
 */
final class GitDiff {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git-diff", 1);
    /** The largest file compared: beyond it a diff side by side is of no use, and slow to make. */
    static final long MOST_BYTES = 8L << 20;
    /** The last request for the diff of each file: an earlier one still running is given up. */
    private static final Map<Path, Long> REQUESTS = new ConcurrentHashMap<>();

    /** The two sides of a diff, as read. */
    record Sides(DiffSource head, DiffSource workingTree) {
    }

    private GitDiff() {
    }

    /** Whether {@code file} is one a diff can be opened for: a file on disk in a working tree. */
    static boolean canDiff(FileObject file) {
        return file != null && file.isData() && GitMenus.inWorkingTree(file);
    }

    /**
     * Whether a diff is offered for {@code file} on a row of the status view in {@code mode}. The
     * diff is of the last commit and the working tree: a row of another mode tells of the index,
     * and the diff would not be of what the row tells.
     */
    static boolean canDiff(FileObject file, VcsCompareMode mode) {
        return canDiff(file) && mode == VcsCompareMode.HEAD_VS_WORKING_TREE;
    }

    /**
     * Opens the diff of {@code file} against the last commit, where it is looked for under the
     * name the status last read has it renamed from, if it was. Must run on the FX thread.
     */
    static void open(FileObject file) {
        File onDisk = file == null || !file.isData() ? null : FileUtil.toFile(file);
        String renamedFrom = onDisk == null ? null : GitStatusCache.getDefault().statusOf(onDisk.toPath())
                .map(VcsFileStatus::originalPath).orElse(null);
        open(file, renamedFrom);
    }

    /**
     * Opens the diff of {@code file} against the last commit. Asked again before it is shown, the
     * diff is read once more and the earlier read given up. Must run on the FX thread.
     *
     * @param headPath the path the file has in the last commit, relative to the repository root,
     *        when it was renamed since; {@code null} when it is where it was
     */
    static void open(FileObject file, String headPath) {
        File onDisk = file == null || !file.isData() ? null : FileUtil.toFile(file);
        Path folder = onDisk == null || onDisk.getParentFile() == null ? null : onDisk.getParentFile().toPath();
        // Told from the .git entry of a parent, as the menu that leads here does.
        Path root = folder == null ? null : GitMenus.workingTreeOf(folder.toFile());
        if (root == null) {
            return;
        }
        Path key = onDisk.toPath();
        String path = root.relativize(key).toString().replace(File.separatorChar, '/');
        long request = REQUESTS.merge(key, 1L, Long::sum);
        BooleanSupplier superseded = () -> REQUESTS.getOrDefault(key, 0L) != request;
        RP.post(() -> {
            Sides sides;
            try {
                // The charset the editor reads the file in, found out here: it may read the disk.
                sides = sides(root, key, path, headPath == null ? path : headPath,
                        FileEncodingQuery.getEncoding(file), superseded);
            } catch (CancellationException ex) {
                return;
            }
            Platform.runLater(() -> {
                if (!superseded.getAsBoolean()) {
                    DiffView.open(key, file.getNameExt(), icon(file), sides.head(), sides.workingTree());
                }
            });
        });
    }

    /**
     * Reads the two sides of the diff of {@code file}. Reads the disk and runs git: not for the FX
     * thread.
     *
     * @param root the root of the working tree
     * @param file the file in the working tree
     * @param path its path relative to {@code root}, with {@code /} between its folders
     * @param headPath its path in the last commit: {@code path}, or where it was renamed from
     * @param working the charset of its text in the working tree
     * @param cancelled whether the diff is no longer wanted
     * @throws CancellationException when {@code cancelled} answered {@code true}
     */
    static Sides sides(Path root, Path file, String path, String headPath, Charset working,
            BooleanSupplier cancelled) {
        String name = file.getFileName().toString();
        // What git stores is in the charset of the file, unless git converts it on checkout.
        Charset stored = GitClient.storedCharset(root, headPath, working, true);
        DiffSource head = source(message("Git.diff.head"), name,
                GitClient.headContent(root, headPath, stored, MOST_BYTES, cancelled));
        DiffSource workingTree = source(message("Git.diff.workingTree"), name,
                GitContentReader.workingTree(file, working, MOST_BYTES));
        return new Sides(head, workingTree);
    }

    /** The side of a diff for {@code content}: its text, no file, or why it cannot be compared. */
    static DiffSource source(String title, String name, GitContent content) {
        if (content instanceof GitContent.Text text) {
            return DiffSource.of(title, name, text.text());
        }
        String problem = problem(content);
        return problem == null ? DiffSource.missing(title, name) : DiffSource.unreadable(title, name, problem);
    }

    /**
     * Why {@code content} is no text to compare, for the user to read; {@code null} for content
     * that is one, or that is not there.
     */
    static String problem(GitContent content) {
        return switch (content) {
            case GitContent.Text text -> null;
            case GitContent.Missing missing -> null;
            case GitContent.Binary binary -> message("Git.diff.binary");
            case GitContent.TooLarge large -> NbBundle.getMessage(GitDiff.class, "Git.diff.tooLarge", large.size(),
                    large.limit());
            case GitContent.Unmerged unmerged -> message("Git.diff.unmerged");
            case GitContent.NotAFile other -> message("Git.diff.notAFile");
            case GitContent.Malformed malformed -> NbBundle.getMessage(GitDiff.class, "Git.diff.malformed",
                    malformed.charset());
            case GitContent.Failed failed -> failed.message().isBlank() ? message("Git.diff.unreadable")
                    : failed.message();
        };
    }

    private static Node icon(FileObject file) {
        FileIconProvider icons = Lookup.getDefault().lookup(FileIconProvider.class);
        return icons == null ? null : icons.createIcon(file);
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitDiff.class, key);
    }
}
