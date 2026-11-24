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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.openide.util.RequestProcessor;

/**
 * The last read status of the Git repositories that were asked about, so that a view can ask for the
 * state of a file on every row it draws. A question never reads the disk beyond finding the
 * repository of a folder once: the status of a repository not read yet is read in the background,
 * and the listeners are told when it is known or has changed.
 * <p>
 * A repository is read again when {@link #refresh(Path) asked}; what asks - a file changed in the
 * IDE, the window getting the focus back, a commit - is {@link GitStatusTriggers}.
 *
 * @since 1.3
 */
final class GitStatusCache {

    /** How to read the status of a repository from its root. */
    @FunctionalInterface
    interface Reader {
        VcsStatus read(Path root) throws IOException;
    }

    private static final Logger LOG = Logger.getLogger(GitStatusCache.class.getName());
    /** Changes come in bursts - a build, a branch switch - that one read answers. */
    private static final int REFRESH_DELAY_MILLIS = 400;
    private static final GitStatusCache DEFAULT = new GitStatusCache(GitClient::status);

    private final Reader reader;
    private final RequestProcessor processor = new RequestProcessor("nbfx-git-status", 1);
    /** The repository root of each folder asked about; empty for a folder outside any working tree. */
    private final Map<Path, Optional<Path>> roots = new ConcurrentHashMap<>();
    private final Map<Path, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<Path, RequestProcessor.Task> reads = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * The status of a repository: its changed files by absolute path, and the folders that hold
     * them - right in them ({@code direct}) or anywhere below ({@code deep}) - each with whether one
     * of those files has conflicts.
     */
    private record Snapshot(VcsStatus status, Map<Path, VcsFileStatus> files, Map<Path, Boolean> direct,
            Map<Path, Boolean> deep) {

        static final Snapshot UNKNOWN = new Snapshot(null, Map.of(), Map.of(), Map.of());

        static Snapshot of(Path root, VcsStatus status) {
            Map<Path, VcsFileStatus> files = new HashMap<>();
            Map<Path, Boolean> direct = new HashMap<>();
            Map<Path, Boolean> deep = new HashMap<>();
            for (VcsFileStatus file : status.files()) {
                Path path = root.resolve(file.path());
                files.put(path, file);
                if (!file.conflicted() && file.change(VcsCompareMode.HEAD_VS_WORKING_TREE).isEmpty()) {
                    // Added and deleted again: nothing a folder holds.
                    continue;
                }
                Path folder = path.getParent();
                if (folder != null) {
                    direct.merge(folder, file.conflicted(), Boolean::logicalOr);
                }
                for (; folder != null && folder.startsWith(root); folder = folder.getParent()) {
                    deep.merge(folder, file.conflicted(), Boolean::logicalOr);
                }
            }
            return new Snapshot(status, files, direct, deep);
        }
    }

    GitStatusCache(Reader reader) {
        this.reader = reader;
    }

    /** The cache of the IDE, read with the {@code git} command line. */
    static GitStatusCache getDefault() {
        return DEFAULT;
    }

    /**
     * The status of {@code file} as last read, or empty when it has no changes, is in no working
     * tree, or its repository was not read yet - in which case it is read now, in the background.
     */
    Optional<VcsFileStatus> statusOf(Path file) {
        Path folder = file == null ? null : file.getParent();
        Snapshot snapshot = folder == null ? null : snapshotOf(folder);
        return snapshot == null ? Optional.empty() : Optional.ofNullable(snapshot.files().get(file));
    }

    /**
     * Whether {@code folder} holds changed files as last read - right in it, or with
     * {@code recursive} anywhere below - and if so whether one of them has conflicts. Empty when it
     * holds none, is in no working tree, or its repository was not read yet - in which case it is
     * read now, in the background.
     */
    Optional<Boolean> conflictsWithin(Path folder, boolean recursive) {
        Snapshot snapshot = folder == null ? null : snapshotOf(folder);
        return snapshot == null ? Optional.empty()
                : Optional.ofNullable((recursive ? snapshot.deep() : snapshot.direct()).get(folder));
    }

    /** The last read status of the repository {@code folder} is in, or {@code null}; see {@link #statusOf}. */
    private Snapshot snapshotOf(Path folder) {
        Path root = rootOf(folder).orElse(null);
        if (root == null) {
            return null;
        }
        Snapshot snapshot = snapshots.get(root);
        if (snapshot == null) {
            // Marked so that the rows drawn until the read ends do not ask for it again.
            if (snapshots.putIfAbsent(root, Snapshot.UNKNOWN) == null) {
                schedule(root, 0);
            }
            return null;
        }
        return snapshot;
    }

    /** The root of the working tree {@code folder} is in, told from the {@code .git} entry of a parent. */
    Optional<Path> rootOf(Path folder) {
        return roots.computeIfAbsent(folder, asked -> {
            for (File parent = asked.toFile(); parent != null; parent = parent.getParentFile()) {
                if (new File(parent, ".git").exists()) {
                    return Optional.of(parent.toPath());
                }
            }
            return Optional.empty();
        });
    }

    /** Whether the status of the repository at {@code root} was asked for, and so is kept up to date. */
    boolean isTracked(Path root) {
        return snapshots.containsKey(root);
    }

    /**
     * Reads again, shortly, the repository {@code folder} is in, if it is one that was asked about;
     * calls in quick succession make one read.
     */
    void refresh(Path folder) {
        Path root = folder == null ? null : rootOf(folder).orElse(null);
        if (root != null && isTracked(root)) {
            schedule(root, REFRESH_DELAY_MILLIS);
        }
    }

    /**
     * Reads again, shortly, every repository asked about, and forgets which folder is in which: a
     * repository may have been made or removed outside the IDE. The listeners are told only of what
     * the reads find changed; a folder that has become a working tree is found when next asked about.
     */
    void refreshAll() {
        roots.clear();
        snapshots.keySet().forEach(root -> schedule(root, REFRESH_DELAY_MILLIS));
    }

    /**
     * As {@link #refreshAll()}, and has the views ask again now: a repository was made in the IDE, so
     * files that were in no working tree are in one.
     */
    void repositoriesChanged() {
        refreshAll();
        fireChanged();
    }

    /** Reads the repository at {@code root} now, on this thread. */
    void read(Path root) {
        Snapshot read;
        try {
            read = Snapshot.of(root, reader.read(root));
        } catch (IOException | RuntimeException ex) {
            // No longer a repository, or git failed: nothing to mark.
            LOG.log(Level.FINE, "No git status for " + root, ex);
            read = Snapshot.of(root, VcsStatus.CLEAN);
        }
        Snapshot previous = snapshots.put(root, read);
        if (previous == null || !read.status().equals(previous.status())) {
            fireChanged();
        }
    }

    void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void schedule(Path root, int delayMillis) {
        reads.computeIfAbsent(root, key -> processor.create(() -> read(key))).schedule(delayMillis);
    }

    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
