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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.theme.ThemeMode;
import org.openide.modules.Places;
import org.openide.util.NbPreferences;

/**
 * The shell's color theme. A theme is one CSS file in the {@code nbfx-themes} folder of the user
 * directory; that file defines every color on {@code .root}.
 * <p>
 * The mode follows the JavaFX platform color scheme, and every scene gets the theme's own
 * {@link ColorScheme} so the native title bar matches the application theme. Component token
 * defaults are added before the theme, so a theme that predates a component still gets a working
 * fallback.
 *
 * @since 1.0
 */
public final class NbfxTheme {

    /** Name of the folder, inside the user directory, that holds the theme files. */
    public static final String FOLDER = "nbfx-themes";

    private static final String EXTENSION = ".css";
    private static final String TEMPLATE = "template";
    private static final String PREFERENCE_KEY = "theme";
    private static final String MODE_KEY = "themeMode";
    private static final String SIDECAR_SUFFIX = ".properties";

    private static final Properties BRAND = loadBrand();
    private static final String DEFAULT_THEME_KEY = "theme.default";
    private static final String BUNDLED_KEY = "theme.bundled";
    private static final String FOLLOW_SYSTEM_KEY = "theme.followSystem";
    private static final String LIGHT_THEME_KEY = "theme.light";
    private static final String DARK_THEME_KEY = "theme.dark";
    private static final String SCHEME_PREFIX = "theme.scheme.";

    private static final Map<Scene, Boolean> SCENES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static Subscription platformSubscription;

    private NbfxTheme() {
    }

    /** Installs the OS color-scheme listener; idempotent. */
    public static synchronized void install() {
        if (platformSubscription != null) {
            return;
        }
        platformSubscription = Platform.getPreferences().colorSchemeProperty()
                .subscribe(scheme -> onPlatformSchemeChanged());
    }

    /** Disposes the OS listener and forgets tracked scenes. */
    public static synchronized void dispose() {
        if (platformSubscription != null) {
            platformSubscription.unsubscribe();
            platformSubscription = null;
        }
        SCENES.clear();
    }

    /** The configured theme mode. */
    public static ThemeMode mode() {
        String value = NbPreferences.forModule(NbfxTheme.class)
                .get(MODE_KEY, defaultMode().name());
        try {
            return ThemeMode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return defaultMode();
        }
    }

    /** Sets the mode and reapplies the theme to every known scene. */
    public static void setMode(ThemeMode mode) {
        Objects.requireNonNull(mode);
        NbPreferences.forModule(NbfxTheme.class).put(MODE_KEY, mode.name());
        applyToAll();
    }

    /** The theme id the user last chose, or the brand default. */
    public static String selectedTheme() {
        return NbPreferences.forModule(NbfxTheme.class).get(PREFERENCE_KEY, defaultTheme());
    }

    /** The theme file actually used, considering the mode and the OS scheme. */
    public static String current() {
        return existingTheme(effectiveTheme());
    }

    /** The theme mode implied by {@code id}; user themes without metadata are light. */
    public static ColorScheme colorScheme(String id) {
        String brand = BRAND.getProperty(SCHEME_PREFIX + id, "");
        if (!brand.isBlank()) {
            try {
                return ColorScheme.valueOf(brand.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                // Fall through to the sidecar/default.
            }
        }
        Path sidecar = folder().resolve(id + SIDECAR_SUFFIX);
        if (Files.isRegularFile(sidecar)) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(sidecar)) {
                properties.load(in);
                String scheme = properties.getProperty("scheme", "");
                if (!scheme.isBlank()) {
                    return ColorScheme.valueOf(scheme.trim().toUpperCase());
                }
            } catch (IOException | IllegalArgumentException ex) {
                // Fall through to the default.
            }
        }
        String lower = id.toLowerCase();
        return lower.contains("dark") || lower.contains("high-contrast")
                ? ColorScheme.DARK : ColorScheme.LIGHT;
    }

    /** The theme used when none has been chosen, or when the chosen file no longer exists. */
    public static String defaultTheme() {
        return BRAND.getProperty(DEFAULT_THEME_KEY, "light").trim();
    }

    private static ThemeMode defaultMode() {
        return Boolean.parseBoolean(BRAND.getProperty(FOLLOW_SYSTEM_KEY, "true"))
                ? ThemeMode.SYSTEM : ThemeMode.LIGHT;
    }

    private static String effectiveTheme() {
        ThemeMode mode = mode();
        if (mode == ThemeMode.SYSTEM) {
            ColorScheme scheme = Platform.getPreferences().getColorScheme();
            return scheme == ColorScheme.DARK
                    ? BRAND.getProperty(DARK_THEME_KEY, "dark").trim()
                    : BRAND.getProperty(LIGHT_THEME_KEY, "light").trim();
        }
        String selected = selectedTheme();
        ColorScheme wanted = mode == ThemeMode.DARK ? ColorScheme.DARK : ColorScheme.LIGHT;
        if (colorScheme(selected) == wanted) {
            return selected;
        }
        return wanted == ColorScheme.DARK
                ? BRAND.getProperty(DARK_THEME_KEY, "dark").trim()
                : BRAND.getProperty(LIGHT_THEME_KEY, "light").trim();
    }

    private static String existingTheme(String id) {
        if (Files.isRegularFile(folder().resolve(id + EXTENSION))) {
            return id;
        }
        String fallback = defaultTheme();
        return Files.isRegularFile(folder().resolve(fallback + EXTENSION)) ? fallback : id;
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

    /** Selects a theme file and sets the mode from its declared scheme. */
    public static void setTheme(String id) {
        Objects.requireNonNull(id);
        NbPreferences.forModule(NbfxTheme.class).put(PREFERENCE_KEY, id);
        NbPreferences.forModule(NbfxTheme.class).put(MODE_KEY,
                (colorScheme(id) == ColorScheme.DARK ? ThemeMode.DARK : ThemeMode.LIGHT).name());
        applyToAll();
    }

    /** Applies the component token defaults and then the current theme to a scene. */
    public static void apply(Scene scene) {
        install();
        SCENES.put(scene, Boolean.TRUE);
        String theme = current();
        scene.getPreferences().setColorScheme(colorScheme(theme));
        Path folder = folder();
        String prefix = folder.toUri().toString();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        final String themePrefix = prefix;
        List<String> sheets = scene.getStylesheets();
        sheets.removeIf(url -> url.startsWith(themePrefix));
        for (String tokenSheet : ThemeTokenRegistry.stylesheets()) {
            if (!sheets.contains(tokenSheet)) {
                sheets.add(tokenSheet);
            }
        }
        Path file = folder.resolve(theme + EXTENSION);
        if (Files.isRegularFile(file)) {
            String url = file.toUri().toString();
            if (!url.equals(sheets.isEmpty() ? null : sheets.get(sheets.size() - 1))) {
                sheets.add(url);
            }
        }
    }

    private static void onPlatformSchemeChanged() {
        if (mode() != ThemeMode.SYSTEM) {
            return;
        }
        applyToAll();
    }

    private static void applyToAll() {
        Runnable apply = () -> {
            for (Window window : Window.getWindows()) {
                Scene scene = window.getScene();
                if (scene != null) {
                    apply(scene);
                }
            }
            for (Scene scene : List.copyOf(SCENES.keySet())) {
                apply(scene);
            }
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
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
