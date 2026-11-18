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
package org.netbeans.modules.nbfx.platform.output;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The consoles of the application: a concurrent map by id, and an observable, ordered list of
 * them for the Output view. It knows nothing of windows: what brings the Output window on screen
 * for a console is given to it.
 * <p>
 * There is one registry for the application, {@link #getDefault()}, which the console service
 * hands consoles out of and the Output view shows; the two meet here and not in the Lookup, so the
 * view shows the platform's consoles whatever else is registered as the service, and whenever it
 * is created.
 */
final class ConsoleRegistry {

    private static final ConsoleRegistry DEFAULT = new ConsoleRegistry();

    private final Map<String, ConsoleModel> consoles = new ConcurrentHashMap<>();
    private final ObservableList<ConsoleModel> ordered = FXCollections.observableArrayList();
    private volatile Consumer<ConsoleModel> revealer = console -> {
    };

    /** A registry of its own, for tests. */
    ConsoleRegistry() {
    }

    /** The registry of the application. */
    static ConsoleRegistry getDefault() {
        return DEFAULT;
    }

    /**
     * Sets what brings the Output window on screen when a console asks to be shown. It is called
     * on the JavaFX Application Thread.
     */
    void setRevealer(Consumer<ConsoleModel> revealer) {
        this.revealer = revealer;
    }

    /** The console whose id is its name; see {@link #console(String, String)}. */
    ConsoleModel console(String name) {
        return console(name, name);
    }

    /**
     * The console identified by {@code id}, created with the name {@code name} if there is none.
     * Safe to call from any thread; shows nothing.
     */
    ConsoleModel console(String id, String name) {
        return consoles.computeIfAbsent(id, consoleId -> {
            ConsoleModel created = new ConsoleModel(consoleId, name, console -> revealer.accept(console));
            FxThread.run(() -> ordered.add(created));
            return created;
        });
    }

    /** The consoles in the order they were created, for the output view. */
    ObservableList<ConsoleModel> consoles() {
        return ordered;
    }

    /**
     * Forgets {@code console}, whose tab was closed: asking for its id again gives a new, empty
     * console in a new tab. An activity still writing to the closed console is not stopped; what it
     * writes is no longer shown. The console lets go of its re-run action and of its run, and of
     * whatever those hold. Called on the JavaFX Application Thread.
     */
    void close(ConsoleModel console) {
        consoles.remove(console.getId(), console);
        ordered.remove(console);
        console.detach();
        console.release();
    }
}
