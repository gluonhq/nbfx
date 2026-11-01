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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.List;
import org.openide.filesystems.FileObject;

/**
 * Supplies the tree state a view persists across sessions. A host view implements this so the
 * session restorer can save which nodes were expanded and selected, without the shell knowing the
 * view's model.
 */
public interface ViewStateProvider {

    /**
     * The paths of the nodes expanded under {@code root}.
     *
     * @param root the view's root
     * @return the expanded paths; never {@code null}
     */
    List<String> getExpandedPaths(FileObject root);

    /**
     * The path of the selected node under {@code root}, or {@code null} if none is selected.
     *
     * @param root the view's root
     * @return the selected path, or {@code null}
     */
    String getSelectedPath(FileObject root);
}
