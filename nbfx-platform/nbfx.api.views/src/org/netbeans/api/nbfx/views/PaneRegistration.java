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
package org.netbeans.api.nbfx.views;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a window pane and the area it hosts. The processor writes
 * {@code NbFx/WindowPanes/<id>} with the kind, area and lifecycle attributes.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(PaneRegistrations.class)
public @interface PaneRegistration {

    /** Stable pane role id; referenced by window layout trees. */
    String id();

    /** Display name of the pane. */
    String displayName();

    /** Whether the pane hosts editors or views. */
    PaneKind kind() default PaneKind.VIEW;

    /** The id of the view area this pane hosts; empty for no declared area. */
    String area() default "";

    /** Whether the pane exists for the whole session, even when empty. */
    boolean permanent() default true;

    /** Whether an empty permanent pane stays visible instead of hiding. */
    boolean keepWhenEmpty() default false;

    /** Order among panes on the same side. */
    int position() default Integer.MAX_VALUE;
}
