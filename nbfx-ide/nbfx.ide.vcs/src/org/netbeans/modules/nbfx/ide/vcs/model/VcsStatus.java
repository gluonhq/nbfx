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
import java.util.Map;
import java.util.TreeMap;

/**
 * The changed files of a repository, read once. A status view shows {@link #files(VcsCompareMode)} for
 * the mode it compares; switching the mode needs no new read.
 *
 * @param files every file that differs in some mode, ordered by path
 * @since 1.1
 */
public record VcsStatus(List<VcsFileStatus> files) {

    /** A repository without changes. */
    public static final VcsStatus CLEAN = new VcsStatus(List.of());

    public VcsStatus {
        files = List.copyOf(files);
    }

    /**
     * The files that differ between the two sides of {@code mode}, one for each
     * {@link VcsFileStatus#path(VcsCompareMode) path of that mode} and in the order of those paths.
     *
     * <p>A file renamed in the index, deleted from disk and written again where it was is two files
     * to git, the renamed one and an untracked one. Between the last commit and the working tree
     * they are one: both sides have it, so it is listed once, as
     * {@link VcsChangeKind#MODIFIED}.
     */
    public List<VcsFileStatus> files(VcsCompareMode mode) {
        Map<String, VcsFileStatus> byPath = new TreeMap<>();
        for (VcsFileStatus file : files) {
            if (file.change(mode).isPresent()) {
                String path = file.path(mode);
                // Out of the index, yet on disk: what the file is between the last commit and the disk.
                byPath.merge(path, file, (first, second) -> first.conflicted() || second.conflicted()
                        ? new VcsFileStatus(path, null, null, null, true)
                        : new VcsFileStatus(path, null, VcsChangeKind.DELETED, VcsChangeKind.UNTRACKED, false));
            }
        }
        return List.copyOf(byPath.values());
    }

    /** Whether a file has an unresolved merge conflict: there is nothing to commit until none has. */
    public boolean hasConflicts() {
        return files.stream().anyMatch(VcsFileStatus::conflicted);
    }

    /** Whether no file differs in any mode. */
    public boolean isClean() {
        return files.isEmpty();
    }
}
