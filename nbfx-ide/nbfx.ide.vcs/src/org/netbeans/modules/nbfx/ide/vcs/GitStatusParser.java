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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

/**
 * Reads the output of {@code git status --porcelain=v2 -z} into a {@link VcsStatus}. That format reports
 * the index and the working tree apart (the {@code XY} field), ends every record with a NUL and leaves
 * paths unquoted; a renamed or copied entry is followed by a second record with its original path.
 *
 * @since 1.1
 */
final class GitStatusParser {

    private GitStatusParser() {
    }

    /**
     * @param output the command's output
     * @throws IllegalArgumentException if a record is not in the porcelain v2 format
     */
    static VcsStatus parse(String output) {
        Map<String, VcsFileStatus> files = new LinkedHashMap<>();
        String[] records = output.split("\0");
        for (int i = 0; i < records.length; i++) {
            String record = records[i];
            if (record.isEmpty()) {
                continue;
            }
            switch (record.charAt(0)) {
                case '1' -> {
                    String[] fields = fields(record, 9);
                    add(files, changed(fields[8], null, fields[1]));
                }
                case '2' -> {
                    String[] fields = fields(record, 10);
                    if (++i == records.length) {
                        throw new IllegalArgumentException("No original path after: " + record);
                    }
                    add(files, changed(fields[9], records[i], fields[1]));
                }
                case 'u' -> add(files, new VcsFileStatus(fields(record, 11)[10], null, null, null, true));
                case '?' -> add(files, new VcsFileStatus(fields(record, 2)[1], null, null,
                        VcsChangeKind.UNTRACKED, false));
                // Ignored files and the branch headers are not changes.
                case '!', '#' -> {
                }
                default -> throw new IllegalArgumentException("Not a git status record: " + record);
            }
        }
        List<VcsFileStatus> sorted = new ArrayList<>(files.values());
        sorted.sort(Comparator.comparing(VcsFileStatus::path));
        return new VcsStatus(sorted);
    }

    /** The leading space-separated fields; the last one is the path, which may itself hold spaces. */
    private static String[] fields(String record, int count) {
        String[] fields = record.split(" ", count);
        if (fields.length < count) {
            throw new IllegalArgumentException("Not a git status record: " + record);
        }
        return fields;
    }

    private static VcsFileStatus changed(String path, String originalPath, String xy) {
        if (xy.length() != 2) {
            throw new IllegalArgumentException("Not a git status code: " + xy);
        }
        return new VcsFileStatus(path, originalPath, kind(xy.charAt(0)), kind(xy.charAt(1)), false);
    }

    private static VcsChangeKind kind(char code) {
        return switch (code) {
            case '.' -> null;
            case 'M' -> VcsChangeKind.MODIFIED;
            case 'T' -> VcsChangeKind.TYPE_CHANGED;
            case 'A' -> VcsChangeKind.ADDED;
            case 'D' -> VcsChangeKind.DELETED;
            case 'R' -> VcsChangeKind.RENAMED;
            case 'C' -> VcsChangeKind.COPIED;
            default -> throw new IllegalArgumentException("Not a git status code: " + code);
        };
    }

    /**
     * Git reports a file deleted from the index and an untracked file on disk at the same path as two
     * records; they are one file with a staged and an unstaged change.
     */
    private static void add(Map<String, VcsFileStatus> files, VcsFileStatus file) {
        files.merge(file.path(), file, (first, second) -> new VcsFileStatus(first.path(),
                first.originalPath() != null ? first.originalPath() : second.originalPath(),
                first.staged() != null ? first.staged() : second.staged(),
                first.unstaged() != null ? first.unstaged() : second.unstaged(),
                first.conflicted() || second.conflicted()));
    }
}
