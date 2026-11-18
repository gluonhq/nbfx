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
package org.netbeans.modules.nbfx.ide.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javafx.collections.ListChangeListener;
import org.netbeans.api.nbfx.output.ConsolePanel;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.project.ProjectRegistry;
import org.openide.util.Lookup;

/**
 * The <em>Re-run</em> actions the build runner gives the consoles, and their end: a console outlives
 * the build that filled it, and its re-run action whatever started the build, so closing a project
 * takes the re-run actions of the builds under its folder away, and with them what they hold.
 */
final class Reruns {

    /** The folder each console's re-run action builds in; the consoles are not kept alive by it. */
    private static final Map<ConsolePanel, Path> FOLDERS = new WeakHashMap<>();
    private static boolean listening;

    private Reruns() {
    }

    /** Gives {@code console} the action running the build in {@code dir} again. */
    static void set(ConsolePanel console, Path dir, Runnable rerun) {
        synchronized (FOLDERS) {
            FOLDERS.put(console, dir.toAbsolutePath().normalize());
            listen();
        }
        console.setRerun(rerun);
    }

    /** Takes the re-run action away from every console whose build is in or under {@code root}. */
    static void forget(Path root) {
        Path folder = root.toAbsolutePath().normalize();
        List<ConsolePanel> consoles = new ArrayList<>();
        synchronized (FOLDERS) {
            FOLDERS.entrySet().removeIf(entry -> {
                if (entry.getValue().startsWith(folder)) {
                    consoles.add(entry.getKey());
                    return true;
                }
                return false;
            });
        }
        consoles.forEach(console -> console.setRerun(null));
    }

    /** Follows the open projects, once there is a registry of them. */
    private static void listen() {
        if (listening) {
            return;
        }
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        if (registry == null) {
            return;
        }
        listening = true;
        registry.getOpenProjects().addListener((ListChangeListener<OpenProject>) change -> {
            while (change.next()) {
                for (OpenProject closed : change.getRemoved()) {
                    forget(Path.of(closed.getPath()));
                }
            }
        });
    }
}
