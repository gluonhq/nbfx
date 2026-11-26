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

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.netbeans.api.nbfx.icons.ToolIcons;

/**
 * The icons of the Versioning view: the compare-mode and commit glyphs of NetBeans' Git module
 * (Apache 2.0), redrawn as SVG on the 16 pixel grid of the originals and rendered at 64 pixels
 * ({@code icons/<name>.svg} and {@code .png}; and the tool bar glyphs and the folder shared
 * through {@link ToolIcons}.
 *
 * @since 1.2
 */
final class GitIcons {

    static final String HEAD_VS_WORKING_TREE = "head_vs_working";
    static final String HEAD_VS_INDEX = "head_vs_index";
    static final String INDEX_VS_WORKING_TREE = "index_vs_working";
    static final String COMMIT = "commit";
    /**
     * The icon of the Show Changes command, in the Team menu and the Git context menu, and of the
     * window it opens.
     */
    static final String SHOW_CHANGES = "show_changes";
    /** The resource path of {@link #SHOW_CHANGES}, for the tab of the Git status window. */
    static final String VIEW_RESOURCE = "org/netbeans/modules/nbfx/ide/vcs/icons/show_changes.png";

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private GitIcons() {
    }

    /** A fresh view of this module's icon {@code icons/<name>.png}, or of the shared glyph of that name. */
    static ImageView view(String name) {
        ImageView view = new ImageView(CACHE.computeIfAbsent(name, GitIcons::load));
        view.setFitWidth(ToolIcons.SIZE);
        view.setFitHeight(ToolIcons.SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    /**
     * The 16 pixel drawing of this module's icon {@code icons/<name>-16.png}, at its own size: the
     * graphic of an item in the system menu bar is not scaled, so it cannot be the 64 pixel image.
     */
    static ImageView menuView(String name) {
        return new ImageView(CACHE.computeIfAbsent(name + "-16", GitIcons::load));
    }

    private static Image load(String name) {
        URL local = GitIcons.class.getResource("icons/" + name + ".png");
        return local != null ? new Image(local.toExternalForm()) : ToolIcons.image(name);
    }

    static {
        // Fail at class load, not in a menu, if the 16 pixel drawing of a command icon is missing.
        Objects.requireNonNull(GitIcons.class.getResource("icons/" + SHOW_CHANGES + "-16.png"));
    }
}
