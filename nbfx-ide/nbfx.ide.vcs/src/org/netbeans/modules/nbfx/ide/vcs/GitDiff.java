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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.scene.Node;
import org.netbeans.api.nbfx.editor.FileIconProvider;
import org.netbeans.api.nbfx.editor.SavedDocument;
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
 *
 * @since 1.4
 */
final class GitDiff {

    private static final String KEY_MODE = "diff.mode";

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
        boolean single = "file".equals(lines[1]);
        Path target = Path.of(lines[2]);
        // A file may be gone - deleted, its diff shows what it held - but not its folder.
        Path folder = single ? target.getParent() : target;
        Path root = folder == null || !folder.toFile().isDirectory() ? null
                : GitStatusCache.getDefault().rootOf(folder).orElse(null);
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
        Path root = folder == null ? null : GitStatusCache.getDefault().rootOf(folder.toPath()).orElse(null);
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
        return status.files().stream()
                .filter(file -> file.path().startsWith(prefix) && file.change(mode).isPresent())
                .sorted(Comparator.comparing(VcsFileStatus::path))
                .toList();
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
    private static final class Provider implements DiffProvider {

        private static final List<String> ICONS = List.of(GitIcons.HEAD_VS_WORKING_TREE, GitIcons.HEAD_VS_INDEX,
                GitIcons.INDEX_VS_WORKING_TREE);

        private final Path root;
        private final String scope;
        private final boolean single;
        private final List<DiffMode> modes = new ArrayList<>();

        Provider(Path root, String scope, boolean single) {
            this.root = root;
            this.scope = scope;
            this.single = single;
            for (VcsCompareMode mode : VcsCompareMode.values()) {
                String icon = ICONS.get(mode.ordinal());
                modes.add(new DiffMode(mode.name(), message("Git.mode." + mode.name()), () -> GitIcons.view(icon)));
            }
        }

        DiffMode mode(VcsCompareMode mode) {
            return modes.get(mode.ordinal());
        }

        @Override
        public List<DiffMode> modes() {
            return List.copyOf(modes);
        }

        @Override
        public SavedDocument savedState(DiffMode chosen) {
            return new SavedDocument(RESTORER_ID,
                    state(VcsCompareMode.valueOf(chosen.id()), single, root.resolve(scope)));
        }

        @Override
        public List<DiffEntry> entries(DiffMode chosen) throws IOException {
            VcsCompareMode mode = VcsCompareMode.valueOf(chosen.id());
            // Asked for as the diff opens, as a mode is chosen and on Refresh: the mode in use.
            NbPreferences.forModule(GitDiff.class).put(KEY_MODE, mode.name());
            VcsStatus status = GitClient.status(root);
            if (single) {
                // The file asked for, changed in this mode or not: the same on both sides then.
                VcsFileStatus file = status.files().stream()
                        .filter(changed -> changed.path().equals(scope)).findFirst().orElse(null);
                return List.of(entry(scope, file, mode));
            }
            return changedWithin(status, scope, mode).stream().map(file -> entry(file.path(), file, mode)).toList();
        }

        private DiffEntry entry(String path, VcsFileStatus file, VcsCompareMode mode) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            VcsChangeKind kind = file == null ? null : file.change(mode).orElse(null);
            // A renamed file is in the last commit under its old name.
            String committed = file != null && file.originalPath() != null ? file.originalPath() : path;
            return new DiffEntry(name, path, kind == null ? "" : message("Git.kind." + kind.name()),
                    entryKind(kind), () -> fileIcon(path), () -> new DiffPair(
                            mode == VcsCompareMode.INDEX_VS_WORKING_TREE ? index(name, path) : head(name, committed),
                            mode == VcsCompareMode.HEAD_VS_INDEX ? index(name, path) : workingTree(name, path)));
        }

        /** The icon of the file on disk; none for one that is gone. */
        private Node fileIcon(String path) {
            File onDisk = root.resolve(path).toFile();
            FileObject file = onDisk.isFile() ? FileUtil.toFileObject(onDisk) : null;
            return file == null ? null : icon(file);
        }

        /** A file the last commit does not have is compared as an empty one; so is one not in the index. */
        private DiffSource head(String name, String path) throws IOException {
            return new DiffSource(message("Git.diff.head"), inMemory(name, GitClient.headContent(root, path).orElse("")));
        }

        private DiffSource index(String name, String path) throws IOException {
            return new DiffSource(message("Git.diff.index"), inMemory(name, GitClient.indexContent(root, path).orElse("")));
        }

        /** The file on disk, so that the diff shows it as it is saved; an empty one when it is gone. */
        private DiffSource workingTree(String name, String path) throws IOException {
            File onDisk = root.resolve(path).toFile();
            FileObject file = onDisk.isFile() ? FileUtil.toFileObject(onDisk) : null;
            return new DiffSource(message("Git.diff.workingTree"), file != null ? file : inMemory(name, ""));
        }
    }

    /** A file in memory named {@code name}, so that an editor colours it as the one on disk, holding {@code text}. */
    static FileObject inMemory(String name, String text) throws IOException {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData(name);
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static Node icon(FileObject file) {
        FileIconProvider icons = Lookup.getDefault().lookup(FileIconProvider.class);
        return icons == null ? null : icons.createIcon(file);
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitDiff.class, key);
    }
}
