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
 * Declares a complete product layout as standard layout XML. The annotation processor validates the
 * XML and writes it to {@code NbFx/Layouts/<id>.xml}; {@link LayoutRegistry} reads the resulting
 * immutable {@link LayoutSpec} at runtime.
 * <p>
 * The XML layout id must equal {@link #id()}. Includes are not supported in annotation XML because
 * the generated layer resource is self-contained. XML is canonicalized to UTF-8, and {@code #key}
 * display names are validated against the annotated package's {@code Bundle.properties}. Ids must be
 * unique within a compilation; across modules the standard layer merge precedence applies, so
 * products should use globally unique ids.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(LayoutRegistrations.class)
public @interface LayoutRegistration {

    /** The stable layout id and generated layer file base name. */
    String id();

    /** A complete version 1 {@code <layout>} XML document using UTF-8-compatible text. */
    String xml();
}
