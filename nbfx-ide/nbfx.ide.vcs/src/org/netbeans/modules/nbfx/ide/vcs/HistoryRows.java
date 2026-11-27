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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Change;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Commit;

/**
 * The rows of the History view's summary: each commit that passes the filter, the files it changed
 * below it while it is expanded, and at the end a row to ask for more when the search has more. It
 * arranges the commits it is given and reads nothing.
 *
 * @since 1.4
 */
final class HistoryRows {

    /** What of a commit the filter text is looked for in. */
    enum Field {
        /** Anything below. */
        ALL,
        /** Its message. */
        MESSAGE,
        /** The name or the address of its author. */
        AUTHOR,
        /** Its id. */
        COMMIT,
        /** The path of a file it changed. */
        FILE
    }

    /** A row of the summary. */
    sealed interface Row permits CommitRow, FileRow, MoreRow {
    }

    /** A commit; {@code expanded} when its whole message and its files are shown. */
    record CommitRow(Commit commit, boolean expanded) implements Row {
    }

    /** A file {@code commit} changed. */
    record FileRow(Commit commit, Change change) implements Row {
    }

    /** The search found more commits than its limit: asks for the next ones. */
    record MoreRow() implements Row {
    }

    private HistoryRows() {
    }

    /**
     * The rows for {@code commits}, in their order.
     *
     * @param expanded the ids of the commits whose files are shown
     * @param changesOf the files to show for a commit: those the search listed, or all of them
     * @param field where {@code contains} is looked for
     * @param contains the filter text, whatever the case; {@code null} or blank for no filter
     * @param more whether the search has more commits
     */
    static List<Row> of(List<Commit> commits, Set<String> expanded, Function<Commit, List<Change>> changesOf,
            Field field, String contains, boolean more) {
        List<Row> rows = new ArrayList<>();
        for (Commit commit : commits) {
            List<Change> changes = changesOf.apply(commit);
            if (!matches(commit, changes, field, contains)) {
                continue;
            }
            boolean open = expanded.contains(commit.id());
            rows.add(new CommitRow(commit, open));
            if (open) {
                changes.forEach(change -> rows.add(new FileRow(commit, change)));
            }
        }
        if (more) {
            rows.add(new MoreRow());
        }
        return rows;
    }

    /** Whether {@code commit}, with the files shown for it, has {@code contains} in {@code field}. */
    static boolean matches(Commit commit, List<Change> changes, Field field, String contains) {
        if (contains == null || contains.isBlank()) {
            return true;
        }
        String text = contains.strip().toLowerCase(Locale.ROOT);
        return switch (field) {
            case MESSAGE -> has(commit.message(), text);
            case AUTHOR -> has(commit.author(), text) || has(commit.email(), text);
            case COMMIT -> has(commit.id(), text);
            case FILE -> changes.stream().anyMatch(change -> hasPath(change, text));
            case ALL -> has(commit.message(), text) || has(commit.author(), text) || has(commit.email(), text)
                    || has(commit.id(), text) || changes.stream().anyMatch(change -> hasPath(change, text));
        };
    }

    private static boolean hasPath(Change change, String text) {
        return has(change.path(), text) || (change.originalPath() != null && has(change.originalPath(), text));
    }

    private static boolean has(String value, String text) {
        return value.toLowerCase(Locale.ROOT).contains(text);
    }
}
