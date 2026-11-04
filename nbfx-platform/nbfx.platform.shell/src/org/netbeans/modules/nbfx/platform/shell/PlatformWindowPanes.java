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

import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.api.nbfx.views.PaneRegistration;

/**
 * The platform's permanent window panes, declared like product panes.
 */
@PaneRegistration(id = "MAIN", displayName = "Editor", kind = PaneKind.EDITOR,
        area = "editor", permanent = true, keepWhenEmpty = true, position = 10)
@PaneRegistration(id = "NAVIGATOR", displayName = "Navigator", kind = PaneKind.VIEW,
        area = "explorer", permanent = true, keepWhenEmpty = false, position = 20)
final class PlatformWindowPanes {

    private PlatformWindowPanes() {
    }
}
