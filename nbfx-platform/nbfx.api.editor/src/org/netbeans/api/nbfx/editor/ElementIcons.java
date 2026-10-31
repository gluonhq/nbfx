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
package org.netbeans.api.nbfx.editor;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.netbeans.api.nbfx.language.ElementModifiers;
import org.netbeans.api.nbfx.language.ElementModifiers.Visibility;
import org.netbeans.api.nbfx.language.SourceElementKind;
import org.netbeans.api.nbfx.language.SourceTypeKind;

/**
 * Maps the shared source-element vocabulary ({@link SourceElementKind},
 * {@link SourceTypeKind} and {@link ElementModifiers}) onto the icon images shipped
 * in this package, so the completion popup, the breadcrumbs bar, the breadcrumbs
 * popup and the usages view all render the same glyphs.
 *
 * <p>Member icons (method / constructor / field) come in visibility variants
 * (public, private, protected, package-private) and, for methods and fields,
 * static variants. Kinds without a glyph (statements, separators) map to
 * {@code null}.</p>
 */
public final class ElementIcons {

    /** Edge length, in pixels, the shipped icons are designed for. */
    public static final double ICON_SIZE = 16;

    private static final Map<String, Image> ICON_CACHE = new ConcurrentHashMap<>();

    private ElementIcons() {
    }

    /**
     * Returns the (cached) icon for an element, or {@code null} when the kind has no glyph.
     *
     * @param kind general semantic category
     * @param typeKind detailed hint for {@link SourceElementKind#TYPE} elements and constructors
     * @param modifiers neutral modifiers selecting visibility / static variants
     * @return shared icon image, or {@code null}
     */
    public static Image iconFor(SourceElementKind kind, SourceTypeKind typeKind,
            ElementModifiers modifiers) {
        String iconName = iconNameFor(kind, typeKind, modifiers);
        return iconName == null ? null : ICON_CACHE.computeIfAbsent(iconName, ElementIcons::loadIcon);
    }

    /**
     * Returns the (cached) icon for an element from {@link Modifier} bits, or {@code null} when
     * the kind has no glyph.
     *
     * @param kind general semantic category
     * @param typeKind detailed hint for {@link SourceElementKind#TYPE} elements and constructors
     * @param modifiers {@link Modifier} bits selecting visibility / static variants
     * @return shared icon image, or {@code null}
     */
    public static Image iconFor(SourceElementKind kind, SourceTypeKind typeKind, int modifiers) {
        return iconFor(kind, typeKind, fromBits(modifiers));
    }

    /**
     * Returns a fixed-size {@link ImageView} showing the element's icon, or {@code null}
     * when the kind has no glyph.
     *
     * @param kind general semantic category
     * @param typeKind detailed hint for {@link SourceElementKind#TYPE} elements and constructors
     * @param modifiers neutral modifiers selecting visibility / static variants
     * @return new image view, or {@code null}
     */
    public static ImageView iconViewFor(SourceElementKind kind, SourceTypeKind typeKind,
            ElementModifiers modifiers) {
        Image icon = iconFor(kind, typeKind, modifiers);
        if (icon == null) {
            return null;
        }
        ImageView view = new ImageView(icon);
        view.setFitWidth(ICON_SIZE);
        view.setFitHeight(ICON_SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    /**
     * Returns a fixed-size {@link ImageView} showing the element's icon from {@link Modifier} bits,
     * or {@code null} when the kind has no glyph.
     *
     * @param kind general semantic category
     * @param typeKind detailed hint for {@link SourceElementKind#TYPE} elements and constructors
     * @param modifiers {@link Modifier} bits selecting visibility / static variants
     * @return new image view, or {@code null}
     */
    public static ImageView iconViewFor(SourceElementKind kind, SourceTypeKind typeKind, int modifiers) {
        return iconViewFor(kind, typeKind, fromBits(modifiers));
    }

    /** Maps an element kind / type-kind to the icon file shipped in this package. */
    public static String iconNameFor(SourceElementKind kind, SourceTypeKind typeKind,
            ElementModifiers modifiers) {
        SourceTypeKind type = typeKind == null ? SourceTypeKind.OTHER : typeKind;
        ElementModifiers mods = modifiers == null ? ElementModifiers.NONE : modifiers;
        return switch (kind) {
            case METHOD -> type == SourceTypeKind.CONSTRUCTOR
                    ? memberIcon("constructor", mods)
                    : memberIcon("method", mods);
            case FIELD -> memberIcon("field", mods);
            case PACKAGE -> "package.png";
            case MODULE -> "module.png";
            case TYPE -> switch (type) {
                case INTERFACE -> "interface.png";
                case ENUM -> "enum.png";
                case RECORD -> "record.png";
                case ANNOTATION, CLASS, OTHER, CONSTRUCTOR -> "class_16.png";
            };
            case VARIABLE, KEYWORD -> "localVariable.png";
            case TAG -> "tag.png";
            case RULE -> "style_sheet.png";
            case SEPARATOR, OTHER -> null;
        };
    }

    /** Maps an element kind / type-kind to the icon file from {@link Modifier} bits. */
    public static String iconNameFor(SourceElementKind kind, SourceTypeKind typeKind, int modifiers) {
        return iconNameFor(kind, typeKind, fromBits(modifiers));
    }

    /** Builds the field / method / constructor icon name from the neutral modifiers. */
    private static String memberIcon(String base, ElementModifiers modifiers) {
        String accessSuffix = accessSuffix(modifiers);
        if ("constructor".equals(base)) {
            return "constructor" + accessSuffix + "_16.png";
        }
        if (modifiers.isStatic()) {
            return base + "_static" + accessSuffix + "_16.png";
        }
        return base + accessSuffix + "_16.png";
    }

    /** Maps the visibility onto the icon-name suffix (private / protected / package-private). */
    private static String accessSuffix(ElementModifiers modifiers) {
        return switch (modifiers.visibility()) {
            case PRIVATE -> "_private";
            case PROTECTED -> "_protected";
            case PUBLIC -> "";
            case PACKAGE_PRIVATE -> "_package_private";
        };
    }

    /** Converts {@link Modifier} bits to the neutral model, for the completion/breadcrumb callers. */
    private static ElementModifiers fromBits(int bits) {
        Visibility visibility = Modifier.isPublic(bits) ? Visibility.PUBLIC
                : Modifier.isProtected(bits) ? Visibility.PROTECTED
                : Modifier.isPrivate(bits) ? Visibility.PRIVATE
                : Visibility.PACKAGE_PRIVATE;
        return new ElementModifiers(visibility, Modifier.isStatic(bits),
                Modifier.isFinal(bits), Modifier.isAbstract(bits));
    }

    private static Image loadIcon(String iconName) {
        try {
            return new Image(Objects.requireNonNull(ElementIcons.class.getResource(iconName)).toExternalForm());
        } catch (Exception ex) {
            return null;
        }
    }
}
