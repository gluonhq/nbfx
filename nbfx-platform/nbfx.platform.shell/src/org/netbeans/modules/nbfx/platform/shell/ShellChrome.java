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

import javafx.scene.Node;

/**
 * The window chrome a product contributes to {@link ShellApplication}: the nodes above and below the
 * docking area. The shell owns the composition and lifecycle; a product only supplies the chrome.
 * <p>
 * The contract deliberately exposes only {@link #top()}/{@link #bottom()}: concrete menu bars,
 * tool-bar containers and status bars stay inside the platform's default implementation. A product
 * that wants different chrome implements this interface; it does not need to subclass the shell.
 *
 * @since 1.0
 */
public interface ShellChrome {

    /** A chrome without top or bottom content. */
    ShellChrome NONE = new ShellChrome() {
    };

    /**
     * The node above the docking area, or {@code null} when the shell has no top chrome.
     *
     * @return the top node, or {@code null}
     */
    default Node top() {
        return null;
    }

    /**
     * The node below the docking area, or {@code null} when the shell has no bottom chrome.
     *
     * @return the bottom node, or {@code null}
     */
    default Node bottom() {
        return null;
    }

    /**
     * Releases the resources owned by this chrome. Called on the JavaFX Application Thread when the
     * shell shuts down. The default does nothing.
     */
    default void dispose() {
    }
}
