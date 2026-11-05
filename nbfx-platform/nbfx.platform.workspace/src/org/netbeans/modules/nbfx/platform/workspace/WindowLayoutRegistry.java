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
package org.netbeans.modules.nbfx.platform.workspace;

import java.util.Optional;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Reads the first-run window layout declared in {@code NbFx/WindowLayouts}. User layouts still win
 * through the session restorer; this registry supplies the default composition before any persisted
 * state exists.
 *
 * @since 1.0
 */
public final class WindowLayoutRegistry {

    private static final Logger LOG = Logger.getLogger(WindowLayoutRegistry.class.getName());

    private WindowLayoutRegistry() {
    }

    /** The declared default tree, or empty when none is registered. */
    public static Optional<DockTree> defaultTree() {
        return find("default");
    }

    /** The layout with {@code id}, parsed from its layer entry. */
    public static Optional<DockTree> find(String id) {
        FileObject folder = FileUtil.getConfigFile(LayerPaths.WINDOW_LAYOUTS);
        FileObject file = folder == null ? null : folder.getFileObject(id);
        return read(file);
    }

    /** Reads a layout from {@code file}; package-visible for tests. */
    static Optional<DockTree> read(FileObject file) {
        Object value = file == null ? null : file.getAttribute("tree");
        if (!(value instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        DockTree tree = DockTrees.parse(text);
        if (tree == null) {
            LOG.warning("Malformed window layout tree: " + text);
        }
        return Optional.ofNullable(tree);
    }
}
