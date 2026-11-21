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
 * One class-based {@code .instance} entry read from a layer folder, before its instance is
 * created. The entry carries the declared class and the layer attributes; its file name is the
 * registration id.
 * <p>
 * {@link LayerInstances#registrations} returns these entries;
 * {@link LayerInstances#instantiate} creates the instance on demand.
 *
 * @param <T> the instance type
 * @param file the layer entry that declares the instance, never {@code null}
 * @param type the declared class, never {@code null}
 * @since 1.5
 */
public record LayerRegistration<T>(FileObject file, Class<? extends T> type) {

    /** Creates a registration. */
    public LayerRegistration {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(type, "type");
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
