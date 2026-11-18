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

import org.netbeans.api.nbfx.output.ConsolePanel;
import org.netbeans.api.nbfx.output.OutputConsole;
import org.openide.util.lookup.ServiceProvider;

/**
 * The platform's {@link OutputConsole}: the consoles of the application's {@link ConsoleRegistry},
 * which the Output view shows. A console asking to be shown opens the Output window.
 */
@ServiceProvider(service = OutputConsole.class)
public final class ConsoleOutput implements OutputConsole {

    private final ConsoleRegistry registry;

    /** The service of the application, for the Lookup. */
    public ConsoleOutput() {
        this(ConsoleRegistry.getDefault());
        registry.setRevealer(console -> OutputViewProvider.reveal());
    }

    /** A service over {@code registry}, which reveals as it is set to; for tests. */
    ConsoleOutput(ConsoleRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ConsolePanel console(String id, String name) {
        return registry.console(id, name);
    }
}
