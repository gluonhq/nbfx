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

import java.util.Objects;
import java.util.function.Supplier;
import javafx.scene.Node;

/**
 * One of the ways a {@link DiffProvider} can compare what it shows - the last commit with the
 * working tree, with the index - offered as a toggle above the diff.
 *
 * @param id what the provider tells its modes apart by
 * @param title what the toggle is for, shown as its tooltip
 * @param icon makes the graphic of the toggle, a fresh node each time; called on the JavaFX
 *        Application Thread
 * @since 1.1
 */
public record DiffMode(String id, String title, Supplier<Node> icon) {

    /** All are required. */
    public DiffMode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
    }
}
