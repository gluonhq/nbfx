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
package org.netbeans.api.nbfx.language;

import java.util.Objects;

/**
 * Toolkit- and language-neutral modifier information for an element: what a renderer needs to choose
 * the icon and styling variant, without exposing {@code java.lang.reflect.Modifier} bits to a model
 * API.
 *
 * @param visibility the element's visibility
 * @param isStatic   whether the element is static
 * @param isFinal    whether the element is final
 * @param isAbstract whether the element is abstract
 */
public record ElementModifiers(Visibility visibility, boolean isStatic, boolean isFinal,
        boolean isAbstract) {

    /** Visibility categories shared by the completion, breadcrumbs and usages views. */
    public enum Visibility {
        /** Public element. */
        PUBLIC,
        /** Protected element. */
        PROTECTED,
        /** Package-private element. */
        PACKAGE_PRIVATE,
        /** Private element. */
        PRIVATE
    }

    /** No modifiers: package-private, non-static, non-final and non-abstract. */
    public static final ElementModifiers NONE = new ElementModifiers(Visibility.PACKAGE_PRIVATE, false, false, false);

    public ElementModifiers {
        Objects.requireNonNull(visibility, "visibility");
    }

    /** Whether the element is public. */
    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }

    /** Whether the element is protected. */
    public boolean isProtected() {
        return visibility == Visibility.PROTECTED;
    }

    /** Whether the element is package-private. */
    public boolean isPackagePrivate() {
        return visibility == Visibility.PACKAGE_PRIVATE;
    }

    /** Whether the element is private. */
    public boolean isPrivate() {
        return visibility == Visibility.PRIVATE;
    }
}
