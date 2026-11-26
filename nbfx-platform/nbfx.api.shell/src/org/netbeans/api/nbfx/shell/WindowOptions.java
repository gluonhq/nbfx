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

import java.util.Objects;
import javafx.stage.Window;
import org.netbeans.api.nbfx.resources.ResourceRef;

/**
 * Immutable options for one shell window.
 *
 * @param kind window kind
 * @param profile explicit chrome profile, or {@code null} to resolve one
 * @param title optional window title
 * @param icon optional window icon
 * @param owner optional owner window
 * @param modal whether the window is modal
 * @param width initial width
 * @param height initial height
 * @param resizable whether the user may resize the window
 * @param rememberBounds whether geometry is persisted
 * @param decoration optional decoration override
 * @param closePolicy what closing the window does
 * @since 1.4
 */
public record WindowOptions(
        WindowKind kind,
        ShellChromeProfile profile,
        String title,
        ResourceRef icon,
        Window owner,
        boolean modal,
        double width,
        double height,
        boolean resizable,
        boolean rememberBounds,
        WindowDecoration decoration,
        WindowClosePolicy closePolicy) {

    /**
     * Compatibility constructor using {@link WindowClosePolicy#CLOSE_CONTENT}.
     *
     * @param kind window kind
     * @param profile explicit chrome profile, or {@code null}
     * @param title optional window title
     * @param icon optional window icon
     * @param owner optional owner window
     * @param modal whether the window is modal
     * @param width initial width
     * @param height initial height
     * @param resizable whether the user may resize the window
     * @param rememberBounds whether geometry is persisted
     * @param decoration optional decoration override
     * @since 1.4
     */
    public WindowOptions(WindowKind kind, ShellChromeProfile profile, String title,
            ResourceRef icon, Window owner, boolean modal, double width, double height,
            boolean resizable, boolean rememberBounds, WindowDecoration decoration) {
        this(kind, profile, title, icon, owner, modal, width, height, resizable,
                rememberBounds, decoration, WindowClosePolicy.CLOSE_CONTENT);
    }

    public WindowOptions {
        kind = Objects.requireNonNull(kind, "kind");
        closePolicy = Objects.requireNonNull(closePolicy, "closePolicy");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0d || height <= 0.0d) {
            throw new IllegalArgumentException("window dimensions must be finite and positive");
        }
    }
}
