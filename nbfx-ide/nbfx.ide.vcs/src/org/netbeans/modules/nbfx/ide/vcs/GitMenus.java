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
import java.nio.file.Path;
import org.netbeans.api.nbfx.project.OpenProject;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * What the Git menus need to know of a file: the working tree it is in, told from the {@code .git}
 * entry of a parent folder, and the folder a repository for it would be created in. A menu is built
 * every time it opens and for every node, so it starts no git process; the view that Show Changes
 * opens asks git, and says so where git knows no repository.
 *
 * @since 1.2
 */
final class GitMenus {

    private GitMenus() {
    }

    /** Whether {@code file} is in a folder that has a {@code .git} entry, or under one. */
    static boolean inWorkingTree(FileObject file) {
        return file != null && inWorkingTree(FileUtil.toFile(file));
    }

    /** Whether {@code file} is in a folder that has a {@code .git} entry, or under one. */
    static boolean inWorkingTree(File file) {
        return workingTreeOf(file) != null;
    }

    /** The nearest folder at or above {@code file} that has a {@code .git} entry, or {@code null}. */
    static Path workingTreeOf(File file) {
        for (File folder = file; folder != null; folder = folder.getParentFile()) {
            if (new File(folder, ".git").exists()) {
                return folder.toPath();
            }
        }
        return null;
    }

    /**
     * The folder whose changes Show Changes on {@code file} shows: {@code folder}, that of its
     * project, when the file is in it, else the working tree the file is in - never a project the
     * file is not part of, nor what the view showed before.
     *
     * @param folder the folder of the project of {@code file}, or {@code null} without one
     * @return the folder, or {@code null} when the file is in neither
     */
    static Path changesFolder(FileObject file, Path folder) {
        File own = file == null ? null : FileUtil.toFile(file);
        if (folder != null && (own == null || own.toPath().startsWith(folder))) {
            return folder;
        }
        return workingTreeOf(own);
    }

    /** The folder of {@code project}, or {@code null} without one. */
    static Path folderOf(OpenProject project) {
        File folder = project == null ? null : FileUtil.toFile(project.getRoot());
        return folder == null ? null : folder.toPath();
    }

    /**
     * The folder proposed for the repository of {@code file}: {@code folder}, that of its project,
     * when the file is in it, else the file's own folder.
     *
     * @param folder the folder of the project of {@code file}, or {@code null} without one
     */
    static Path proposedRoot(FileObject file, Path folder) {
        if (file == null) {
            return folder;
        }
        File own = FileUtil.toFile(file.isFolder() ? file : file.getParent());
        if (folder != null && (own == null || own.toPath().startsWith(folder))) {
            return folder;
        }
        return own == null ? null : own.toPath();
    }
}
