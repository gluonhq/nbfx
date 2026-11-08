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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Basic display-name, bundled-theme and token-default contracts. */
public class NbfxThemeTest {

    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9])-nbfx-[A-Za-z0-9-]+");
    private static final Pattern DEFINITION =
            Pattern.compile("(?m)^\\s*(-nbfx-[A-Za-z0-9-]+)\\s*:");

    @Test
    public void bundledThemesDeclareTheirScheme() {
        assertEquals(ColorScheme.LIGHT, NbfxTheme.colorScheme("light"));
        assertEquals(ColorScheme.DARK, NbfxTheme.colorScheme("dark"));
        assertEquals(ColorScheme.DARK, NbfxTheme.colorScheme("solarized-dark"));
        assertEquals(ColorScheme.DARK, NbfxTheme.colorScheme("high-contrast"));
    }

    @Test
    public void displayNamesAreReadable() {
        assertEquals("Dark", NbfxTheme.displayName("dark"));
        assertEquals("Solarized Dark", NbfxTheme.displayName("solarized-dark"));
        assertEquals("High Contrast", NbfxTheme.displayName("high-contrast"));
    }

    @Test
    public void bundledThemesAreAvailableWithoutAUserFolder() {
        List<String> themes = NbfxTheme.themes();

        assertTrue("the bundled light theme is listed", themes.contains("light"));
        assertTrue("the bundled dark theme is listed", themes.contains("dark"));
        assertTrue("the bundled solarized theme is listed", themes.contains("solarized-dark"));
        assertTrue("the bundled high-contrast theme is listed", themes.contains("high-contrast"));
        assertFalse("the template is not a selectable theme", themes.contains("template"));
        assertEquals("the default theme comes first", NbfxTheme.defaultTheme(), themes.get(0));
    }

    @Test
    public void platformDefaultsAreRegistered() {
        assertFalse("a token-defaults stylesheet must always be available",
                ThemeTokenRegistry.stylesheets().isEmpty());
    }

    @Test
    public void everyReferencedTokenHasADefault() throws IOException {
        Set<String> defaults = new TreeSet<>();
        for (String stylesheet : ThemeTokenRegistry.stylesheets()) {
            try (InputStream in = URI.create(stylesheet).toURL().openStream()) {
                defaults.addAll(definedTokens(in));
            }
        }
        assertFalse("the registered defaults define no tokens", defaults.isEmpty());

        Set<String> referenced = new TreeSet<>();
        Path root = repositoryRoot();
        for (String cluster : List.of("nbfx-platform", "nbfx-ide")) {
            try (Stream<Path> files = Files.walk(root.resolve(cluster))) {
                List<Path> sources = files
                        .filter(Files::isRegularFile)
                        .filter(NbfxThemeTest::isSource)
                        .toList();
                for (Path source : sources) {
                    referenced.addAll(referencedTokens(source));
                }
            }
        }
        referenced.removeAll(defaults);
        assertTrue("tokens referenced without a default in defaults.css: " + referenced,
                referenced.isEmpty());
    }

    private static boolean isSource(Path path) {
        String name = path.toString().replace('\\', '/');
        return (name.endsWith(".css") || name.endsWith(".java"))
                && !name.contains("/build/") && !name.contains("/dist/");
    }

    private static Set<String> definedTokens(InputStream in) throws IOException {
        String css = stripComments(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        Set<String> tokens = new TreeSet<>();
        Matcher matcher = DEFINITION.matcher(css);
        while (matcher.find()) {
            tokens.add(matcher.group(1));
        }
        return tokens;
    }

    private static Set<String> referencedTokens(Path source) throws IOException {
        String text = stripComments(Files.readString(source));
        if (source.toString().endsWith(".java")) {
            text = text.replaceAll("(?m)//.*$", "");
        }
        Set<String> tokens = new TreeSet<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (!token.endsWith("-")) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /** The source tree root; the test runs from a module build directory inside it. */
    private static Path repositoryRoot() throws IOException {
        Path root = findRoot(moduleLocation());
        if (root == null) {
            root = findRoot(Path.of(System.getProperty("user.dir")).toAbsolutePath());
        }
        if (root == null) {
            throw new IOException("Cannot locate the nbfx sources from the test class path");
        }
        return root;
    }

    private static Path moduleLocation() {
        try {
            return Path.of(NbfxTheme.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toAbsolutePath();
        } catch (URISyntaxException | RuntimeException ex) {
            return null;
        }
    }

    private static Path findRoot(Path start) {
        if (start == null) {
            return null;
        }
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("nbfx-platform"))
                    && Files.isDirectory(candidate.resolve("nbfx-ide"))) {
                return candidate;
            }
        }
        return null;
    }
}
