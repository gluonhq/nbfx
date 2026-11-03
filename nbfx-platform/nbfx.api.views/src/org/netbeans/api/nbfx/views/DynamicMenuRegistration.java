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
 * Registers a dynamic section of a menu. The annotated class implements the dynamic-menu factory
 * SPI. The processor writes {@code NbFx/<path>/<id>.dynamic} with the factory class, position and
 * optional separator; the parent folder supplies the menu id.
 * <p>
 * The menu itself must be declared with {@link MenuRegistration}; dynamic entries are sections of
 * an existing menu. There is no separate dynamic-menu root.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(DynamicMenuRegistrations.class)
public @interface DynamicMenuRegistration {

    /** The menu surface, for example {@code Menus/File} or {@code Menus/Window/IDE Tools}. */
    String path();

    /** Stable id of the dynamic section, used as the layer file name. */
    String id();

    /** Position among the menu's static references and dynamic sections. */
    int position();

    /** Optional display name for a dynamic submenu; empty for a section. */
    String displayName() default "";

    /** Whether a separator is drawn before this section. */
    boolean separatorBefore() default false;
}
