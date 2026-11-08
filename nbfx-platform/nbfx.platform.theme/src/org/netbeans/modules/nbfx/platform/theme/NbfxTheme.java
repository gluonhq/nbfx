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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.theme.ThemeMode;
import org.openide.modules.Places;
import org.openide.util.NbPreferences;

/**
 * The shell's color theme. The bundled themes ship with this module, and a theme with the same id
 * in the optional {@code nbfx-themes} folder of the user directory overlays it. A theme file
 * defines every color on {@code .root}.
 * <p>
 * The mode follows the JavaFX platform color scheme, and every scene gets the theme's own
 * {@link ColorScheme} so the native title bar matches the application theme. Component token
 * defaults are added before the theme, so a theme that predates a component still gets a working
 * fallback and a missing theme file cannot leave the scene without colors.
 *
 * @since 1.0
 */
public final class NbfxTheme {

    /**
     * Name of the optional folder, inside the user directory, that overlays the bundled themes.
     * A user folder that is missing or cannot be created is not an error: the bundled themes
     * remain available.
     */
    public static final String FOLDER = "nbfx-themes";

    private static final String EXTENSION = ".css";
    private static final String TEMPLATE = "template";
    private static final String PREFERENCE_KEY = "theme";
    private static final String MODE_KEY = "themeMode";
    private static final String SIDECAR_SUFFIX = ".properties";
    private static final String THEME_URL_KEY = "nbfx.theme.url";
    private static final String THEMES_RESOURCE = "themes/";

    private static final Logger LOG = Logger.getLogger(NbfxTheme.class.getName());

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
    private static boolean warnedAboutUserDirectory;

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
        Path folder = folder();
        Path sidecar = folder == null ? null : folder.resolve(id + SIDECAR_SUFFIX);
        if (sidecar != null && Files.isRegularFile(sidecar)) {
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
                ? ThemeMode.SYSTEM
                : colorScheme(defaultTheme()) == ColorScheme.DARK ? ThemeMode.DARK : ThemeMode.LIGHT;
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
        if (themeUrl(id) != null) {
            return id;
        }
        String fallback = defaultTheme();
        return themeUrl(fallback) != null ? fallback : id;
    }

    /** The stylesheet URL of a theme: a user overlay first, then the bundled resource. */
    private static String themeUrl(String id) {
        Path folder = folder();
        if (folder != null) {
            Path file = folder.resolve(id + EXTENSION);
            if (Files.isRegularFile(file)) {
                return file.toUri().toString();
            }
        }
        URL bundled = NbfxTheme.class.getResource(THEMES_RESOURCE + id + EXTENSION);
        return bundled == null ? null : bundled.toExternalForm();
    }

    private static List<String> bundled() {
        return Arrays.stream(BRAND.getProperty(BUNDLED_KEY,
                "light.css,dark.css,solarized-dark.css,high-contrast.css,template.css").split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private static List<String> bundledIds() {
        return bundled().stream()
                .filter(name -> name.endsWith(EXTENSION))
                .map(name -> name.substring(0, name.length() - EXTENSION.length()))
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

    /**
     * The optional folder holding user theme overlays, or {@code null} when no user directory can
     * be determined. The folder is created when possible; a failure is logged and the bundled
     * themes are still used.
     */
    public static Path folder() {
        Path base = userDirectory();
        if (base == null) {
            warnAboutUserDirectory();
            return null;
        }
        Path folder = base.resolve(FOLDER);
        try {
            Files.createDirectories(folder);
        } catch (IOException | SecurityException ex) {
            LOG.log(Level.WARNING, "Cannot create the theme folder " + folder, ex);
        }
        return folder;
    }

    private static Path userDirectory() {
        java.io.File user = Places.getUserDirectory();
        if (user != null) {
            return user.toPath();
        }
        String configured = System.getProperty("netbeans.user");
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            return Path.of(configured);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Invalid netbeans.user for themes: " + configured, ex);
            return null;
        }
    }

    private static void warnAboutUserDirectory() {
        if (warnedAboutUserDirectory) {
            return;
        }
        warnedAboutUserDirectory = true;
        LOG.warning("No user directory is available; using the bundled themes only");
    }

    /** The available theme ids: the bundled themes plus the user overlays, default first. */
    public static List<String> themes() {
        Set<String> ids = new TreeSet<>(bundledIds());
        Path folder = folder();
        if (folder != null && Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                files.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(EXTENSION))
                        .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                        .forEach(ids::add);
            } catch (IOException | SecurityException ex) {
                LOG.log(Level.FINE, "Cannot list user themes in " + folder, ex);
            }
        }
        ids.remove(TEMPLATE);
        List<String> ordered = new ArrayList<>(ids);
        ordered.sort(Comparator.comparing((String id) -> !defaultTheme().equals(id))
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(ordered);
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

    /**
     * Applies the component token defaults and then the current theme to a scene. The theme keeps
     * its position relative to other stylesheets, such as the brand sheets added after it, and a
     * scene that is already up to date is not reparsed.
     */
    public static void apply(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        install();
        SCENES.put(scene, Boolean.TRUE);
        String theme = current();
        scene.getPreferences().setColorScheme(colorScheme(theme));
        List<String> tokenSheets = ThemeTokenRegistry.stylesheets();
        String themeUrl = themeUrl(theme);
        List<String> sheets = scene.getStylesheets();
        if (isApplied(sheets, tokenSheets, themeUrl)) {
            return;
        }
        int anchor = sheets.size();
        for (int i = sheets.size() - 1; i >= 0; i--) {
            String url = sheets.get(i);
            if (isThemeSheet(url) || tokenSheets.contains(url)) {
                anchor = i;
                sheets.remove(i);
            }
        }
        int insert = Math.min(anchor, sheets.size());
        for (String tokenSheet : tokenSheets) {
            if (!sheets.contains(tokenSheet)) {
                sheets.add(insert++, tokenSheet);
            }
        }
        if (themeUrl == null) {
            scene.getProperties().remove(THEME_URL_KEY);
        } else {
            sheets.add(Math.min(insert, sheets.size()), themeUrl);
            scene.getProperties().put(THEME_URL_KEY, themeUrl);
        }
    }

    /**
     * Applies the theme to a dialog's scene. The scene does not exist yet when an {@code Alert} is
     * configured, so the theme is applied as soon as the dialog pane is attached.
     */
    public static void apply(DialogPane dialogPane) {
        Objects.requireNonNull(dialogPane, "dialogPane");
        dialogPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                apply(newScene);
            }
        });
        Scene scene = dialogPane.getScene();
        if (scene != null) {
            apply(scene);
        }
    }

    private static boolean isApplied(List<String> sheets, List<String> tokenSheets, String themeUrl) {
        if (themeUrl == null) {
            return tokenSheets.stream().allMatch(sheets::contains)
                    && sheets.stream().noneMatch(NbfxTheme::isThemeSheet);
        }
        int themeIndex = sheets.indexOf(themeUrl);
        if (themeIndex < 0 || sheets.lastIndexOf(themeUrl) != themeIndex) {
            return false;
        }
        for (String tokenSheet : tokenSheets) {
            int tokenIndex = sheets.indexOf(tokenSheet);
            if (tokenIndex < 0 || tokenIndex > themeIndex) {
                return false;
            }
        }
        for (String url : sheets) {
            if (!url.equals(themeUrl) && isThemeSheet(url)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isThemeSheet(String url) {
        if (url == null) {
            return false;
        }
        Path folder = folder();
        if (folder != null) {
            String prefix = folder.toUri().toString();
            if (!prefix.endsWith("/")) {
                prefix += "/";
            }
            if (url.startsWith(prefix)) {
                return true;
            }
        }
        for (String name : bundled()) {
            URL bundled = NbfxTheme.class.getResource(THEMES_RESOURCE + name);
            if (bundled != null && url.equals(bundled.toExternalForm())) {
                return true;
            }
        }
        return false;
    }

    private static void onPlatformSchemeChanged() {
        if (mode() != ThemeMode.SYSTEM) {
            return;
        }
        applyToAll();
    }

    private static void applyToAll() {
        Runnable apply = () -> {
            Set<Scene> scenes = new LinkedHashSet<>();
            for (Window window : Window.getWindows()) {
                Scene scene = window.getScene();
                if (scene != null) {
                    scenes.add(scene);
                }
            }
            synchronized (SCENES) {
                scenes.addAll(SCENES.keySet());
            }
            for (Scene scene : scenes) {
                apply(scene);
            }
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
    }
}
