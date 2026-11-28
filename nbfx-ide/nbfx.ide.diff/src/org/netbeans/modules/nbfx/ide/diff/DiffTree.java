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
package org.netbeans.modules.nbfx.ide.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Arranges the files of a diff as the rows of its table: one after the other, or under their
 * folders, as the Git window arranges the changed files. It knows no toolkit; the view turns the
 * nodes into tree items.
 */
final class DiffTree {

    /** A row of the table. */
    sealed interface Row {

        /** What the File column shows. */
        String name();
    }

    /**
     * A folder; a chain of folders that hold nothing else is one row.
     *
     * @param name the folder name, or the names of the chain joined by {@code /}
     * @param path the path of the (last) folder, by which it is known again after a refresh
     */
    record Folder(String name, String path) implements Row {
    }

    /** A file that differs. */
    record File(DiffEntry entry) implements Row {

        @Override
        public String name() {
            return entry.name();
        }
    }

    /** A row and the rows under it. */
    record Node(Row row, List<Node> children) {
    }

    private DiffTree() {
    }

    /** The files, in the order given. */
    static List<Node> list(List<DiffEntry> entries) {
        return entries.stream().map(DiffTree::fileNode).toList();
    }

    /**
     * The files under the folders of their {@link DiffEntry#location() locations}, which are split
     * at {@code /}: in a folder, its folders come before its files, the folders by name and the
     * files in the order given. A folder whose only content is one other folder shares its row
     * ({@code src/main/java}).
     */
    static List<Node> tree(List<DiffEntry> entries) {
        Dir root = new Dir("");
        for (DiffEntry entry : entries) {
            Dir dir = root;
            String path = entry.location();
            int from = 0;
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', from)) {
                String folder = path.substring(0, slash);
                dir = dir.dirs.computeIfAbsent(path.substring(from, slash), name -> new Dir(folder));
                from = slash + 1;
            }
            dir.files.add(entry);
        }
        return nodes(root);
    }

    private static List<Node> nodes(Dir dir) {
        List<Node> nodes = new ArrayList<>();
        for (Map.Entry<String, Dir> entry : dir.dirs.entrySet()) {
            StringBuilder name = new StringBuilder(entry.getKey());
            Dir folder = entry.getValue();
            while (folder.files.isEmpty() && folder.dirs.size() == 1) {
                Map.Entry<String, Dir> only = folder.dirs.firstEntry();
                name.append('/').append(only.getKey());
                folder = only.getValue();
            }
            nodes.add(new Node(new Folder(name.toString(), folder.path), nodes(folder)));
        }
        for (DiffEntry file : dir.files) {
            nodes.add(fileNode(file));
        }
        return List.copyOf(nodes);
    }

    private static Node fileNode(DiffEntry entry) {
        return new Node(new File(entry), List.of());
    }

    /** A folder while the tree is built. */
    private static final class Dir {
        final String path;
        final TreeMap<String, Dir> dirs = new TreeMap<>();
        final List<DiffEntry> files = new ArrayList<>();

        Dir(String path) {
            this.path = path;
        }
    }
}
