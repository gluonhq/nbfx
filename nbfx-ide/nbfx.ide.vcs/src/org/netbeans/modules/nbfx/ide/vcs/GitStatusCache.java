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
import java.util.Set;
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
 * state of a file on every row it draws. A question never reads the disk: the repository of a
 * folder is found, and its status read, in the background, and the listeners are told when it is
 * known or has changed.
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
    /** The repository root found for each folder asked about; empty for a folder outside any working tree. */
    private final Map<Path, Optional<Path>> roots = new ConcurrentHashMap<>();
    private final Map<Path, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<Path, RequestProcessor.Task> reads = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    /** The folders asked about whose repository is still to be found, or to be found again. */
    private final Set<Path> unresolved = ConcurrentHashMap.newKeySet();
    private final RequestProcessor.Task resolver = processor.create(this::resolve);

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
        Optional<Path> known = roots.get(folder);
        Path root;
        if (known == null) {
            // Found out in the background; until then, the repository it is known to be below.
            if (unresolved.add(folder)) {
                resolver.schedule(0);
            }
            root = trackedRootOf(folder);
        } else {
            root = known.orElse(null);
        }
        return root == null ? null : snapshots.get(root);
    }

    /** The deepest repository asked about that {@code folder} is in, or {@code null}: nothing is read. */
    private Path trackedRootOf(Path folder) {
        Path deepest = null;
        for (Path root : snapshots.keySet()) {
            if (folder.startsWith(root) && (deepest == null || root.getNameCount() > deepest.getNameCount())) {
                deepest = root;
            }
        }
        return deepest;
    }

    /**
     * The root of the working tree {@code folder} is in, told from the {@code .git} entry of a
     * parent - a folder for a repository, a file for a worktree or a submodule. Reads the disk.
     */
    static Optional<Path> findRoot(Path folder) {
        for (File parent = folder.toFile(); parent != null; parent = parent.getParentFile()) {
            if (new File(parent, ".git").exists()) {
                return Optional.of(parent.toPath());
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the repository of the folders asked about, in the background: a repository found for the
     * first time is read, and the listeners are told when a folder turns out to be in another
     * repository, or in none, than the one it was answered from.
     */
    private void resolve() {
        boolean changed = false;
        for (Path folder : List.copyOf(unresolved)) {
            unresolved.remove(folder);
            Optional<Path> answered = roots.get(folder);
            if (answered == null) {
                answered = Optional.ofNullable(trackedRootOf(folder));
            }
            Optional<Path> found = findRoot(folder);
            roots.put(folder, found);
            if (found.isPresent() && snapshots.putIfAbsent(found.get(), Snapshot.UNKNOWN) == null) {
                // The listeners are told when it is read.
                schedule(found.get(), 0);
            } else if (!found.equals(answered)) {
                changed = true;
            }
        }
        if (changed) {
            fireChanged();
        }
    }

    /** Whether the status of the repository at {@code root} was asked for, and so is kept up to date. */
    boolean isTracked(Path root) {
        return snapshots.containsKey(root);
    }

    /**
     * Reads again, shortly, the repositories asked about that {@code folder} is in; calls in quick
     * succession make one read. Reads no disk itself.
     */
    void refresh(Path folder) {
        if (folder == null) {
            return;
        }
        for (Path root : snapshots.keySet()) {
            if (folder.startsWith(root)) {
                schedule(root, REFRESH_DELAY_MILLIS);
            }
        }
    }

    /**
     * Reads again, shortly, every repository asked about, and finds out again which folder is in
     * which: a repository may have been made or removed outside the IDE. Until then the folders are
     * answered as before, and the listeners are told only of what is found changed.
     */
    void refreshAll() {
        refreshAll(REFRESH_DELAY_MILLIS);
    }

    /**
     * As {@link #refreshAll()}, at once: a repository was made in the IDE, so files that were in no
     * working tree are in one.
     */
    void repositoriesChanged() {
        refreshAll(0);
    }

    private void refreshAll(int delayMillis) {
        if (!roots.isEmpty()) {
            unresolved.addAll(roots.keySet());
            resolver.schedule(delayMillis);
        }
        snapshots.keySet().forEach(root -> schedule(root, delayMillis));
    }

    /**
     * Reads the repository at {@code root} now, on this thread, if it is one asked about. When the
     * read fails the last status read is kept - git may be busy, or its index locked, and nothing
     * says the changes are gone - unless the folder is no longer a working tree, which is forgotten.
     */
    void read(Path root) {
        Snapshot previous = snapshots.get(root);
        if (previous == null) {
            return;
        }
        Snapshot read;
        try {
            read = Snapshot.of(root, reader.read(root));
        } catch (IOException | RuntimeException ex) {
            if (findRoot(root).filter(root::equals).isPresent()) {
                LOG.log(Level.INFO, "git status failed for {0}, the last one read is kept: {1}",
                        new Object[] {root, ex.getMessage()});
                LOG.log(Level.FINE, null, ex);
            } else {
                forget(root);
            }
            return;
        }
        // Not put back if forgotten meanwhile.
        if (snapshots.replace(root, read) != null && !read.status().equals(previous.status())) {
            fireChanged();
        }
    }

    /** Stops keeping the status of {@code root}, no longer a working tree, and of the folders in it. */
    private void forget(Path root) {
        LOG.log(Level.FINE, "No longer a git working tree: {0}", root);
        snapshots.remove(root);
        reads.remove(root);
        // In a repository further up, or in none: found out when next asked about.
        roots.values().removeIf(found -> found.filter(root::equals).isPresent());
        fireChanged();
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
