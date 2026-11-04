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
import java.util.Properties;
import java.util.stream.Stream;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.openide.modules.Places;
import org.openide.util.NbPreferences;

/**
 * The shell's color theme. A theme is one CSS file in the {@code nbfx-themes} folder of the user
 * directory; that file defines every color on {@code .root}.
 * <p>
 * Component token defaults are added before the theme, so a theme that predates a component still
 * gets a working fallback. The theme itself is always the last stylesheet of a scene.
 *
 * @since 1.0
 */
public final class NbfxTheme {

    /** Name of the folder, inside the user directory, that holds the theme files. */
    public static final String FOLDER = "nbfx-themes";

    private static final String EXTENSION = ".css";
    private static final String TEMPLATE = "template";
    private static final String PREFERENCE_KEY = "theme";

    private static final Properties BRAND = loadBrand();
    private static final String DEFAULT_THEME_KEY = "theme.default";
    private static final String BUNDLED_KEY = "theme.bundled";

    private NbfxTheme() {
    }

    /** The theme used when none has been chosen, or when the chosen file no longer exists. */
    public static String defaultTheme() {
        return BRAND.getProperty(DEFAULT_THEME_KEY, "light").trim();
    }

    private static List<String> bundled() {
        return Arrays.stream(BRAND.getProperty(BUNDLED_KEY,
                "light.css,dark.css,solarized-dark.css,high-contrast.css,template.css").split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private static Properties loadBrand() {
        Properties properties = new Properties();
        try (InputStream in = NbfxTheme.class.getResourceAsStream("theme.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ex) {
            // Keep the defaults: a broken brand file must not stop the shell from starting.
        }
        return properties;
    }

    /** The folder holding the theme files; created with the bundled themes on first use. */
    public static Path folder() {
        java.io.File user = Places.getUserDirectory();
        Path base = user != null
                ? user.toPath()
                : Path.of(System.getProperty("java.io.tmpdir"), "nbfx-test-user");
        Path folder = base.resolve(FOLDER);
        if (!Files.isDirectory(folder)) {
            seed(folder);
        }
        return folder;
    }

    /** The available theme ids: default first, then alphabetical, excluding the template. */
    public static List<String> themes() {
        try (Stream<Path> files = Files.list(folder())) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .filter(id -> !TEMPLATE.equals(id))
                    .sorted(Comparator.comparing((String id) -> !defaultTheme().equals(id))
                            .thenComparing(Comparator.naturalOrder()))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    /** A readable name for a theme id, for example {@code high-contrast}. */
    public static String displayName(String id) {
        return Arrays.stream(id.split("-+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(id);
    }

    /** The selected theme, falling back to the default when it is no longer available. */
    public static String current() {
        String id = NbPreferences.forModule(NbfxTheme.class).get(PREFERENCE_KEY, defaultTheme());
        List<String> available = themes();
        if (available.contains(id)) {
            return id;
        }
        return available.contains(defaultTheme()) || available.isEmpty() ? defaultTheme() : available.get(0);
    }

    /** Stores the theme and applies it to every open window. Must run on the JavaFX thread. */
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
     * Applies the component token defaults and then the current theme to a scene. Safe to call
     * more than once: the previous theme sheet is removed and the current one is added last.
     */
    public static void apply(Scene scene) {
        Path folder = folder();
        String prefix = folder.toUri().toString();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        final String themePrefix = prefix;
        List<String> sheets = scene.getStylesheets();
        // Keep the token defaults, drop only the previous theme.
        sheets.removeIf(url -> url.startsWith(themePrefix));
        for (String tokenSheet : ThemeTokenRegistry.stylesheets()) {
            if (!sheets.contains(tokenSheet)) {
                sheets.add(tokenSheet);
            }
        }
        Path file = folder.resolve(current() + EXTENSION);
        if (Files.isRegularFile(file)) {
            String url = file.toUri().toString();
            if (!url.equals(sheets.isEmpty() ? null : sheets.get(sheets.size() - 1))) {
                sheets.add(url);
            }
        }
    }

    private static void seed(Path folder) {
        try {
            Files.createDirectories(folder);
            for (String name : bundled()) {
                Path target = folder.resolve(name);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream in = NbfxTheme.class.getResourceAsStream("themes/" + name)) {
                    if (in != null) {
                        Files.copy(in, target);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create the theme folder " + folder, ex);
        }
    }
}
