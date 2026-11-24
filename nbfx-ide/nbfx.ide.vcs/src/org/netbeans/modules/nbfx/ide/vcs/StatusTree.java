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
import java.util.Map;
import java.util.TreeMap;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

/**
 * Arranges the files of a {@link VcsStatus} that differ in a compare mode as the rows of the status
 * table: one after the other, or under their folders. It knows no toolkit; the view turns the nodes
 * into tree items.
 *
 * @since 1.2
 */
final class StatusTree {

    /** A row and the rows under it. */
    record Node(StatusRow row, List<Node> children) {
    }

    private StatusTree() {
    }

    /** The changed files, in path order. */
    static List<Node> list(VcsStatus status, VcsCompareMode mode) {
        return status.files(mode).stream().map(file -> fileNode(file, mode)).toList();
    }

    /**
     * The changed files under their folders: in a folder, its folders come before its files, each by
     * name. A folder whose only content is one other folder shares its row ({@code src/main/java}).
     */
    static List<Node> tree(VcsStatus status, VcsCompareMode mode) {
        Dir root = new Dir("");
        for (VcsFileStatus file : status.files(mode)) {
            Dir dir = root;
            String path = file.path();
            int from = 0;
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', from)) {
                String folder = path.substring(0, slash);
                dir = dir.dirs.computeIfAbsent(path.substring(from, slash), name -> new Dir(folder));
                from = slash + 1;
            }
            dir.files.add(file);
        }
        return nodes(root, mode);
    }

    private static List<Node> nodes(Dir dir, VcsCompareMode mode) {
        List<Node> nodes = new ArrayList<>();
        for (Map.Entry<String, Dir> entry : dir.dirs.entrySet()) {
            StringBuilder name = new StringBuilder(entry.getKey());
            Dir folder = entry.getValue();
            while (folder.files.isEmpty() && folder.dirs.size() == 1) {
                Map.Entry<String, Dir> only = folder.dirs.firstEntry();
                name.append('/').append(only.getKey());
                folder = only.getValue();
            }
            nodes.add(new Node(new StatusRow.Folder(name.toString(), folder.path), nodes(folder, mode)));
        }
        for (VcsFileStatus file : dir.files) {
            nodes.add(fileNode(file, mode));
        }
        return List.copyOf(nodes);
    }

    private static Node fileNode(VcsFileStatus file, VcsCompareMode mode) {
        String path = file.path();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new Node(new StatusRow.File(name, file, file.change(mode).orElseThrow()), List.of());
    }

    /** A folder while the tree is built; the files arrive in path order, so they stay sorted. */
    private static final class Dir {
        final String path;
        final TreeMap<String, Dir> dirs = new TreeMap<>();
        final List<VcsFileStatus> files = new ArrayList<>();

        Dir(String path) {
            this.path = path;
        }
    }
}
