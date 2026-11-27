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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    private static final int POLL_MILLIS = 50;
    /** Paths are meant as written: a {@code *} or a {@code :} in a name is part of the name. */
    private static final Map<String, String> LITERAL = Map.of("GIT_LITERAL_PATHSPECS", "1", "LC_ALL", "C");

    /** What is at a path: its kind as git names it, and the id of its object. */
    private record Entry(String type, String objectId) {
    }

    private GitContentReader() {
    }

    /**
     * The content the file at {@code path}, relative to {@code root}, has in the last commit.
     *
     * @param charset what the bytes git stores spell text in; see {@link GitClient#storedCharset}
     * @param maxBytes the largest file to read; a larger one is {@link GitContent.TooLarge}
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
            // No commit yet is no content, and no failure; a branch that cannot be read is one.
            return isUnborn(root, cancelled) ? new GitContent.Missing() : failed(listed);
        }
        Entry entry = null;
        for (String record : text(listed).split("\u0000")) {
            // "<mode> <type> <id>\t<path>"
            int tab = record.indexOf('\t');
            String[] fields = tab < 0 ? new String[0] : record.substring(0, tab).split(" ");
            if (fields.length == 3 && record.substring(tab + 1).equals(path)) {
                entry = new Entry(fields[1], fields[2]);
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
        for (String record : text(listed).split("\u0000")) {
            // "<mode> <id> <stage>\t<path>", and for a folder the files in it, which are not it.
            int tab = record.indexOf('\t');
            String[] fields = tab < 0 ? new String[0] : record.substring(0, tab).split(" ");
            if (fields.length != 3 || !record.substring(tab + 1).equals(path)) {
                continue;
            }
            if ("0".equals(fields[2])) {
                // A link to another repository has the mode of one, and its commit for an id.
                entry = new Entry("160000".equals(fields[0]) ? "commit" : "blob", fields[1]);
            } else {
                unmerged = true;
            }
        }
        if (entry == null) {
            return unmerged ? new GitContent.Unmerged() : new GitContent.Missing();
        }
        return read(root, entry, charset, maxBytes, cancelled);
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
            if (size[0] > maxBytes || size[0] > Integer.MAX_VALUE - 8) {
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
                return new GitContent.TooLarge(size[0], maxBytes);
            }
            if (isBinary(bytes)) {
                return new GitContent.Binary(entry.objectId(), bytes.length);
            }
            try {
                String text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                return new GitContent.Text(text, entry.objectId(), bytes.length);
            } catch (CharacterCodingException ex) {
                return new GitContent.Malformed(charset.name());
            }
        } catch (IOException ex) {
            String error = "";
            try {
                error = said.get(1, TimeUnit.SECONDS).strip();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
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
