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

    /** The files that differ between the two sides of {@code mode}, in path order. */
    public List<VcsFileStatus> files(VcsCompareMode mode) {
        return files.stream().filter(file -> file.change(mode).isPresent()).toList();
    }

    /** Whether no file differs in any mode. */
    public boolean isClean() {
        return files.isEmpty();
    }
}
