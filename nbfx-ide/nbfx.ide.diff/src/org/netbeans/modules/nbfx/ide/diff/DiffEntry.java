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
package org.netbeans.modules.nbfx.ide.diff;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.scene.Node;

/**
 * A file a {@link DiffProvider} has a diff of: a row of the table above the diff, and how to get
 * the two texts to compare when the row is selected.
 *
 * @param name the name of the file
 * @param location where the file is, as the provider tells it - a path relative to a repository
 * @param status how the file changed, in words - "Modified" - or empty
 * @param kind how the file changed, by which its name is coloured
 * @param icon makes the icon beside the name, a fresh node each time, on the JavaFX Application
 *        Thread; {@code null}, or a supplier of {@code null}, for none
 * @param loader reads the two texts
 * @since 1.1
 */
public record DiffEntry(String name, String location, String status, Kind kind, Supplier<Node> icon,
        Loader loader) {

    /** How a file changed, as far as a diff view tells changes apart. */
    public enum Kind {
        ADDED, MODIFIED, DELETED, CONFLICTED,
        /** None of the others, or not changed. */
        OTHER
    }

    /** Reads the two texts of an entry; called off the JavaFX Application Thread. */
    @FunctionalInterface
    public interface Loader {
        DiffPair load() throws IOException;
    }

    /** All but the icon are required. */
    public DiffEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(loader, "loader");
    }
}
