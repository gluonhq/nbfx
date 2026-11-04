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
package org.netbeans.modules.nbfx.platform.workspace;

import org.netbeans.api.nbfx.view.DockLocation;
import org.netbeans.api.nbfx.views.AreaRegistration;

/**
 * The platform's default view areas, declared in the layer like product areas.
 */
@AreaRegistration(id = "editor", displayName = "Editor", location = DockLocation.CENTER,
        weight = 1.0d, position = 10)
@AreaRegistration(id = "explorer", displayName = "Explorer", location = DockLocation.LEFT,
        weight = 0.3d, position = 20)
@AreaRegistration(id = "navigator", displayName = "Navigator", location = DockLocation.LEFT_BOTTOM,
        weight = 0.5d, position = 30)
@AreaRegistration(id = "properties", displayName = "Properties", location = DockLocation.RIGHT,
        weight = 0.3d, position = 40)
@AreaRegistration(id = "output", displayName = "Output", location = DockLocation.CENTER_BOTTOM,
        weight = 0.3d, position = 50)
final class PlatformViewAreas {

    private PlatformViewAreas() {
    }
}
