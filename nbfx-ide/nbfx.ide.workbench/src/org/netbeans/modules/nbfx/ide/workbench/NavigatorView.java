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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.List;
import java.util.function.Consumer;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.platform.shell.ViewStateProvider;
import org.openide.filesystems.FileObject;

/**
 * An IDE navigator view (a project tree): a {@link ViewProvider} that takes part in project session
 * restore and project-load reporting.
 * <p>
 * The platform {@link ViewProvider} stays generic; only views that show a project tree implement this
 * IDE-tier interface.
 */
public interface NavigatorView extends ViewProvider, ViewStateProvider {

    /**
     * Reveals {@code file} in this view: expands to it and selects it.
     *
     * @param file the file to reveal and select
     */
    void revealFile(FileObject file);

    /**
     * Stashes node identifiers to expand for the project rooted at {@code root} once its tree has
     * (lazily) built, so a session can restore the expansion.
     *
     * @param root  the project's root folder
     * @param paths the node identifiers to expand
     */
    void restoreExpandedPaths(FileObject root, List<String> paths);

    /**
     * Stashes the identifier of the node to select for the project rooted at {@code root} once its
     * tree has (lazily) built, so a session can restore the selection.
     *
     * @param root the project's root folder
     * @param path the identifier of the node to select
     */
    void restoreSelectedPath(FileObject root, String path);

    /**
     * The identifiers of the currently expanded nodes of the project rooted at {@code root}.
     *
     * @param root the project's root folder
     * @return the expanded-node identifiers, never {@code null}
     */
    List<String> getExpandedPaths(FileObject root);

    /**
     * The identifier of the currently selected node within the project rooted at {@code root}, or
     * {@code null} when nothing is selected.
     *
     * @param root the project's root folder
     * @return the selected-node identifier, or {@code null}
     */
    String getSelectedPath(FileObject root);

    /**
     * Registers a callback invoked on the FX thread with a project's root once that project's tree
     * has finished loading.
     *
     * @param onProjectLoaded the callback to run when a project has loaded
     */
    void setOnProjectLoaded(Consumer<FileObject> onProjectLoaded);

    /**
     * Registers a callback invoked on the FX thread with a project's root when that project could not
     * be loaded.
     *
     * @param onProjectLoadFailed the callback to run when a project failed to load
     */
    void setOnProjectLoadFailed(Consumer<FileObject> onProjectLoadFailed);
}
