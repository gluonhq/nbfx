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
package org.netbeans.modules.nbfx.platform.theme;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.openide.modules.Places;
import org.openide.util.NbPreferences;

/**
 * The shell's color theme. A theme is one CSS file in the {@code nbfx-themes} folder of the user
 * directory; that file defines every color on {@code .root}: the UI tokens, the code editor, the
 * syntax colors, the popups and the Modena base colors of the stock controls.
 * <p>
 * The folder is created with the bundled themes the first time it is needed. Users can edit those
 * files, add their own (copy {@code template.css}), or delete the ones they do not want. Every
 * {@code .css} file except {@code template.css} is listed in the View menu.
 * <p>
 * A scene gets the selected theme as its last stylesheet, so its {@code .root} values win over any
 * other sheet. Call {@link #apply(Scene)} after the scene's other stylesheets have been added.
 *
 * @since 1.0
 */
public final class NbfxTheme {

    /** The theme used when none has been chosen, or when the chosen file no longer exists. */
    public static final String DEFAULT_THEME = "light";

    /** Name of the folder, inside the user directory, that holds the theme files. */
    public static final String FOLDER = "nbfx-themes";

    private static final String EXTENSION = ".css";
    private static final String TEMPLATE = "template";
    private static final String PREFERENCE_KEY = "theme";
    private static final List<String> BUNDLED = List.of(
            "light.css", "dark.css", "solarized-dark.css", "high-contrast.css", "template.css");

    private NbfxTheme() {
    }

    /**
     * The folder holding the theme files, {@code <userdir>/nbfx-themes}. The folder is created with
     * the bundled themes if it does not exist yet.
     *
     * @return the theme folder
     */
    public static Path folder() {
        Path folder = Places.getUserDirectory().toPath().resolve(FOLDER);
        if (!Files.isDirectory(folder)) {
            seed(folder);
        }
        return folder;
    }

    /**
     * The ids of the available themes: the file names without the extension, excluding the
     * template. The default theme comes first, the others in alphabetical order.
     *
     * @return the theme ids, never null
     */
    public static List<String> themes() {
        try (Stream<Path> files = Files.list(folder())) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .filter(id -> !TEMPLATE.equals(id))
                    .sorted(Comparator.comparing((String id) -> !DEFAULT_THEME.equals(id))
                            .thenComparing(Comparator.naturalOrder()))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    /**
     * A readable name for a theme id, for example {@code "high-contrast"} becomes
     * {@code "High Contrast"}.
     *
     * @param id a theme id
     * @return the display name
     */
    public static String displayName(String id) {
        return Arrays.stream(id.split("-+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(id);
    }

    /**
     * The theme chosen by the user, persisted across sessions. Falls back to the default theme
     * when the chosen theme is no longer available.
     *
     * @return the id of the selected theme
     */
    public static String current() {
        String id = NbPreferences.forModule(NbfxTheme.class).get(PREFERENCE_KEY, DEFAULT_THEME);
        List<String> available = themes();
        if (available.contains(id)) {
            return id;
        }
        return available.contains(DEFAULT_THEME) || available.isEmpty() ? DEFAULT_THEME : available.get(0);
    }

    /**
     * Stores the theme and applies it to every open window. Must run on the JavaFX thread.
     *
     * @param id the id of a theme returned by {@link #themes()}
     */
    public static void setTheme(String id) {
        Objects.requireNonNull(id);
        NbPreferences.forModule(NbfxTheme.class).put(PREFERENCE_KEY, id);
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene != null) {
                apply(scene);
            }
        }
    }

    /**
     * Applies the current theme to a scene. Safe to call more than once: the previous theme sheet is
     * removed and the current one is added last, so it always wins.
     *
     * @param scene the scene to theme, typically on the JavaFX thread
     */
    public static void apply(Scene scene) {
        Path folder = folder();
        String prefix = folder.toUri().toString();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        String themePrefix = prefix;
        Path file = folder.resolve(current() + EXTENSION);
        if (!Files.isRegularFile(file)) {
            scene.getStylesheets().removeIf(url -> url.startsWith(themePrefix));
            return;
        }
        String url = file.toUri().toString();
        List<String> sheets = scene.getStylesheets();
        if (!sheets.isEmpty() && url.equals(sheets.get(sheets.size() - 1))) {
            return;
        }
        sheets.removeIf(sheet -> sheet.startsWith(themePrefix));
        sheets.add(url);
    }

    /** Copies the bundled themes into a new theme folder. Existing files are never overwritten. */
    private static void seed(Path folder) {
        try {
            Files.createDirectories(folder);
            for (String name : BUNDLED) {
                try (InputStream in = NbfxTheme.class.getResourceAsStream("themes/" + name)) {
                    if (in != null) {
                        Files.copy(in, folder.resolve(name));
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create the theme folder " + folder, ex);
        }
    }
}
