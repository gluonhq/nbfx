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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import javafx.scene.Node;
import org.netbeans.api.nbfx.editor.FileIconProvider;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.modules.nbfx.ide.diff.DiffEntry;
import org.netbeans.modules.nbfx.ide.diff.DiffMode;
import org.netbeans.modules.nbfx.ide.diff.DiffPair;
import org.netbeans.modules.nbfx.ide.diff.DiffProvider;
import org.netbeans.modules.nbfx.ide.diff.DiffSource;
import org.netbeans.modules.nbfx.ide.diff.DiffView;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.netbeans.spi.nbfx.editor.DocumentRestorer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

/**
 * Git &#9656; Diff: shows what changed in a file, or in the files of a folder, in a diff tab. The
 * tab compares the last commit, the index and the working tree two by two, as the Git window does:
 * {@code HEAD} with the working tree at first, or the way asked for. Of a folder it lists the files
 * that differ, to choose from; of a file, that file alone, as NetBeans does.
 * <p>
 * Whatever is compared, both sides are read off the FX thread and by the same rules - no more than
 * {@link #MOST_BYTES}, no binary file, no bytes that are no text in the charset of the file - into
 * the snapshots the diff view shows and compares. A file that is on one side only is missing on
 * the other, which is not the same as empty; a file that was renamed is compared with what it was
 * called in the commit; a side that cannot be compared says why in the diff itself.
 *
 * @since 1.4
 */
final class GitDiff {

    private static final String KEY_MODE = "diff.mode";
    /** The largest file compared: beyond it a diff side by side is of no use, and slow to make. */
    static final long MOST_BYTES = 8L << 20;

    /** The two sides of the diff of a file between the last commit and the working tree, as read. */
    record Sides(DiffSource head, DiffSource workingTree) {
    }

    private GitDiff() {
    }

    /** The id of {@link Restorer}. */
    static final String RESTORER_ID = "git-diff";

    /**
     * What opens again the diff of the file or the folder at {@code target}, comparing as
     * {@code mode}: the mode, whether it is of one file, and the path, each on a line.
     */
    static String state(VcsCompareMode mode, boolean single, Path target) {
        return mode.name() + "\n" + (single ? "file" : "folder") + "\n" + target;
    }

    /**
     * Opens the diff saved as {@code state}, if what it was of is still in a working tree. Must
     * run on the FX thread.
     *
     * @return whether it was opened
     */
    static boolean restore(String state) {
        String[] lines = state.split("\n", 3);
        if (lines.length < 3) {
            return false;
        }
        VcsCompareMode mode;
        try {
            mode = VcsCompareMode.valueOf(lines[0]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        // The path is the last of the three, and whole: with the line breaks a name may have.
        if (!"file".equals(lines[1]) && !"folder".equals(lines[1])) {
            return false;
        }
        boolean single = "file".equals(lines[1]);
        Path target;
        try {
            target = Path.of(lines[2]);
        } catch (InvalidPathException ex) {
            return false;
        }
        if (!target.isAbsolute()) {
            return false;
        }
        // A file may be gone - deleted, its diff shows what it held - but not its folder.
        Path folder = single ? target.getParent() : target;
        // Told from the .git entry of a parent, as the menu that leads to a diff does.
        Path root = folder == null || !folder.toFile().isDirectory() ? null
                : GitMenus.workingTreeOf(folder.toFile());
        if (root == null) {
            return false;
        }
        FileObject file = FileUtil.toFileObject(target.toFile());
        String name = target.getFileName() == null ? target.toString() : target.getFileName().toString();
        String path = root.relativize(target).toString().replace(File.separatorChar, '/');
        open(root, path, single, name, file == null ? null : icon(file), mode);
        return true;
    }

    /** Opens the Git diffs of the last session again. */
    @ServiceProvider(service = DocumentRestorer.class)
    public static final class Restorer implements DocumentRestorer {

        @Override
        public String id() {
            return RESTORER_ID;
        }

        @Override
        public void restore(String state) {
            GitDiff.restore(state);
        }
    }

    /** The way the diff last shown compared: what the next one opened from a menu compares as. */
    static VcsCompareMode lastMode() {
        try {
            return VcsCompareMode.valueOf(NbPreferences.forModule(GitDiff.class)
                    .get(KEY_MODE, VcsCompareMode.HEAD_VS_WORKING_TREE.name()));
        } catch (IllegalArgumentException ex) {
            return VcsCompareMode.HEAD_VS_WORKING_TREE;
        }
    }

    /** Whether a diff can be opened for {@code file}: a file or a folder on disk in a working tree. */
    static boolean canDiff(FileObject file) {
        return file != null && FileUtil.toFile(file) != null && GitMenus.inWorkingTree(file);
    }

    /**
     * Opens the diff of {@code file}, comparing as the diff last shown did - the last commit with
     * the working tree at first. Nothing for a file that is in no working tree. Must run on the FX
     * thread.
     */
    static void open(FileObject file) {
        File onDisk = file == null ? null : FileUtil.toFile(file);
        File folder = onDisk == null ? null : file.isData() ? onDisk.getParentFile() : onDisk;
        Path root = folder == null ? null : GitMenus.workingTreeOf(folder);
        if (root == null) {
            return;
        }
        String path = root.relativize(onDisk.toPath()).toString().replace(File.separatorChar, '/');
        open(root, path, file.isData(), file.getNameExt(), icon(file), lastMode());
    }

    /**
     * Opens the diff of the file at {@code path} of the repository at {@code root} - one that may
     * be gone from disk - comparing as {@code mode}. Must run on the FX thread.
     */
    static void open(Path root, String path, String name, Node icon, VcsCompareMode mode) {
        open(root, path, true, name, icon, mode);
    }

    private static void open(Path root, String path, boolean single, String name, Node icon, VcsCompareMode mode) {
        Provider provider = new Provider(root, path, single);
        DiffView.open(root.resolve(path), name, icon, provider, provider.mode(mode));
    }

    /**
     * The files of {@code status} that differ in {@code mode} in the folder at {@code scope} - a
     * path relative to the repository root, empty for the root itself - or below it, by path.
     */
    static List<VcsFileStatus> changedWithin(VcsStatus status, String scope, VcsCompareMode mode) {
        String prefix = scope.isEmpty() ? "" : scope + "/";
        // As the mode sees them: what the status view lists in it, under the paths it lists them.
        return status.files(mode).stream()
                .filter(file -> file.path(mode).startsWith(prefix)
                        // Moved out of the folder: gone from it, which is a change of the folder.
                        || mode != VcsCompareMode.INDEX_VS_WORKING_TREE && headPath(file, mode).startsWith(prefix))
                .sorted(Comparator.comparing(file -> file.path(mode)))
                .toList();
    }

    /**
     * Where the last commit has the file {@code mode} lists as {@code file}: under the name it
     * was renamed from, for a renamed one. A copy is a new file, with nothing in the last commit
     * to compare it to, whatever it was copied from.
     */
    static String headPath(VcsFileStatus file, VcsCompareMode mode) {
        boolean renamed = file.staged() == VcsChangeKind.RENAMED || file.unstaged() == VcsChangeKind.RENAMED;
        return renamed && file.originalPath() != null ? file.originalPath() : file.path(mode);
    }

    /** How a diff view colours a file changed as {@code kind}. */
    static DiffEntry.Kind entryKind(VcsChangeKind kind) {
        return kind == null ? DiffEntry.Kind.OTHER : switch (kind) {
            case ADDED, COPIED, UNTRACKED -> DiffEntry.Kind.ADDED;
            case MODIFIED, RENAMED, TYPE_CHANGED -> DiffEntry.Kind.MODIFIED;
            case DELETED -> DiffEntry.Kind.DELETED;
            case CONFLICTED -> DiffEntry.Kind.CONFLICTED;
        };
    }

    /** What the diff tab of one file, or of a folder, of a repository shows. */
    static final class Provider implements DiffProvider {

        private final Path root;
        private final String scope;
        private final boolean single;
        /** The ways to compare, by what they compare, in the order they are offered in. */
        private final Map<VcsCompareMode, DiffMode> modes = new EnumMap<>(VcsCompareMode.class);

        Provider(Path root, String scope, boolean single) {
            this.root = root;
            this.scope = scope;
            this.single = single;
            for (VcsCompareMode mode : VcsCompareMode.values()) {
                String icon = modeIcon(mode);
                modes.put(mode, new DiffMode(mode.name(), message("Git.mode." + mode.name()), () -> GitIcons.view(icon)));
            }
        }

        private static String modeIcon(VcsCompareMode mode) {
            return switch (mode) {
                case HEAD_VS_WORKING_TREE -> GitIcons.HEAD_VS_WORKING_TREE;
                case HEAD_VS_INDEX -> GitIcons.HEAD_VS_INDEX;
                case INDEX_VS_WORKING_TREE -> GitIcons.INDEX_VS_WORKING_TREE;
            };
        }

        DiffMode mode(VcsCompareMode mode) {
            return modes.get(mode);
        }

        @Override
        public List<DiffMode> modes() {
            return List.copyOf(modes.values());
        }

        /** The diff of one file is of that file: no table to choose it from. */
        @Override
        public boolean listed() {
            return !single;
        }

        /** The mode the user chose is the one the next diff opened from a menu compares as. */
        @Override
        public void modeChosen(DiffMode chosen) {
            NbPreferences.forModule(GitDiff.class).put(KEY_MODE, chosen.id());
        }

        @Override
        public SavedDocument savedState(DiffMode chosen) {
            return new SavedDocument(RESTORER_ID,
                    state(VcsCompareMode.valueOf(chosen.id()), single, root.resolve(scope)));
        }

        @Override
        public List<DiffEntry> entries(DiffMode chosen) throws IOException {
            VcsCompareMode mode = VcsCompareMode.valueOf(chosen.id());
            VcsStatus status = GitClient.status(root);
            if (single) {
                // The file asked for, changed in this mode or not: the same on both sides then.
                VcsFileStatus file = status.files(mode).stream()
                        .filter(changed -> changed.path(mode).equals(scope)).findFirst().orElse(null);
                return List.of(entry(scope, file, mode));
            }
            return changedWithin(status, scope, mode).stream().map(file -> entry(file.path(mode), file, mode)).toList();
        }

        private DiffEntry entry(String path, VcsFileStatus file, VcsCompareMode mode) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            VcsChangeKind kind = file == null ? null : file.change(mode).orElse(null);
            // A renamed file is in the last commit under its old name; a copied one is not in it.
            String committed = file == null ? path : headPath(file, mode);
            return new DiffEntry(name, path, kind == null ? "" : message("Git.kind." + kind.name()),
                    entryKind(kind), () -> fileIcon(path),
                    () -> pair(root, path, committed, mode, workingCharset(root.resolve(path)), () -> false));
        }

        /** The icon of the file on disk; none for one that is gone. */
        private Node fileIcon(String path) {
            File onDisk = root.resolve(path).toFile();
            FileObject file = onDisk.isFile() ? FileUtil.toFileObject(onDisk) : null;
            return file == null ? null : icon(file);
        }
    }

    /**
     * The charset the text of {@code file} is in, in the working tree: the one the editor reads it
     * in, or UTF-8 for a file that is gone. May read the disk: not for the FX thread.
     */
    static Charset workingCharset(Path file) {
        FileObject onDisk = file.toFile().isFile() ? FileUtil.toFileObject(file.toFile()) : null;
        return onDisk == null ? StandardCharsets.UTF_8 : FileEncodingQuery.getEncoding(onDisk);
    }

    /**
     * Reads the two sides of the diff of a file as {@code mode} compares them. Reads the disk and
     * runs git: not for the FX thread.
     *
     * @param root the root of the working tree
     * @param path the path of the file relative to {@code root}, with {@code /} between its folders
     * @param headPath its path in the last commit: {@code path}, or where it was renamed from
     * @param mode which two of the last commit, the index and the working tree are compared
     * @param working the charset of its text in the working tree
     * @param cancelled whether the diff is no longer wanted
     * @throws CancellationException when {@code cancelled} answered {@code true}
     */
    static DiffPair pair(Path root, String path, String headPath, VcsCompareMode mode, Charset working,
            BooleanSupplier cancelled) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        DiffSource left = mode == VcsCompareMode.INDEX_VS_WORKING_TREE
                ? index(root, name, path, working, cancelled) : head(root, name, headPath, working, cancelled);
        DiffSource right = mode == VcsCompareMode.HEAD_VS_INDEX
                ? index(root, name, path, working, cancelled) : workingTree(root, name, path, working);
        return new DiffPair(left, right);
    }

    /** The two sides of the diff of {@code file} between the last commit and the working tree; see {@link #pair}. */
    static Sides sides(Path root, Path file, String path, String headPath, Charset working,
            BooleanSupplier cancelled) {
        DiffPair pair = pair(root, path, headPath, VcsCompareMode.HEAD_VS_WORKING_TREE, working, cancelled);
        return new Sides(pair.left(), pair.right());
    }

    /** What the last commit has of the file: in the charset git stores it in. */
    private static DiffSource head(Path root, String name, String path, Charset working, BooleanSupplier cancelled) {
        Charset stored = GitClient.storedCharset(root, path, working, true);
        return source(message("Git.diff.head"), name, GitClient.headContent(root, path, stored, MOST_BYTES, cancelled))
                .withPath(path);
    }

    /** What the index has of the file: what the next commit takes. */
    private static DiffSource index(Path root, String name, String path, Charset working, BooleanSupplier cancelled) {
        Charset stored = GitClient.storedCharset(root, path, working, false);
        return source(message("Git.diff.index"), name,
                GitClient.indexContent(root, path, stored, MOST_BYTES, cancelled)).withPath(path);
    }

    /** The file as it is on disk, read with the same care as what git has. */
    private static DiffSource workingTree(Path root, String name, String path, Charset working) {
        return source(message("Git.diff.workingTree"), name,
                GitContentReader.workingTree(root.resolve(path), working, MOST_BYTES)).withPath(path);
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
