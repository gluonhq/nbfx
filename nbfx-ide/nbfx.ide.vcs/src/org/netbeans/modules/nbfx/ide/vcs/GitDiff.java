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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.Node;
import org.netbeans.api.nbfx.editor.FileIconProvider;
import org.netbeans.modules.nbfx.ide.diff.DiffSource;
import org.netbeans.modules.nbfx.ide.diff.DiffView;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Git &#9656; Diff: shows what changed in a file since the last commit, the content of
 * {@code HEAD} on the left and the file in the working tree on the right.
 *
 * @since 1.4
 */
final class GitDiff {

    private static final Logger LOG = Logger.getLogger(GitDiff.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-git-diff", 1);

    private GitDiff() {
    }

    /** Whether {@code file} is one a diff can be opened for: a file on disk in a working tree. */
    static boolean canDiff(FileObject file) {
        return file != null && file.isData() && GitMenus.inWorkingTree(file);
    }

    /** Opens the diff of {@code file} against the last commit. Must run on the FX thread. */
    static void open(FileObject file) {
        File onDisk = file == null || !file.isData() ? null : FileUtil.toFile(file);
        Path folder = onDisk == null || onDisk.getParentFile() == null ? null : onDisk.getParentFile().toPath();
        Path root = folder == null ? null : GitStatusCache.getDefault().rootOf(folder).orElse(null);
        if (root == null) {
            return;
        }
        String path = root.relativize(onDisk.toPath()).toString().replace(File.separatorChar, '/');
        RP.post(() -> {
            FileObject head;
            try {
                // A file the last commit does not have is compared with nothing.
                head = inMemory(file.getNameExt(), GitClient.headContent(root, path).orElse(""));
            } catch (IOException ex) {
                LOG.log(Level.INFO, "Cannot read HEAD:" + path, ex);
                return;
            }
            Platform.runLater(() -> DiffView.open(file, file.getNameExt(), icon(file),
                    new DiffSource(message("Git.diff.head"), head),
                    new DiffSource(message("Git.diff.workingTree"), file)));
        });
    }

    /** A file in memory named {@code name}, so that an editor colours it as the one on disk, holding {@code text}. */
    static FileObject inMemory(String name, String text) throws IOException {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData(name);
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static Node icon(FileObject file) {
        FileIconProvider icons = Lookup.getDefault().lookup(FileIconProvider.class);
        return icons == null ? null : icons.createIcon(file);
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitDiff.class, key);
    }
}
