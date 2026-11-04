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

/**
 * A declared permanent window pane.
 *
 * @param id            stable role id
 * @param displayName   display name
 * @param kind          editor or view pane
 * @param area          hosted view area id, or empty
 * @param keepWhenEmpty whether an empty pane stays visible
 * @param position      order among panes
 * @since 1.0
 */
public record WindowPane(String id, String displayName, PaneKind kind, String area,
        boolean keepWhenEmpty, int position) {

    /** The pane's role value. */
    public NbfxTabPane.PaneRole role() {
        return NbfxTabPane.PaneRole.of(id);
    }
}
