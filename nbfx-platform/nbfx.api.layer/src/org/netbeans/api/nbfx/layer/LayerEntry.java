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
package org.netbeans.api.nbfx.layer;

import java.util.Objects;
import org.openide.filesystems.FileObject;

/**
 * One instance read from a layer folder: the created object and the entry that declares it.
 * The entry carries the registration attributes and its file name is the registration id.
 *
 * @param <T> the instance type
 * @param instance the created instance, never {@code null}
 * @param file the layer entry that declares {@code instance}, never {@code null}
 * @since 1.4
 */
public record LayerEntry<T>(T instance, FileObject file) {

    /** Creates an entry. */
    public LayerEntry {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(file, "file");
    }

    /** The registration id: the layer entry's base name. */
    public String id() {
        return file.getName();
    }

    /** The layer attribute {@code name}, or {@code null} when it is not set. */
    public Object attribute(String name) {
        return file.getAttribute(name);
    }
}
