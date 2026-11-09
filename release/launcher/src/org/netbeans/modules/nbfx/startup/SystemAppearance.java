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
package org.netbeans.modules.nbfx.startup;

import java.io.PrintWriter;
import java.util.Locale;
import org.netbeans.CLIHandler;

/**
 * Lets AWT, and with it JavaFX, follow the macOS appearance.
 *
 * <p>The platform initialises AWT before the FX launcher starts JavaFX. On macOS, AWT pins the
 * application's {@code NSAppearance} to Aqua (light) unless {@code apple.awt.application.appearance}
 * is {@code system}. JavaFX reads the OS color scheme from that appearance, so
 * {@code Platform.getPreferences().getColorScheme()} is then always {@code LIGHT}, the OS listener
 * never fires, and the "Follow System" theme mode shows the light theme on a dark desktop.
 *
 * <p>The release's {@code etc/netbeans.conf} passes the property (upstream's default options do),
 * but {@code ant run} (nbbuild's {@code tryme}) starts {@code nbexec} without the conf. A
 * {@link #WHEN_BOOT} handler runs in the bootstrap before anything touches AWT, so setting the
 * property here covers every start. An explicit {@code -J-D} on the command line wins.
 */
public final class SystemAppearance extends CLIHandler {

    static final String PROPERTY = "apple.awt.application.appearance";

    public SystemAppearance() {
        super(WHEN_BOOT);
    }

    /** Sets the property on macOS when it is not set; {@code true} when it did. */
    static boolean apply() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return false;
        }
        if (System.getProperty(PROPERTY) != null) {
            return false;
        }
        System.setProperty(PROPERTY, "system");
        return true;
    }

    @Override
    protected int cli(Args args) {
        apply();
        return 0;
    }

    @Override
    protected void usage(PrintWriter w) {
    }
}
