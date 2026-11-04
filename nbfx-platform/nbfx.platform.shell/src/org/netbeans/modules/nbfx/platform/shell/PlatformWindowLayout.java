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

import org.netbeans.api.nbfx.views.WindowLayoutRegistration;

/**
 * The platform's first-run tree: the navigator left of the editor at the explorer area's weight.
 * Products add panes to it by declaring their own {@code default} layout in the layer, which
 * overrides this one.
 */
@WindowLayoutRegistration(id = "default", tree = "H[0.3000](P:NAVIGATOR,P:MAIN)")
final class PlatformWindowLayout {

    private PlatformWindowLayout() {
    }
}
