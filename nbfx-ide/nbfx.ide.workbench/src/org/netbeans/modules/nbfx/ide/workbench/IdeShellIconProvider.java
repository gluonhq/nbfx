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

import org.netbeans.modules.nbfx.platform.shell.api.ShellIconProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.modules.nbfx.ide.actions.IdeActionIds;

/**
 * The IDE's icon resources, kept out of the generic builders. A contributed command that carries
 * its own icon takes precedence; this is the transitional home for the launcher's command icons.
 */
final class IdeShellIconProvider implements ShellIconProvider {

    private static final Logger LOG = Logger.getLogger(IdeShellIconProvider.class.getName());

    /** Maps a command id to its icon resource (relative to this class' package). */
    private static final Map<String, String> ICONS = new HashMap<>();
    static {
        ICONS.put(IdeActionIds.NEW_PROJECT, "newProject24.png");
        ICONS.put(IdeActionIds.OPEN_PROJECT, "openProject24.png");
        ICONS.put(ActionIds.SAVE, "save.png");
        ICONS.put(ActionIds.SAVE_ALL, "saveAll24.png");
        ICONS.put(ActionIds.CUT, "cut.png");
        ICONS.put(ActionIds.COPY, "copy.png");
        ICONS.put(ActionIds.PASTE, "paste.png");
        ICONS.put(ActionIds.FIND, "find.png");
        ICONS.put(ActionIds.UNDO, "undo24.png");
        ICONS.put(ActionIds.REDO, "redo24.png");
        ICONS.put(IdeActionIds.SELECT_PROJECTS, "projectTab.png");
        ICONS.put(IdeActionIds.SELECT_FILES, "filesTab.png");
        ICONS.put(ActionIds.SELECT_EDITOR, "editorTab.png");
        ICONS.put(IdeActionIds.SELECT_IN_PROJECTS, "projectTab.png");
        ICONS.put(IdeActionIds.SELECT_IN_FILES, "filesTab.png");
        ICONS.put(IdeActionIds.BUILD, "build.png");
        ICONS.put(IdeActionIds.CLEAN_BUILD, "cleanBuild.png");
        ICONS.put(IdeActionIds.CLEAN, "clean24.png");
        ICONS.put(IdeActionIds.TEST, "test.png");
        ICONS.put(IdeActionIds.RUN, "run.png");
        ICONS.put(IdeActionIds.DEBUG_FILE, "debug.png");
    }

    private final Map<String, Image> imageCache = new HashMap<>();

    @Override
    public Node icon(String commandId, double size) {
        String resource = ICONS.get(commandId);
        if (resource == null) {
            return null;
        }
        String name = size <= ShellIconProvider.MENU_SIZE
                ? resource.replaceFirst("\\.png$", "-16.png")
                : resource;
        Image image = fullImage(name);
        if (image == null) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        return view;
    }

    /**
     * Loads (and caches) the full-resolution source image for the given resource name, falling back
     * to the 16&times;16 variant for icons that only ship in that size (menu-only commands).
     */
    private Image fullImage(String resourceName) {
        return imageCache.computeIfAbsent(resourceName, name -> {
            var url = IdeShellIconProvider.class.getResource(name);
            if (url == null) {
                String smallResource = name.replaceFirst("(?<!-16)\\.png$", "-16.png");
                url = IdeShellIconProvider.class.getResource(smallResource);
            }
            if (url == null) {
                LOG.warning("Icon resource not found: " + name);
                return null;
            }
            return new Image(url.toExternalForm());
        });
    }
}
