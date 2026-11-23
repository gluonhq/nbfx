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

import javafx.scene.control.TabPane;

/**
 * The stable boundary of a window pane: a role and the JavaFX node that renders it. A
 * {@link org.netbeans.spi.nbfx.shell.WindowPaneProvider} returns this type instead of the shell's
 * concrete tab pane, so the tab implementation stays internal.
 * <p>
 * The current docking implementation still hosts the pane's tabs in the shell's own tab pane; a
 * provider therefore returns the pane the shell created for it. A fully custom node type is a
 * later widening of the window abstraction and is not part of this contract.
 *
 * @since 1.0
 */
public interface WindowPaneHost {

    /** The stable role of this pane. */
    PaneRole role();

    /** The JavaFX node rendering the pane. */
    TabPane pane();
}
