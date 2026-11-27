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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the refs of a repository for the Repository Browser - its branches, tags, stashes and remotes,
 * and what is checked out - through the {@code git} command line. What git prints is parsed apart
 * from running it, a line at a time: no name of a ref holds a tab or a line break.
 *
 * @since 1.4
 */
final class GitRefs {

    private static final String LOCAL = "refs/heads/";
    private static final String REMOTE = "refs/remotes/";
    private static final String TAGS = "refs/tags/";
    /** Name, commit, whether checked out, the branch tracked and how far from it, and what a symbolic ref names. */
    private static final String BRANCH_FORMAT = "--format=%(refname)%09%(objectname)%09%(HEAD)%09%(upstream:short)"
            + "%09%(upstream:track)%09%(symref)";
    /** Name, the id of the ref, that of the commit an annotated tag names, and the first line of the message. */
    private static final String TAG_FORMAT = "--format=%(refname)%09%(objectname)%09%(*objectname)%09%(contents:subject)";
    /** Id, author with the address, the time of the commit in seconds, and the message; a NUL ends each but the last. */
    private static final String COMMIT_FORMAT = "--format=%H%x00%an <%ae>%x00%ct%x00%B";
    /** Whether a tag or what it names, its id, who made it, when in seconds, and the message; a NUL ends each but the last. */
    private static final String TAG_DETAILS_FORMAT = "--format=%(objecttype)%00%(objectname)%00%(taggername) %(taggeremail)"
            + "%00%(taggerdate:unix)%00%(contents)";
    private static final Pattern AHEAD = Pattern.compile("ahead (\\d+)");
    private static final Pattern BEHIND = Pattern.compile("behind (\\d+)");
    /** How much of a commit id names a head that is on no branch and has no tag. */
    private static final int HEAD_ID_LENGTH = 7;

    private GitRefs() {
    }

    /**
     * Reads the repository whose working tree is {@code root}.
     *
     * @throws IOException with what git said, when {@code root} is no repository or git cannot be run
     */
    static GitRepositoryInfo read(Path root) throws IOException {
        List<GitRepositoryInfo.Branch> local = new ArrayList<>();
        List<GitRepositoryInfo.Branch> remote = new ArrayList<>();
        parseBranches(git(root, "for-each-ref", BRANCH_FORMAT, LOCAL, REMOTE), local, remote);
        List<GitRepositoryInfo.Tag> tags = parseTags(git(root, "for-each-ref", TAG_FORMAT, TAGS));
        List<GitRepositoryInfo.Stash> stashes = parseStashes(git(root, "stash", "list", "--format=%gd%x09%H%x09%gs"));
        List<GitRepositoryInfo.Remote> remotes = parseRemotes(git(root, "remote", "-v"));
        return new GitRepositoryInfo(root, head(root, tags), local, remote, tags, stashes, remotes);
    }

    /**
     * The file that holds the configuration of the repository whose working tree is {@code root},
     * which is not in a {@code .git} folder there for every one: not for a linked working tree.
     *
     * @throws IOException with what git said, when {@code root} is no repository or git cannot be run
     */
    static Path configFile(Path root) throws IOException {
        return root.resolve(git(root, "rev-parse", "--git-path", "config").strip()).normalize();
    }

    /**
     * Reads the commit {@code revision} names, or the one the tag it names points to.
     *
     * @throws IOException with what git said, when there is no such commit or git cannot be run
     */
    static GitRepositoryInfo.Commit commit(Path root, String revision) throws IOException {
        return parseCommit(git(root, "show", "--no-patch", COMMIT_FORMAT, revision + "^{commit}", "--"));
    }

    /**
     * Reads what {@code tag} says of itself, or {@code null} for a tag that is a plain name of its commit.
     *
     * @throws IOException with what git said, when there is no such tag or git cannot be run
     */
    static GitRepositoryInfo.TagDetails tagDetails(Path root, GitRepositoryInfo.Tag tag) throws IOException {
        return parseTagDetails(git(root, "for-each-ref", TAG_DETAILS_FORMAT, tag.ref()));
    }

    /** The details of what {@code git for-each-ref} prints as {@link #TAG_DETAILS_FORMAT} has it. */
    static GitRepositoryInfo.TagDetails parseTagDetails(String output) throws IOException {
        String[] fields = output.split("\\u0000", 5);
        if (fields.length == 5 && !"tag".equals(fields[0])) {
            return null;
        }
        try {
            if (fields.length == 5) {
                return new GitRepositoryInfo.TagDetails(fields[1], fields[2],
                        Instant.ofEpochSecond(Long.parseLong(fields[3])), fields[4].strip());
            }
        } catch (NumberFormatException ex) {
            // No tag as asked for, then.
        }
        throw new IOException(output.strip());
    }

    /** The commit of what {@code git show} prints as {@link #COMMIT_FORMAT} has it. */
    static GitRepositoryInfo.Commit parseCommit(String output) throws IOException {
        String[] fields = output.split("\\u0000", 4);
        try {
            if (fields.length == 4) {
                return new GitRepositoryInfo.Commit(fields[0], fields[1],
                        Instant.ofEpochSecond(Long.parseLong(fields[2])), fields[3].strip());
            }
        } catch (NumberFormatException ex) {
            // No commit as asked for, then.
        }
        throw new IOException(output.strip());
    }

    /**
     * What is checked out: the branch - also one without a commit yet - or, on none, the tags of the
     * commit, or the start of its id.
     */
    private static String head(Path root, List<GitRepositoryInfo.Tag> tags) throws IOException {
        // The full ref: its short name is not that of the branch where a tag has the same one.
        GitClient.Result branch = GitClient.result(root, Map.of(), "symbolic-ref", "--quiet", "HEAD");
        if (branch.exitCode() == 0) {
            String ref = branch.output().trim();
            return ref.startsWith(LOCAL) ? ref.substring(LOCAL.length()) : ref;
        }
        return detachedHead(git(root, "rev-parse", "HEAD").trim(), tags);
    }

    /** What a head on no branch is called: the tags of its commit {@code id}, or the start of the id. */
    static String detachedHead(String id, List<GitRepositoryInfo.Tag> tags) {
        String names = tags.stream().filter(tag -> tag.id().equals(id)).map(GitRepositoryInfo.Tag::name)
                .collect(Collectors.joining(","));
        if (!names.isEmpty()) {
            return names;
        }
        return id.length() > HEAD_ID_LENGTH ? id.substring(0, HEAD_ID_LENGTH) + "..." : id;
    }

    /**
     * Adds the branches {@code output} lists to {@code local} and {@code remote}. The head of a
     * remote, {@code origin/HEAD}, is no branch of it but the name of one, and is left out.
     */
    static void parseBranches(String output, List<GitRepositoryInfo.Branch> local,
            List<GitRepositoryInfo.Branch> remote) {
        for (String line : lines(output)) {
            String[] fields = line.split("\t", -1);
            if (fields.length < 6 || !fields[5].isEmpty()) {
                continue;
            }
            String ref = fields[0];
            boolean isLocal = ref.startsWith(LOCAL);
            if (!isLocal && !ref.startsWith(REMOTE)) {
                continue;
            }
            String track = fields[4];
            // A branch whose upstream was deleted tracks nothing there is to compare it with.
            String upstream = fields[3].isEmpty() || track.contains("gone") ? null : fields[3];
            GitRepositoryInfo.Branch branch = new GitRepositoryInfo.Branch(
                    ref.substring((isLocal ? LOCAL : REMOTE).length()), fields[1], !isLocal, "*".equals(fields[2]),
                    upstream, count(AHEAD, track), count(BEHIND, track));
            (isLocal ? local : remote).add(branch);
        }
    }

    static List<GitRepositoryInfo.Tag> parseTags(String output) {
        List<GitRepositoryInfo.Tag> tags = new ArrayList<>();
        for (String line : lines(output)) {
            String[] fields = line.split("\t", 4);
            if (fields.length < 4 || !fields[0].startsWith(TAGS)) {
                continue;
            }
            // An annotated tag is an object of its own: what it names is the commit it points to.
            String id = fields[2].isEmpty() ? fields[1] : fields[2];
            tags.add(new GitRepositoryInfo.Tag(fields[0].substring(TAGS.length()), id, fields[3]));
        }
        return tags;
    }

    static List<GitRepositoryInfo.Stash> parseStashes(String output) {
        List<GitRepositoryInfo.Stash> stashes = new ArrayList<>();
        for (String line : lines(output)) {
            String[] fields = line.split("\t", 3);
            if (fields.length == 3) {
                stashes.add(new GitRepositoryInfo.Stash(fields[0], fields[1], fields[2]));
            }
        }
        return stashes;
    }

    /**
     * The remotes of {@code git remote -v}, which names each address with what it is for, and for a
     * partial clone with its filter after that: {@code origin <url> (fetch) [blob:none]}.
     */
    static List<GitRepositoryInfo.Remote> parseRemotes(String output) {
        Map<String, List<String>> fetch = new LinkedHashMap<>();
        Map<String, List<String>> push = new LinkedHashMap<>();
        for (String line : lines(output)) {
            int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            String name = line.substring(0, tab);
            String address = line.substring(tab + 1);
            fetch.computeIfAbsent(name, key -> new ArrayList<>());
            push.computeIfAbsent(name, key -> new ArrayList<>());
            int fetched = address.lastIndexOf(" (fetch)");
            int pushed = address.lastIndexOf(" (push)");
            if (fetched > 0) {
                fetch.get(name).add(address.substring(0, fetched));
            } else if (pushed > 0) {
                push.get(name).add(address.substring(0, pushed));
            }
        }
        List<GitRepositoryInfo.Remote> remotes = new ArrayList<>();
        fetch.forEach((name, urls) -> remotes.add(new GitRepositoryInfo.Remote(name, urls, push.get(name))));
        return remotes;
    }

    private static int count(Pattern pattern, String track) {
        Matcher matcher = pattern.matcher(track);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static List<String> lines(String output) {
        return output.lines().filter(line -> !line.isEmpty()).toList();
    }

    private static String git(Path root, String... args) throws IOException {
        GitClient.Result result = GitClient.result(root, Map.of(), args);
        if (result.exitCode() != 0) {
            throw new IOException(result.output().trim());
        }
        return result.output();
    }
}
