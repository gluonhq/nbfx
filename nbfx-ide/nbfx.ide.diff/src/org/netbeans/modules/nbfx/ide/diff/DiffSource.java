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

import java.util.Objects;
import org.openide.filesystems.FileObject;

/**
 * One of the two texts a diff view compares: a file, on disk or in memory, and what the view calls
 * it above its text - "HEAD", "Modified In Working Tree".
 *
 * @param title the heading of this side
 * @param file the file whose text is shown; its name tells the editor how to colour it
 * @param path where the file is in what is compared, with {@code /} between its folders - in a
 *        repository, from its root - for the unified diff to name it by, as {@code a/path} and
 *        {@code b/path}; null to have it named by the title
 * @since 1.0
 */
public record DiffSource(String title, FileObject file, String path) {

    /** The title and the file are required. */
    public DiffSource {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(file, "file");
    }

    /**
     * A text with no path, named by its title in the unified diff.
     *
     * @param title the heading of this side
     * @param file the file whose text is shown
     */
    public DiffSource(String title, FileObject file) {
        this(title, file, null);
    }

    /**
     * What the unified diff calls this side: its path after {@code prefix}, or its title.
     */
    String unifiedName(String prefix) {
        return path == null ? title : prefix + path;
    }
}
