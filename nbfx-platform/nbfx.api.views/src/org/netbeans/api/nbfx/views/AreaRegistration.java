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
import org.netbeans.api.nbfx.view.DockLocation;

/**
 * Declares a view area in the layer. An area is a named docking destination such as the explorer,
 * navigator, properties or a product-specific sidebar. The processor writes
 * {@code NbFx/ViewAreas/<id>} with the location, weight and display name.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(AreaRegistrations.class)
public @interface AreaRegistration {

    /** Stable area id; referenced by {@link ViewRegistration#area()}. */
    String id();

    /** Display name of the area. */
    String displayName();

    /** The docking side the area occupies. */
    DockLocation location();

    /** The share of the available space the area takes, between 0 and 1. */
    double weight() default 0.3d;

    /** Order among areas on the same side. */
    int position() default Integer.MAX_VALUE;
}
