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

import org.netbeans.api.nbfx.shell.ShellProfileRegistration;
import org.netbeans.api.nbfx.shell.ShellProfileRegistrations;

/** Neutral default chrome profiles. */
@ShellProfileRegistrations({
    @ShellProfileRegistration(id = "main", windowKind = "MAIN",
            regions = {"BRAND", "MENUBAR", "TOOLBAR", "DOCK", "STATUS", "OVERLAY"},
            decoration = "NATIVE", systemMenuBar = true),
    @ShellProfileRegistration(id = "floating-editor", windowKind = "FLOATING",
            regions = {"TOOLBAR", "DOCK", "STATUS", "OVERLAY"},
            decoration = "NATIVE", systemMenuBar = false),
    @ShellProfileRegistration(id = "floating-tool", windowKind = "FLOATING",
            regions = {"TOOLBAR", "DOCK", "STATUS", "OVERLAY"},
            decoration = "NATIVE", systemMenuBar = false),
    @ShellProfileRegistration(id = "presentation", windowKind = "CUSTOM",
            regions = {"DOCK", "OVERLAY"}, decoration = "UNDECORATED", systemMenuBar = false)
})
final class PlatformShellProfiles {

    private PlatformShellProfiles() {
    }
}
