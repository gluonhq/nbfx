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

import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;

/**
 * Package-private runtime handle for a floating pane. The public <code>DockWindow</code> contract is
 * introduced separately once the runtime semantics are proven.
 *
 * @param owner the manager that created the window
 * @param pane the floating pane
 * @param stage the window showing it
 * @param anchor the former dock-area anchor, or {@code null} for a newly created content window
 */
record FloatingWindow(DefaultWindowManager owner, NbfxTabPane pane, Stage stage,
        DockArea.Anchor<TabPane> anchor) {
}
