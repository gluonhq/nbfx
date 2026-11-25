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
package org.netbeans.api.nbfx.shell;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one shell chrome region under {@code NbFx/ShellRegions}. The annotation emits metadata
 * only; Node-producing contributions and renderers are registered separately.
 *
 * @since 1.1
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(ShellRegionRegistrations.class)
public @interface ShellRegionRegistration {

    /** Built-in short/canonical id or namespaced product region id. */
    String id();

    /** Built-in short/canonical kind or namespaced product kind. */
    String kind();

    /** Order among sibling regions. */
    int position() default Integer.MAX_VALUE;

    /** Exact atomic profile ids; empty means all profiles. */
    String[] profiles() default {};

    /** Optional CSS style class for the region wrapper. */
    String styleClass() default "";

    /** Initial visibility policy. */
    boolean visibleByDefault() default true;
}
