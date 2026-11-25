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
 * Registers a {@link ShellContribution} implementation as a direct {@code .instance} child of a shell
 * region. The layer owns placement metadata; the contribution creates a fresh Node per shell mount.
 *
 * @since 1.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(ShellContributionRegistrations.class)
public @interface ShellContributionRegistration {

    /** Built-in short/canonical region id or namespaced product region id. */
    String region();

    /** Order within the region. */
    int position() default Integer.MAX_VALUE;

    /** Exact atomic profile ids; empty means all profiles. */
    String[] profiles() default {};
}
