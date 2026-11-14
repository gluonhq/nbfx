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
package org.netbeans.api.nbfx.icons;

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * An image view with two drawings of one icon: it shows the {@linkplain #lightImageProperty() light
 * image} under a light color scheme and the {@linkplain #darkImageProperty() dark image} under a
 * dark one. Without a dark image it shows the light one under both.
 * <p>
 * The view follows the {@link ColorScheme} of the scene it is in, which the theme sets on every
 * scene, so the icon changes with the theme. Outside a scene, as the graphic of an item in the
 * system menu bar is, it uses the color scheme the OS had when the view was created. A scene only
 * refers to the view while the view is part of it, so a view that was removed can be collected.
 * <p>
 * The view sets its {@link #imageProperty() image} whenever the light image, the dark image or the
 * color scheme changes, so an image set directly lasts until the next such change. While the
 * image property is bound elsewhere, the binding wins and the view leaves the image alone.
 * <p>
 * The dark drawing of the resource {@code icon.png} is {@code icon_dark.png} next to it.
 *
 * @since 1.7
 */
public class ThemedImageView extends ImageView {

    /** What the name of a dark drawing adds before the extension. */
    public static final String DARK_SUFFIX = "_dark";

    /** The images loaded by {@link #setIcon}, by URL: every view of an icon shares them. */
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private final ObjectProperty<Image> lightImage = new SimpleObjectProperty<>(this, "lightImage") {
        @Override
        protected void invalidated() {
            setThemedImage();
        }
    };
    private final ObjectProperty<Image> darkImage = new SimpleObjectProperty<>(this, "darkImage") {
        @Override
        protected void invalidated() {
            setThemedImage();
        }
    };
    private final ReadOnlyObjectWrapper<ColorScheme> colorScheme =
            new ReadOnlyObjectWrapper<>(this, "colorScheme") {
                @Override
                protected void invalidated() {
                    setThemedImage();
                }
            };
    /** Kept in a field: a property refers to what it is bound to only weakly. */
    private final ObservableValue<ColorScheme> sceneColorScheme = sceneProperty()
            .flatMap(scene -> scene.getPreferences().colorSchemeProperty())
            .orElse(platformColorScheme());

    /** A view without images. */
    public ThemedImageView() {
        colorScheme.bind(sceneColorScheme);
    }

    /** A view of the two drawings of an icon; {@code dark} may be {@code null}. */
    public ThemedImageView(Image light, Image dark) {
        this();
        setLightImage(light);
        setDarkImage(dark);
    }

    /** The resource name of the dark drawing of {@code name}: {@code a.png} gives {@code a_dark.png}. */
    public static String darkName(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= name.lastIndexOf('/')
                ? name + DARK_SUFFIX
                : name.substring(0, dot) + DARK_SUFFIX + name.substring(dot);
    }

    /**
     * Sets both images from the resource {@code name}, resolved against {@code owner}'s package:
     * the light image is the resource and the dark image is its {@linkplain #darkName(String) dark
     * drawing}, or {@code null} when there is none. The images are loaded once and shared.
     *
     * @throws NullPointerException when {@code owner} has no resource {@code name}
     */
    public final void setIcon(Class<?> owner, String name) {
        URL light = Objects.requireNonNull(owner.getResource(name),
                () -> "Missing icon " + name + " next to " + owner.getName());
        URL dark = owner.getResource(darkName(name));
        setLightImage(CACHE.computeIfAbsent(light.toExternalForm(), Image::new));
        setDarkImage(dark == null ? null : CACHE.computeIfAbsent(dark.toExternalForm(), Image::new));
    }

    /** The image shown under a light color scheme, and under a dark one without a dark image. */
    public final ObjectProperty<Image> lightImageProperty() {
        return lightImage;
    }

    public final Image getLightImage() {
        return lightImage.get();
    }

    public final void setLightImage(Image image) {
        lightImage.set(image);
    }

    /** The image shown under a dark color scheme, or {@code null} to show the light image. */
    public final ObjectProperty<Image> darkImageProperty() {
        return darkImage;
    }

    public final Image getDarkImage() {
        return darkImage.get();
    }

    public final void setDarkImage(Image image) {
        darkImage.set(image);
    }

    /** The color scheme that decides which image is shown. */
    public final ReadOnlyObjectProperty<ColorScheme> colorSchemeProperty() {
        return colorScheme.getReadOnlyProperty();
    }

    public final ColorScheme getColorScheme() {
        return colorScheme.get();
    }

    private void setThemedImage() {
        Image light = getLightImage();
        Image dark = getDarkImage();
        ColorScheme scheme = getColorScheme();
        if (imageProperty().isBound()) {
            // Bound by the caller: setting it would throw.
            return;
        }
        setImage(scheme == ColorScheme.DARK && dark != null ? dark : light);
    }

    private static ColorScheme platformColorScheme() {
        try {
            return Platform.getPreferences().getColorScheme();
        } catch (IllegalStateException ex) {
            // No toolkit yet: the scene's scheme decides once the view is shown.
            return ColorScheme.LIGHT;
        }
    }
}
