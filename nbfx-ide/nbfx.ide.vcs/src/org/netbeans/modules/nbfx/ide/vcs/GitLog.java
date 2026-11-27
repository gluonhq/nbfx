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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Change;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Commit;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Criteria;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;

/**
 * Reads the history of a repository with the {@code git} command line: the commits a search asks
 * for, each with the files it changed. It reads on the calling thread, which must not be the one
 * that draws.
 * <p>
 * A merge is compared with its first parent, so that it lists what it brought to the line of
 * history it was merged into; a moved file is told as renamed.
 *
 * @since 1.4
 */
final class GitLog {

    private static final char COMMIT_START = '\u0001';
    private static final char MESSAGE_END = '\u0002';
    /** Id, parents, author, address, author time and message, between marks no path can hold. */
    private static final String FORMAT = "--format=%x01%H%x00%P%x00%an%x00%ae%x00%at%x00%B%x02";
    private static final int FIELDS = 6;
    private static final String FIRST_PARENT = "--diff-merges=first-parent";
    /** Messages are matched as said, not translated. */
    private static final Map<String, String> ENVIRONMENT = Map.of("LC_ALL", "C");

    private GitLog() {
    }

    /** The commits {@code criteria} asks for in the repository at {@code root}, the newest first. */
    static GitHistory read(Path root, Criteria criteria) throws IOException {
        return read(root, criteria, 0);
    }

    /**
     * As {@link #read(Path, Criteria)}, past the first {@code skip} commits of the search: the next
     * ones of a search that had {@link GitHistory#more() more}.
     *
     * @throws IOException with what git said, when it could not search: no repository, no such
     *         revision
     */
    static GitHistory read(Path root, Criteria criteria, int skip) throws IOException {
        if (criteria.limit() < 1) {
            return new GitHistory(List.of(), false);
        }
        GitClient.Result result = GitClient.result(root, ENVIRONMENT, arguments(criteria, skip));
        if (result.exitCode() != 0) {
            boolean fromHead = blank(criteria.revision()) && (blank(criteria.to()) || isDate(criteria.to()));
            if (fromHead && !hasCommits(root)) {
                // A repository just made: no history yet, and that is no failure.
                return new GitHistory(List.of(), false);
            }
            throw new IOException(result.output().strip());
        }
        List<Commit> commits = parse(result.output());
        // One more than the limit was asked for, to tell whether there is more.
        boolean more = commits.size() > criteria.limit();
        return new GitHistory(more ? commits.subList(0, criteria.limit()) : commits, more);
    }

    /**
     * All the files the commit {@code id} changed, whatever paths the search that found it was
     * for.
     */
    static List<Change> changes(Path root, String id) throws IOException {
        GitClient.Result result = GitClient.result(root, ENVIRONMENT, "diff-tree", "-r", "-z", "-M", "--root",
                "--no-commit-id", "--name-status", FIRST_PARENT, revision(id));
        if (result.exitCode() != 0) {
            throw new IOException(result.output().strip());
        }
        return parseChanges(result.output());
    }

    /** The arguments of the {@code git log} that answers {@code criteria}. */
    static String[] arguments(Criteria criteria, int skip) throws IOException {
        List<String> args = new ArrayList<>(List.of("--no-pager", "log", "-z", "-M", "--name-status", FIRST_PARENT,
                FORMAT, "-n", String.valueOf(criteria.limit() + 1L)));
        if (skip > 0) {
            args.add("--skip=" + skip);
        }
        if (!criteria.merges()) {
            args.add("--no-merges");
        }
        if (!blank(criteria.message()) || !blank(criteria.author())) {
            // As typed, not as a pattern, and whatever the case.
            args.add("--fixed-strings");
            args.add("--regexp-ignore-case");
        }
        if (!blank(criteria.message())) {
            args.add("--grep=" + criteria.message().strip());
        }
        if (!blank(criteria.author())) {
            args.add("--author=" + criteria.author().strip());
        }
        if (isDate(criteria.from())) {
            args.add("--since=" + criteria.from().strip() + " 00:00:00");
        }
        if (isDate(criteria.to())) {
            args.add("--until=" + criteria.to().strip() + " 23:59:59");
        }
        // Searched from the newest commit wanted, or from the branch, or from what is checked out.
        if (!blank(criteria.to()) && !isDate(criteria.to())) {
            args.add(revision(criteria.to()));
        } else {
            args.add(blank(criteria.revision()) ? "HEAD" : revision(criteria.revision()));
        }
        if (!blank(criteria.from()) && !isDate(criteria.from())) {
            // Down to the oldest wanted, itself included: not what it follows.
            args.add("--not");
            args.add(revision(criteria.from()) + "^@");
            args.add("--not");
        }
        args.add("--");
        args.addAll(criteria.paths());
        return args.toArray(String[]::new);
    }

    /** {@code text} as a revision git is given: not something it would take for an option. */
    private static String revision(String text) throws IOException {
        String revision = text.strip();
        if (revision.startsWith("-")) {
            throw new IOException("Not a revision: " + revision);
        }
        return revision;
    }

    /** Whether {@code text} is a day, as {@code YYYY-MM-DD}, and not a revision. */
    static boolean isDate(String text) {
        if (blank(text) || !text.strip().matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            LocalDate.parse(text.strip());
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static boolean hasCommits(Path root) {
        GitClient.Result head = GitClient.result(root, ENVIRONMENT, "rev-parse", "--quiet", "--verify", "HEAD");
        // Said with 1 and nothing else when there is none; anything else is a failure to tell.
        return !(head.exitCode() == 1 && head.output().isBlank());
    }

    /** The commits in the output of the {@code git log} of {@link #arguments}. */
    static List<Commit> parse(String output) throws IOException {
        List<Commit> commits = new ArrayList<>();
        int start = output.indexOf(COMMIT_START);
        while (start >= 0) {
            int next = output.indexOf(COMMIT_START, start + 1);
            commits.add(parseCommit(output.substring(start + 1, next < 0 ? output.length() : next)));
            start = next;
        }
        return commits;
    }

    private static Commit parseCommit(String record) throws IOException {
        int end = record.indexOf(MESSAGE_END);
        String[] fields = end < 0 ? new String[0] : record.substring(0, end).split("\u0000", FIELDS);
        if (fields.length != FIELDS) {
            throw new IOException("Unexpected git log record: " + record);
        }
        Instant date;
        try {
            date = Instant.ofEpochSecond(Long.parseLong(fields[4]));
        } catch (NumberFormatException ex) {
            throw new IOException("Unexpected git log date: " + fields[4], ex);
        }
        List<String> parents = fields[1].isBlank() ? List.of() : List.of(fields[1].split(" "));
        return new Commit(fields[0], parents, fields[2], fields[3], date, fields[5].stripTrailing(),
                parseChanges(record.substring(end + 1)));
    }

    /**
     * The changes in what git lists with {@code --name-status -z}: a status and a path, each ended
     * by a NUL, and two paths - where it was, where it is - for a file renamed or copied.
     */
    static List<Change> parseChanges(String output) throws IOException {
        List<Change> changes = new ArrayList<>();
        String[] tokens = output.split("\u0000");
        int i = 0;
        while (i < tokens.length) {
            // Between the message and the first status git leaves a NUL and a line end.
            String status = tokens[i++].strip();
            if (status.isEmpty()) {
                continue;
            }
            VcsChangeKind kind = kind(status);
            boolean moved = kind == VcsChangeKind.RENAMED || kind == VcsChangeKind.COPIED;
            if (i + (moved ? 1 : 0) >= tokens.length) {
                throw new IOException("Unexpected git status record: " + status);
            }
            String original = moved ? tokens[i++] : null;
            changes.add(new Change(kind, tokens[i++], original));
        }
        return changes;
    }

    private static VcsChangeKind kind(String status) throws IOException {
        return switch (status.charAt(0)) {
            case 'A' -> VcsChangeKind.ADDED;
            case 'M' -> VcsChangeKind.MODIFIED;
            case 'D' -> VcsChangeKind.DELETED;
            case 'R' -> VcsChangeKind.RENAMED;
            case 'C' -> VcsChangeKind.COPIED;
            case 'T' -> VcsChangeKind.TYPE_CHANGED;
            default -> throw new IOException("Unexpected git status: " + status);
        };
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
