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

import org.netbeans.api.nbfx.view.DockLocation;
import org.netbeans.api.nbfx.views.AreaRegistration;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.api.nbfx.views.PaneRegistration;

/**
 * A product-style pane registration used by the platform conformance tests.
 */
@PaneRegistration(id = "RIGHT_SIDEBAR", displayName = "Right Sidebar",
        kind = PaneKind.VIEW, area = "right-sidebar", keepWhenEmpty = true, position = 30)
@AreaRegistration(id = "right-sidebar", displayName = "Right Sidebar",
        location = DockLocation.RIGHT, weight = 0.2, position = 30)
public final class CustomPaneFixture {

    private CustomPaneFixture() {
    }
}
