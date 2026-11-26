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
package org.netbeans.api.nbfx.shell;

import javafx.stage.Window;
import org.netbeans.api.nbfx.layout.LayoutNode;

/**
 * Stable shell-window handle. A floating window keeps the same content and layout instances while
 * shown, hidden and redocked.
 *
 * @since 1.12
 */
public interface DockWindow {

    /** The stable window identity. */
    WindowId id();

    /** The window kind. */
    WindowKind kind();

    /** The JavaFX window backing this handle. */
    Window window();

    /** The content-free layout snapshot, or {@code null} for custom content without a layout. */
    LayoutNode layout();

    /** The content hosted by this window. */
    WindowContent content();

    /** Shows the window. */
    void show();

    /** Hides the window without closing its content. */
    void hide();

    /** Closes the window using the shell's normal close lifecycle. */
    void close();

    /** A snapshot of this window's layout, view tabs and geometry. */
    WindowState state();
}
