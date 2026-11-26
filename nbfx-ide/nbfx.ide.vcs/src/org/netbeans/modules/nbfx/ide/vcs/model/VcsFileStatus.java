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

import java.util.Objects;
import java.util.Optional;

/**
 * The state of one changed file: how the index differs from the last commit, and how the working tree
 * differs from the index. {@link #change(VcsCompareMode)} answers what a status view shows for a mode.
 * The type knows no version-control system and no toolkit.
 *
 * @param path the file, relative to the repository root, with {@code /} separators
 * @param originalPath where a renamed or copied file comes from, or {@code null}
 * @param staged how the index differs from the last commit, or {@code null} when it does not; never
 *        {@link VcsChangeKind#UNTRACKED} or {@link VcsChangeKind#CONFLICTED}
 * @param unstaged how the working tree differs from the index, or {@code null} when it does not; never
 *        {@link VcsChangeKind#CONFLICTED}
 * @param conflicted whether the file has an unresolved merge conflict
 * @since 1.1
 */
public record VcsFileStatus(String path, String originalPath, VcsChangeKind staged, VcsChangeKind unstaged,
        boolean conflicted) {

    public VcsFileStatus {
        Objects.requireNonNull(path, "path");
        if (staged == VcsChangeKind.UNTRACKED || staged == VcsChangeKind.CONFLICTED) {
            throw new IllegalArgumentException("Not a staged change: " + staged);
        }
        if (unstaged == VcsChangeKind.CONFLICTED) {
            throw new IllegalArgumentException("A conflict is the conflicted flag, not an unstaged change");
        }
        if (staged == null && unstaged == null && !conflicted) {
            throw new IllegalArgumentException("An unchanged file has no status: " + path);
        }
    }

    /**
     * How the file differs between the two sides of {@code mode}.
     *
     * <p>A conflicted file is {@link VcsChangeKind#CONFLICTED} in every mode, so that no mode hides it.
     * Between the last commit and the working tree, the staged and the unstaged change combine: a file
     * added to the index and then deleted from disk is in neither, and so has no change; a renamed file
     * deleted from disk is {@link VcsChangeKind#DELETED}, as its original is gone - the file the last
     * commit has, which {@link #path(VcsCompareMode)} names; a file deleted from the index with an
     * untracked file in its place is {@link VcsChangeKind#MODIFIED}.
     *
     * @return the change, or empty when the file is the same on both sides
     */
    public Optional<VcsChangeKind> change(VcsCompareMode mode) {
        if (conflicted) {
            return Optional.of(VcsChangeKind.CONFLICTED);
        }
        return Optional.ofNullable(switch (mode) {
            case HEAD_VS_INDEX -> staged;
            case INDEX_VS_WORKING_TREE -> unstaged;
            case HEAD_VS_WORKING_TREE -> combined();
        });
    }

    /**
     * The file {@link #change(VcsCompareMode)} is of: {@link #path()}, but for a file renamed in the
     * index and then deleted from disk, between the last commit and the working tree - what is
     * deleted there is the file the last commit has, at {@link #originalPath()}; the path it was
     * renamed to is in neither.
     *
     * @return the path, relative to the repository root
     */
    public String path(VcsCompareMode mode) {
        boolean renameUndone = mode == VcsCompareMode.HEAD_VS_WORKING_TREE && !conflicted
                && staged == VcsChangeKind.RENAMED && unstaged == VcsChangeKind.DELETED && originalPath != null;
        return renameUndone ? originalPath : path;
    }

    private VcsChangeKind combined() {
        if (staged == null) {
            return unstaged;
        }
        if (unstaged == null) {
            return staged;
        }
        boolean gone = unstaged == VcsChangeKind.DELETED;
        return switch (staged) {
            // Not in the last commit: gone from disk as well means nothing changed.
            case ADDED, COPIED -> gone ? null : staged;
            case RENAMED -> gone ? VcsChangeKind.DELETED : VcsChangeKind.RENAMED;
            // Out of the index, yet a file is on disk in its place.
            case DELETED -> VcsChangeKind.MODIFIED;
            default -> gone ? VcsChangeKind.DELETED
                    : staged == VcsChangeKind.TYPE_CHANGED || unstaged == VcsChangeKind.TYPE_CHANGED
                            ? VcsChangeKind.TYPE_CHANGED : VcsChangeKind.MODIFIED;
        };
    }
}
