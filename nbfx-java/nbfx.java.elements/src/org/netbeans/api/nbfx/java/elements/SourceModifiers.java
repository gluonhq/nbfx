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
package org.netbeans.api.nbfx.java.elements;

import java.lang.reflect.Modifier;
import java.util.Set;
import org.netbeans.api.nbfx.language.ElementModifiers;
import org.netbeans.api.nbfx.language.ElementModifiers.Visibility;

/**
 * Java-specific modifier conversion: maps Java language-model modifiers or {@link Modifier} bits to
 * the neutral {@link ElementModifiers} vocabulary, and keeps the bitmask form used by the Java
 * completion providers.
 */
public final class SourceModifiers {

    private SourceModifiers() {
    }

    /**
     * Converts a set of {@code javax.lang.model.element.Modifier} constants into the neutral
     * modifier model.
     *
     * <p>Flags are matched by enum <em>name</em> rather than identity: at runtime the set may
     * hold constants of nb-javac's own copy of {@code javax.lang.model.element.Modifier}
     * (bundled by {@code libs.nbjavacapi}), which is a different class than the JDK's that
     * this module sees, so an identity-based {@code contains} would never match.</p>
     *
     * @param modifiers language-model modifiers (any {@code Modifier}-like enum constants),
     *                  may be {@code null}
     * @return the neutral modifier model
     */
    public static ElementModifiers toElementModifiers(Set<?> modifiers) {
        Visibility visibility = Visibility.PACKAGE_PRIVATE;
        boolean isStatic = false;
        boolean isFinal = false;
        boolean isAbstract = false;
        if (modifiers != null) {
            for (Object modifier : modifiers) {
                if (modifier instanceof Enum<?> flag) {
                    switch (flag.name()) {
                        case "PUBLIC" -> visibility = Visibility.PUBLIC;
                        case "PROTECTED" -> visibility = Visibility.PROTECTED;
                        case "PRIVATE" -> visibility = Visibility.PRIVATE;
                        case "STATIC" -> isStatic = true;
                        case "FINAL" -> isFinal = true;
                        case "ABSTRACT" -> isAbstract = true;
                        default -> {
                        }
                    }
                }
            }
        }
        return new ElementModifiers(visibility, isStatic, isFinal, isAbstract);
    }

    /**
     * Converts a {@link Modifier} bitmask into the neutral modifier model.
     *
     * @param bits {@link Modifier}-compatible bits
     * @return the neutral modifier model
     */
    public static ElementModifiers fromBits(int bits) {
        Visibility visibility = Modifier.isPublic(bits) ? Visibility.PUBLIC
                : Modifier.isProtected(bits) ? Visibility.PROTECTED
                : Modifier.isPrivate(bits) ? Visibility.PRIVATE
                : Visibility.PACKAGE_PRIVATE;
        return new ElementModifiers(visibility, Modifier.isStatic(bits),
                Modifier.isFinal(bits), Modifier.isAbstract(bits));
    }

    /**
     * Converts a set of {@code javax.lang.model.element.Modifier} constants into the
     * equivalent {@link Modifier} bitmask (visibility, {@code static},
     * {@code final} and {@code abstract} only), for the Java completion providers.
     *
     * @param modifiers language-model modifiers (any {@code Modifier}-like enum constants),
     *                  may be {@code null}
     * @return the reflect-compatible bitmask
     */
    public static int toModifierBits(Set<?> modifiers) {
        int bits = 0;
        if (modifiers != null) {
            for (Object modifier : modifiers) {
                if (modifier instanceof Enum<?> flag) {
                    bits |= switch (flag.name()) {
                        case "PUBLIC" -> Modifier.PUBLIC;
                        case "PROTECTED" -> Modifier.PROTECTED;
                        case "PRIVATE" -> Modifier.PRIVATE;
                        case "STATIC" -> Modifier.STATIC;
                        case "FINAL" -> Modifier.FINAL;
                        case "ABSTRACT" -> Modifier.ABSTRACT;
                        default -> 0;
                    };
                }
            }
        }
        return bits;
    }
}
