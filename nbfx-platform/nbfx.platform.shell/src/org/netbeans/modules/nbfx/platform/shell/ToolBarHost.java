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

/**
 * A chrome that owns the main tool bars. The shell uses this capability for toolbar persistence
 * without knowing the concrete chrome implementation; a custom chrome may implement it when it
 * wants the standard persistence and detached-window mirroring.
 *
 * @since 1.0
 */
public interface ToolBarHost {

    /** The main window's tool bar container. */
    ToolBarContainer toolBars();
}
