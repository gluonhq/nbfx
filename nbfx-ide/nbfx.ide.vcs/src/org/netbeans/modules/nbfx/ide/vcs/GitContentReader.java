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

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.LinkOption;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Reads the content git has of a file, in the last commit or in the index, as a
 * {@link GitContent}. What is at the path is looked up once - its kind and the id of its object -
 * and that object is then read by its id, so that the content is of one moment even while the
 * branch or the index moves. The size of the object is known before any of it is read, and
 * nothing is read of one larger than the caller wants. A read that is cancelled, or whose thread
 * is interrupted, ends the git process it runs.
 * <p>
 * It reads on the calling thread, which waits for git: not the thread that draws, and not one
 * other work queues behind.
 *
 * @since 1.3
 */
final class GitContentReader {

    /** How far into a file git looks for a NUL to call it binary. */
    private static final int BINARY_DETECTION_BYTES = 8000;
    /** The largest content read, whatever is asked for: what an array can hold. */
    static final long MOST_BYTES = Integer.MAX_VALUE - 8;
    /** How git ends when it is asked of an object it does not have, and what it says of {@code HEAD} then. */
    private static final int NO_SUCH_OBJECT = 128;
    private static final String NO_HEAD = "Not a valid object name HEAD";
    /** Paths are meant as written: a {@code *} or a {@code :} in a name is part of the name. */
    private static final Map<String, String> LITERAL = Map.of("GIT_LITERAL_PATHSPECS", "1", "LC_ALL", "C");

    /** What is at a path: its kind as git names it, and the id of its object. */
    private record Entry(String type, String objectId, String mode) {
    }

    /** The modes git gives a file, one that can be run, and a link. */
    static final String FILE_MODE = "100644";
    static final String EXECUTABLE_MODE = "100755";
    static final String LINK_MODE = "120000";
    /** How much of a working tree file is read between two looks at whether it is still wanted. */
    private static final int PIECE = 1 << 16;

    private GitContentReader() {
    }

    /**
     * The content the file at {@code path}, relative to {@code root}, has in the last commit.
     *
     * @param charset what the bytes git stores spell text in; see {@link GitClient#storedCharset}
     * @param maxBytes the largest file to read; a larger one is {@link GitContent.TooLarge}, as is
     *        one of more than {@link #MOST_BYTES}, the limit it is then told with
     * @param cancelled whether the content is no longer wanted; asked while git is waited for
     * @throws CancellationException when {@code cancelled} answered {@code true}, or the thread
     *         was interrupted, which it stays
     */
    static GitContent head(Path root, String path, Charset charset, long maxBytes, BooleanSupplier cancelled) {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(cancelled, "cancelled");
        GitClient.Output listed = GitClient.run(root, LITERAL, cancelled, "ls-tree", "-z", "--full-tree", "HEAD",
                "--", path);
        if (listed.exitCode() != 0) {
            // No commit yet is no content, and no failure - but only when that is what git said:
            // a command that could not run, or a branch that cannot be read, is a failure.
            boolean noHead = listed.exitCode() == NO_SUCH_OBJECT && listed.error().contains(NO_HEAD);
            return noHead && isUnborn(root, cancelled) ? new GitContent.Missing() : failed(listed);
        }
        Entry entry = null;
        for (String[] fields : entries(listed.output(), path)) {
            // "<mode> <type> <id>"
            if (fields.length == 3) {
                entry = new Entry(fields[1], fields[2], fields[0]);
            }
        }
        return entry == null ? new GitContent.Missing() : read(root, entry, charset, maxBytes, cancelled);
    }

    /**
     * The content the file at {@code path}, relative to {@code root}, has in the index - what the
     * next commit takes - as {@link #head} tells that of the commit. A file in conflict has none:
     * it is {@link GitContent.Unmerged}.
     */
    static GitContent index(Path root, String path, Charset charset, long maxBytes, BooleanSupplier cancelled) {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(cancelled, "cancelled");
        // Not ":path", which git reads as ":<stage>:path" for a name that starts like one.
        GitClient.Output listed = GitClient.run(root, LITERAL, cancelled, "ls-files", "-s", "-z", "--", path);
        if (listed.exitCode() != 0) {
            return failed(listed);
        }
        Entry entry = null;
        boolean unmerged = false;
        // "<mode> <id> <stage>"; for a folder git lists the files in it, which are not it.
        for (String[] fields : entries(listed.output(), path)) {
            if (fields.length != 3) {
                continue;
            }
            if ("0".equals(fields[2])) {
                // A link to another repository has the mode of one, and its commit for an id.
                entry = new Entry("160000".equals(fields[0]) ? "commit" : "blob", fields[1], fields[0]);
            } else {
                unmerged = true;
            }
        }
        if (entry == null) {
            return unmerged ? new GitContent.Unmerged() : new GitContent.Missing();
        }
        return read(root, entry, charset, maxBytes, cancelled);
    }

    /**
     * The fields before the tab of the records of {@code listing} - {@code NUL}-ended, each
     * "{@code <fields> TAB <path>}" - that are of {@code path} exactly. The paths are compared as
     * the bytes they are: a name that is no UTF-8 is not taken for the one it would be read as.
     */
    static List<String[]> entries(byte[] listing, String path) {
        byte[] wanted = path.getBytes(StandardCharsets.UTF_8);
        List<String[]> entries = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= listing.length; i++) {
            if (i < listing.length && listing[i] != 0) {
                continue;
            }
            int tab = start;
            while (tab < i && listing[tab] != '\t') {
                tab++;
            }
            if (tab < i && Arrays.equals(listing, tab + 1, i, wanted, 0, wanted.length)) {
                entries.add(new String(listing, start, tab - start, StandardCharsets.US_ASCII).split(" "));
            }
            start = i + 1;
        }
        return entries;
    }

    /**
     * The content {@code file} has on disk, in the working tree, told as that of the commit and
     * the index is and with the same care: nothing is read of a file larger than
     * {@code maxBytes}, a binary file is no text, and bytes that are no text in {@code charset}
     * are not made one. Its text has no object id: git has not stored it.
     */
    static GitContent workingTree(Path file, Charset charset, long maxBytes) {
        return workingTree(file, charset, maxBytes, () -> false);
    }

    /**
     * As {@link #workingTree(Path, Charset, long)}, given up when {@code cancelled} says the
     * content is no longer wanted: asked before the file is touched and between the pieces it is
     * read in.
     * <p>
     * A link is not followed: its content is where it points to, as text, which is what git
     * stores of it - not the content of the file it points to, and not nothing when it points
     * nowhere.
     *
     * @throws CancellationException when {@code cancelled} answered {@code true}
     */
    static GitContent workingTree(Path file, Charset charset, long maxBytes, BooleanSupplier cancelled) {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(cancelled, "cancelled");
        if (cancelled.getAsBoolean()) {
            throw new CancellationException();
        }
        try {
            if (Files.isSymbolicLink(file)) {
                String target = Files.readSymbolicLink(file).toString();
                return new GitContent.Text(target, null, target.getBytes(StandardCharsets.UTF_8).length, LINK_MODE);
            }
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                return new GitContent.Missing();
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return new GitContent.NotAFile(Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) ? "tree" : "other");
            }
            long limit = Math.min(maxBytes, MOST_BYTES);
            long size = Files.size(file);
            if (size > limit) {
                return new GitContent.TooLarge(size, limit);
            }
            // One byte beyond the limit at most, however much the file grew since it was measured.
            long most = Math.min(limit + 1, Integer.MAX_VALUE - 8);
            ByteArrayOutputStream read = new ByteArrayOutputStream((int) Math.min(size + 1, most));
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] piece = new byte[PIECE];
                while (read.size() < most) {
                    if (cancelled.getAsBoolean()) {
                        throw new CancellationException();
                    }
                    int count = in.read(piece, 0, (int) Math.min(piece.length, most - read.size()));
                    if (count < 0) {
                        break;
                    }
                    read.write(piece, 0, count);
                }
            }
            byte[] bytes = read.toByteArray();
            if (bytes.length > limit) {
                return new GitContent.TooLarge(Math.max(Files.size(file), bytes.length), limit);
            }
            GitContent content = decoded(bytes, null, charset);
            return content instanceof GitContent.Text text ? text.withMode(modeOnDisk(file)) : content;
        } catch (CancellationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            return new GitContent.Failed(String.valueOf(ex.getMessage()));
        }
    }

    /**
     * The mode git would give the file at {@code file}: {@code 100755} for one its owner can run,
     * {@code 100644} for another; {@code null} where the disk does not tell - it keeps no such
     * permission - so that nothing is made of it.
     */
    private static String modeOnDisk(Path file) {
        try {
            PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                return null;
            }
            return view.readAttributes().permissions().contains(PosixFilePermission.OWNER_EXECUTE)
                    ? EXECUTABLE_MODE : FILE_MODE;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /** {@code bytes} as text in {@code charset}, or what they are instead: binary, or no text in it. */
    private static GitContent decoded(byte[] bytes, String objectId, Charset charset) {
        if (isBinary(bytes)) {
            return new GitContent.Binary(objectId, bytes.length);
        }
        try {
            String text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return new GitContent.Text(text, objectId, bytes.length);
        } catch (CharacterCodingException ex) {
            return new GitContent.Malformed(charset.name());
        }
    }

    /** Whether the branch checked out has no commit yet, as in a repository just made. */
    private static boolean isUnborn(Path root, BooleanSupplier cancelled) {
        GitClient.Output branch = GitClient.run(root, LITERAL, cancelled, "symbolic-ref", "--quiet", "HEAD");
        if (branch.exitCode() != 0 || text(branch).isBlank()) {
            return false;
        }
        // Lists the branch if there is one, and warns of one it cannot read.
        GitClient.Output refs = GitClient.run(root, LITERAL, cancelled, "for-each-ref", "--count=1",
                text(branch).strip());
        return refs.exitCode() == 0 && text(refs).isBlank() && refs.error().isBlank();
    }

    private static GitContent read(Path root, Entry entry, Charset charset, long maxBytes,
            BooleanSupplier cancelled) {
        if (!"blob".equals(entry.type())) {
            return new GitContent.NotAFile(entry.type());
        }
        if (cancelled.getAsBoolean()) {
            throw new CancellationException();
        }
        Process process;
        try {
            process = GitClient.start(root, LITERAL, "cat-file", "--batch");
        } catch (IOException ex) {
            return new GitContent.Failed(String.valueOf(ex.getMessage()));
        }
        // No more than can be held at once, whatever the caller would take.
        long limit = Math.min(maxBytes, MOST_BYTES);
        // What git says beside its answer explains a failure, and is read for that only: an
        // answer that is whole needs none of it, and is not doubted for it.
        Future<String> said = GitClient.READERS.submit(
                () -> new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        // The answer is read as it comes: its first line says how large the object is, and the
        // rest is left unread, and git ended, when that is more than is wanted.
        long[] size = {-1};
        Future<byte[]> answer = GitClient.READERS.submit(() -> {
            try (OutputStream ask = process.getOutputStream()) {
                ask.write((entry.objectId() + "\n").getBytes(StandardCharsets.US_ASCII));
            }
            InputStream in = process.getInputStream();
            // "<id> <type> <size>", or "<id> missing".
            String[] header = line(in).split(" ");
            if (header.length != 3 || !"blob".equals(header[1])) {
                throw new IOException("Unexpected answer of git cat-file: " + String.join(" ", header));
            }
            size[0] = Long.parseLong(header[2]);
            if (size[0] > limit) {
                return null;
            }
            byte[] bytes = in.readNBytes((int) size[0]);
            if (bytes.length != size[0]) {
                throw new IOException("git cat-file ended after " + bytes.length + " of " + size[0] + " bytes");
            }
            return bytes;
        });
        try {
            byte[] bytes = GitClient.await(answer, process, cancelled);
            if (bytes == null) {
                return new GitContent.TooLarge(size[0], limit);
            }
            GitContent content = decoded(bytes, entry.objectId(), charset);
            return content instanceof GitContent.Text text ? text.withMode(entry.mode()) : content;
        } catch (IOException ex) {
            String error = "";
            try {
                error = said.get(1, TimeUnit.SECONDS).strip();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancellationException();
            } catch (ExecutionException | TimeoutException unread) {
                // What failed says enough.
            }
            return new GitContent.Failed(error.isEmpty() ? String.valueOf(ex.getMessage()) : error);
        } finally {
            // Nothing more is asked of it: ended if it still runs, as after a file too large.
            process.destroyForcibly();
            said.cancel(true);
        }
    }

    /** The bytes up to the next line end of {@code in}, as text; the header of an answer is ASCII. */
    private static String line(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int read = in.read(); read != '\n'; read = in.read()) {
            if (read < 0 || line.length() > 200) {
                throw new IOException("Unexpected answer of git cat-file: " + line);
            }
            line.append((char) read);
        }
        return line.toString();
    }

    /** As git tells a binary file from a text one: by a NUL among its first bytes. */
    static boolean isBinary(byte[] content) {
        int looked = Math.min(content.length, BINARY_DETECTION_BYTES);
        for (int i = 0; i < looked; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static GitContent failed(GitClient.Output output) {
        return new GitContent.Failed(output.error().strip());
    }

    private static String text(GitClient.Output output) {
        return new String(output.output(), StandardCharsets.UTF_8);
    }
}
