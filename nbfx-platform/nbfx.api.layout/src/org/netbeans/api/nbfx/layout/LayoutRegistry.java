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
package org.netbeans.api.nbfx.layout;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Discovers declarative product layouts from {@code NbFx/Layouts}. Malformed entries are skipped and
 * reported to the log; they do not prevent other layouts from loading.
 *
 * @since 1.0
 */
public final class LayoutRegistry {

    private static final Logger LOG = Logger.getLogger(LayoutRegistry.class.getName());

    private LayoutRegistry() {
    }

    /** All registered layouts, ordered by stable id. */
    public static List<LayoutSpec> discover() {
        return read(FileUtil.getConfigFile(LayerPaths.LAYOUTS));
    }

    /** The layout with {@code id}, if it is registered and valid. */
    public static Optional<LayoutSpec> find(String id) {
        if (id == null || id.isBlank() || id.indexOf('/') >= 0 || id.indexOf('\\') >= 0) {
            return Optional.empty();
        }
        FileObject folder = FileUtil.getConfigFile(LayerPaths.LAYOUTS);
        return readFile(folder == null ? null : folder.getFileObject(id, "xml"));
    }

    /** Reads layout XML files below {@code folder}; package-visible for tests. */
    static List<LayoutSpec> read(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<LayoutSpec> result = new ArrayList<>();
        for (FileObject file : folder.getChildren()) {
            if (file.isData() && "xml".equals(file.getExt())) {
                readFile(file).ifPresent(result::add);
            }
        }
        result.sort(Comparator.comparing(LayoutSpec::id));
        return List.copyOf(result);
    }

    /** Reads one layout XML file; package-visible for tests. */
    static Optional<LayoutSpec> readFile(FileObject file) {
        if (file == null || !file.isData() || !"xml".equals(file.getExt())) {
            return Optional.empty();
        }
        try (InputStream input = file.getInputStream()) {
            LayoutSpec layout = LayoutXml.read(input);
            if (!file.getName().equals(layout.id())) {
                LOG.warning("Layout file " + file.getPath() + " declares id " + layout.id());
                return Optional.empty();
            }
            return Optional.of(layout);
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warning("Cannot read layout " + file.getPath() + ": " + ex.getMessage());
            return Optional.empty();
        }
    }
}
