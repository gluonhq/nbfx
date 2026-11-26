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

import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;

/**
 * A row of the status table: a changed file, or a folder that groups changed files in the tree view.
 *
 * @since 1.2
 */
sealed interface StatusRow {

    /** What the File column shows. */
    String name();

    /** The path relative to the repository root, with {@code /} separators. */
    String path();

    /**
     * A folder; in the tree a chain of folders that hold nothing else is one row.
     *
     * @param name the folder name, or the names of the chain joined by {@code /}
     * @param path the path of the (last) folder
     */
    record Folder(String name, String path) implements StatusRow {
    }

    /**
     * A changed file.
     *
     * @param name the file name
     * @param path where the file is in the mode the table compares, which for a file renamed and then
     *        deleted is where it was
     * @param status the state of the file
     * @param kind how the file differs in the mode the table compares
     */
    record File(String name, String path, VcsFileStatus status, VcsChangeKind kind) implements StatusRow {
    }
}
