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

import java.time.Instant;
import java.util.List;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;

/**
 * What a search of the history of a repository found: the commits, the newest first, and whether
 * there are more than were asked for.
 *
 * @param commits the commits found, no more than the limit of the search
 * @param more whether the search would have found more commits with a higher limit
 * @since 1.4
 */
record GitHistory(List<Commit> commits, boolean more) {

    /** How many characters of the id of a commit tell it to the user, as git abbreviates it. */
    private static final int SHORT_ID_LENGTH = 7;

    GitHistory {
        commits = List.copyOf(commits);
    }

    /**
     * One commit.
     *
     * @param id its full id
     * @param parents the ids of the commits it follows: none for the first of a repository, two or
     *        more for a merge
     * @param author who wrote the change
     * @param email the address of the author
     * @param date when the author wrote it
     * @param message the whole message, without the line ends git adds after it
     * @param changes the files it changed, compared with its first parent; when the search was
     *        for some paths, only those among them
     */
    record Commit(String id, List<String> parents, String author, String email, Instant date, String message,
            List<Change> changes) {

        Commit {
            parents = List.copyOf(parents);
            changes = List.copyOf(changes);
        }

        /** The start of the id, as the user is told a commit. */
        String shortId() {
            return id.length() <= SHORT_ID_LENGTH ? id : id.substring(0, SHORT_ID_LENGTH);
        }

        /** The first line of the message: what the commit is called in a list. */
        String summary() {
            int end = message.indexOf('\n');
            return (end < 0 ? message : message.substring(0, end)).strip();
        }

        /** Whether the commit joins two or more lines of history. */
        boolean isMerge() {
            return parents.size() > 1;
        }
    }

    /**
     * A file a commit changed.
     *
     * @param kind how: added, modified, deleted, renamed, copied, or changed in type
     * @param path the file, relative to the repository root, with {@code /} between its folders
     * @param originalPath where a renamed file was, or what a copied one was copied from; else
     *        {@code null}
     */
    record Change(VcsChangeKind kind, String path, String originalPath) {
    }

    /**
     * What to search the history for. Every text may be {@code null} or blank for no such limit.
     *
     * @param revision the branch, tag or commit whose history is searched; that checked out if none
     * @param message text the message of a commit holds, whatever the case of its letters
     * @param author text the name or the address of the author holds, whatever the case
     * @param from the oldest commit wanted: a revision, itself included, or a date as
     *        {@code YYYY-MM-DD}, that day included
     * @param to the newest commit wanted: a revision or a date, as {@code from}
     * @param limit how many commits to find at most; none for less than one
     * @param merges whether the commits that merge others are wanted too
     * @param paths the files and folders, relative to the repository root, whose changes are
     *        searched; all the repository if empty
     */
    record Criteria(String revision, String message, String author, String from, String to, int limit,
            boolean merges, List<String> paths) {

        /** How many commits a search finds unless told otherwise, as in NetBeans. */
        static final int DEFAULT_LIMIT = 50;

        Criteria {
            paths = List.copyOf(paths);
        }

        /** The last commits of what is checked out that changed {@code paths}, merges included. */
        static Criteria of(List<String> paths) {
            return new Criteria(null, null, null, null, null, DEFAULT_LIMIT, true, paths);
        }
    }
}
