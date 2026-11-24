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
 * @since 1.0
 */
public record DiffSource(String title, FileObject file) {

    /** Both are required. */
    public DiffSource {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(file, "file");
    }
}
