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
 * Declares neutral physical-group metadata under {@code NbFx/DockGroups}. The registration produces
 * an {@link AreaStyle#EMPTY} group; use {@link LayoutRegistration} when the group needs styles or is
 * part of a complete layout tree.
 * <p>
 * The owning area may be declared in another module. Empty {@link #presentation()} or
 * {@link #policyId()} values inherit from the resolved area at runtime. Ids must be unique within a
 * compilation; across modules the standard layer merge precedence applies.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(DockGroupRegistrations.class)
public @interface DockGroupRegistration {

    /** Stable namespaced group id. */
    String id();

    /** Stable built-in or namespaced owning area id. */
    String area();

    /** Semantic group behavior. */
    PaneKind kind() default PaneKind.VIEW;

    /** Presentation override, or empty to inherit the area's default. */
    String presentation() default "";

    /** Number of content items accepted. */
    ContentCapacity capacity() default ContentCapacity.MANY;

    /** Docking-policy override, or empty to inherit the area's policy. */
    String policyId() default "";

    /** Persistence and empty-group lifecycle. */
    GroupLifetime lifetime() default GroupLifetime.PERSISTENT;
}
