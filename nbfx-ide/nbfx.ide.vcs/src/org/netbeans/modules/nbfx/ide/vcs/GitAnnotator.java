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
import java.util.Optional;
import org.netbeans.api.nbfx.vcs.VcsAnnotator;
import org.netbeans.api.nbfx.vcs.VcsFileState;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 * Git's answer to the views that mark changed files and the folders that hold them: the state of a
 * file between the last commit and the working tree, from the {@link GitStatusCache}.
 *
 * @since 1.3
 */
@ServiceProvider(service = VcsAnnotator.class)
public final class GitAnnotator implements VcsAnnotator {

    private final GitStatusCache cache;

    /** The annotator of the IDE, over the shared cache, kept up to date from the first question on. */
    public GitAnnotator() {
        this(GitStatusCache.getDefault());
    }

    GitAnnotator(GitStatusCache cache) {
        this.cache = cache;
    }

    @Override
    public Optional<VcsFileState> stateOf(FileObject file) {
        File onDisk = file == null || file.isFolder() ? null : onDisk(file);
        return onDisk == null ? Optional.empty()
                : cache.statusOf(onDisk.toPath()).flatMap(GitAnnotator::stateOf);
    }

    @Override
    public Optional<VcsFileState> stateWithin(FileObject folder, boolean recursive) {
        File onDisk = folder == null || !folder.isFolder() ? null : onDisk(folder);
        return onDisk == null ? Optional.empty()
                : cache.conflictsWithin(onDisk.toPath(), recursive)
                        .map(conflicts -> conflicts ? VcsFileState.CONFLICTED : VcsFileState.MODIFIED);
    }

    /** The file on disk, with the cache of the IDE kept up to date from the first question on. */
    private File onDisk(FileObject file) {
        File onDisk = FileUtil.toFile(file);
        if (onDisk != null && cache == GitStatusCache.getDefault()) {
            GitStatusTriggers.install(cache);
        }
        return onDisk;
    }

    /** The state a file with {@code status} is drawn in: what changed since the last commit. */
    static Optional<VcsFileState> stateOf(VcsFileStatus status) {
        if (status.conflicted()) {
            return Optional.of(VcsFileState.CONFLICTED);
        }
        return status.change(VcsCompareMode.HEAD_VS_WORKING_TREE).map(GitAnnotator::stateOf);
    }

    private static VcsFileState stateOf(VcsChangeKind kind) {
        return switch (kind) {
            case ADDED, UNTRACKED, RENAMED, COPIED -> VcsFileState.ADDED;
            case MODIFIED, TYPE_CHANGED -> VcsFileState.MODIFIED;
            case DELETED -> VcsFileState.DELETED;
            case CONFLICTED -> VcsFileState.CONFLICTED;
        };
    }

    @Override
    public void addChangeListener(Runnable listener) {
        cache.addChangeListener(listener);
    }

    @Override
    public void removeChangeListener(Runnable listener) {
        cache.removeChangeListener(listener);
    }
}
