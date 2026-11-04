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

/**
 * Creates the concrete pane for a registered {@link WindowPane}. Products register a provider in
 * the lookup when the pane needs product services; a subclass can instead override
 * {@link ShellApplication#createPane(WindowPane)}. The editor pane is always resolved through the
 * content manager and never through a provider.
 *
 * @since 1.0
 */
public interface WindowPaneProvider {

    /** Priority among providers; the highest priority that returns a pane wins. */
    default int priority() {
        return 0;
    }

    /**
     * Creates the pane for {@code pane}, or returns {@code null} to let the next provider (or the
     * shell's default) handle it. The returned pane must carry {@link WindowPane#role() the declared
     * role}; the window composition and persisted layouts are keyed by it.
     *
     * @param pane the declaration to instantiate
     * @return the pane, or {@code null} to decline
     */
    NbfxTabPane createPane(WindowPane pane);
}
