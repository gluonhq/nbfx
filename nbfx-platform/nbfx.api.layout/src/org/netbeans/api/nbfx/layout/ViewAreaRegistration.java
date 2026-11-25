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
package org.netbeans.api.nbfx.layout;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares neutral logical-area metadata under {@code NbFx/LayoutAreas}. The registration produces
 * an {@link AreaStyle#EMPTY} area; use {@link LayoutRegistration} when the area needs styles or is
 * part of a complete layout tree.
 * <p>
 * Ids are canonicalized through {@link PaneRole}. A {@code #key} display name is validated against
 * the annotated package's {@code Bundle.properties} at compile time. Ids must be unique within a
 * compilation; across modules the standard layer merge precedence applies.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(ViewAreaRegistrations.class)
public @interface ViewAreaRegistration {

    /** Stable area id; built-in short forms and namespaced product roles are accepted. */
    String id();

    /** Neutral or product-defined area kind. */
    String kind() default "MAIN";

    /** Localized display name or {@code #bundle-key}. */
    String displayName();

    /** Routing default used when no explicit placement is supplied. */
    DockLocation location() default DockLocation.CENTER;

    /** Routing weight used when no explicit divider is supplied. */
    double weight() default 1.0d;

    /** Presentation inherited by groups unless they override it. */
    String presentation() default "tabbed";

    /** Operations supported by the area. */
    ViewAreaCapability[] capabilities() default {};

    /** Stable docking-policy registration id. */
    String policyId() default "default";

    /** Initial visibility policy. */
    ViewAreaVisibility visibility() default ViewAreaVisibility.STARTUP;

    /** Behavior when the area has no content. */
    EmptyAreaPolicy emptyPolicy() default EmptyAreaPolicy.KEEP;

    /** Order among sibling areas. */
    int order() default 0;
}
