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
package org.netbeans.api.nbfx.content;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a {@link DropHandler} in the layer. The annotated class must be a public concrete
 * {@link DropHandler} with a public no-argument constructor.
 *
 * @since 1.5
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(DropRegistrations.class)
public @interface DropRegistration {

    /** The drop target kind, as a canonical or short built-in form or a namespaced product kind. */
    String targetKind();

    /** The drop target id within {@link #targetKind()}. */
    String targetId();

    /** Source kinds accepted by the handler, in canonical or short form. */
    String[] sources();

    /** Position of this registration within the drop-handler folder. */
    int position() default Integer.MAX_VALUE;
}
