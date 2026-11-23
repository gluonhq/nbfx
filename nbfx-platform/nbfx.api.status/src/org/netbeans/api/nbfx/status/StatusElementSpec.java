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
package org.netbeans.api.nbfx.status;

import java.util.Objects;

/**
 * A discovered status bar element registration: its metadata and the class to instantiate when the
 * status bar is built. Discovery never creates the element itself.
 *
 * @param id        the element id (the layer file's base name)
 * @param alignment the slot the element is placed in
 * @param position  the position within the slot
 * @param type      the element class, with a public no-argument constructor
 * @since 1.2
 */
public record StatusElementSpec(String id, StatusAlignment alignment, int position,
        Class<? extends StatusElement> type) {

    /** Creates a spec. */
    public StatusElementSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(type, "type");
    }
}
