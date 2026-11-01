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
package org.netbeans.modules.nbfx.ui;

import org.netbeans.modules.nbfx.platform.theme.NbfxTheme;
import java.util.Objects;
import javafx.scene.Scene;

/**
 * Stylesheets of the shell. The structural rules come from {@code styles.css}; the colors come
 * from the selected theme (see {@link NbfxTheme}), which is always the last sheet of a scene.
 */
public final class ShellStyles {

    private static final String BASE_STYLESHEET = Objects.requireNonNull(
            ShellStyles.class.getResource("styles.css"), "missing styles.css").toExternalForm();

    private ShellStyles() {
    }

    /**
     * Adds the shell's structural styles, if not yet present, and then the current theme.
     *
     * @param scene the scene of a shell window
     */
    public static void apply(Scene scene) {
        if (!scene.getStylesheets().contains(BASE_STYLESHEET)) {
            scene.getStylesheets().add(BASE_STYLESHEET);
        }
        NbfxTheme.apply(scene);
    }
}
