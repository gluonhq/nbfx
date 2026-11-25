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

import java.util.List;
import javafx.scene.Node;

/**
 * Runtime-owned lifecycle for declarative shell regions. All mount/update/dispose operations run on
 * the JavaFX Application Thread. A mounted Node identity remains stable across updates.
 *
 * @since 1.3
 */
public interface ShellRegionHost {

    /** Regions applicable to {@code profile}, ordered by position/id. */
    List<ShellRegionSpec> regions(ShellChromeProfile profile);

    /** Mounts one region and returns its stable root Node. */
    Node mount(ShellRegionSpec region, ShellRegionContext context);

    /** Updates one existing mount without replacing its root Node. */
    void update(ShellRegionSpec region, Node mounted);

    /** Detaches and disposes one mount; repeated disposal is harmless. */
    void dispose(ShellRegionSpec region, Node mounted);
}
