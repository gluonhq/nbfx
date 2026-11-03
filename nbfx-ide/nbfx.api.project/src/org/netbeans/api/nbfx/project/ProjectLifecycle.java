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
package org.netbeans.api.nbfx.project;

/**
 * Closes open projects and moves the selection between them. The implementation belongs to the
 * running IDE shell: closing a project also handles unsaved documents, detached tabs, loading,
 * session state and persistence, which a command module must not duplicate.
 * <p>
 * Opening a project by root is {@link ProjectOpener#open(org.openide.filesystems.FileObject)}.
 *
 * @since 1.0
 */
public interface ProjectLifecycle {

    /**
     * Closes {@code project}, confirming unsaved changes and dropping its editor tabs and persisted
     * state. Unknown or {@code null} projects are ignored.
     *
     * @param project the project to close
     */
    void close(OpenProject project);

    /** Closes every open project, confirming unsaved changes once. */
    void closeAll();

    /** Selects and reveals the project after the selected one, wrapping around. */
    void selectNext();

    /** Selects and reveals the project before the selected one, wrapping around. */
    void selectPrevious();
}
