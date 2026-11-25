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
package org.netbeans.api.nbfx.actions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * References an already registered action from a UI surface: a menu, tool bar, ribbon, shell region
 * or context menu.
 * <p>
 * The reference is emitted into the generated layer under {@code NbFx/<path>/<id>.ref}, where
 * {@code path} may be {@code Menus/<menu>}, {@code Toolbars/<bar>},
 * {@code ShellRegions/<region>/...} or {@code ContextMenus/<context>}. The action itself is supplied by an
 * {@link ActionRegistration} on the same element or by an action id published by an action
 * provider.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(ActionReferences.class)
public @interface ActionReference {

    /** The id of the action to reference (see {@link ActionRegistration#id()}). */
    String id();

    /** The relative UI surface path, for example {@code "Menus/File"} or
     * {@code "ShellRegions/nbfx:ribbon/Home"}. */
    String path();

    /** The position of the reference relative to its siblings in the same surface. */
    int position() default Integer.MAX_VALUE;

    /** Whether a separator is drawn before this reference. */
    boolean separatorBefore() default false;

    /**
     * An optional content type (MIME type) the reference applies to. When empty the reference applies
     * to every node of the surface; when set, a mime-aware surface only shows it for that type.
     */
    String mimeType() default "";

    /** Optional localized display-name override or {@code #bundle-key}. */
    String displayName() default "";

    /** Optional icon resource override. */
    String iconName() default "";

    /** Optional exact surface context token. */
    String contextType() default "";

    /** Optional exact source language id. */
    String languageId() default "";

    /** Exact atomic profile ids; empty means all profiles. */
    String[] profiles() default {};

    /** Presentation hints encoded as {@code key=value} entries. */
    String[] presentationHints() default {};
}
