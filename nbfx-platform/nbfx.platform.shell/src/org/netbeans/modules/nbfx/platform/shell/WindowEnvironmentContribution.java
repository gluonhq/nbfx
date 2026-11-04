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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.List;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.actions.Command;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * A product/IDE contribution to the generic {@code WindowEnvironment}. The default environment
 * composes the contributions in lookup order; a contribution returns the first non-null answer for
 * each operation. Contributions are per shell/JVM and should not hold global state.
 *
 * @since 1.0
 */
public interface WindowEnvironmentContribution {

    /** Creates the bars for a detached window, or {@code null} when this contribution has none. */
    default Region createDetachedBars(Lookup context, List<Command> scopedCommands) {
        return null;
    }

    /** Reveals {@code file} in the navigator view at {@code index}. */
    default boolean revealInNavigator(FileObject file, int index) {
        return false;
    }

    /** Selects the next open project. */
    default boolean selectNextProject() {
        return false;
    }

    /** Selects the previous open project. */
    default boolean selectPreviousProject() {
        return false;
    }

    /** The home pane for {@code tab}, or {@code null}. */
    default TabPane homeOf(Tab tab) {
        return null;
    }

    /** Whether a navigator reveal is in progress. */
    default boolean isNavigatorRevealInProgress() {
        return false;
    }

    /** Marks a navigator reveal as in progress. */
    default void setNavigatorRevealInProgress(boolean inProgress) {
    }
}
