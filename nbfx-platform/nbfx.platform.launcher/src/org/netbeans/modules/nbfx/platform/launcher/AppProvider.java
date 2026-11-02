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
package org.netbeans.modules.nbfx.platform.launcher;

import java.nio.file.Path;
import javafx.application.Application;

/**
 * Supplies the JavaFX application class the platform launcher starts. The IDE provides the default
 * implementation; a downstream product can provide its own without changing the launcher.
 */
public interface AppProvider {

    /**
     * The JavaFX application to launch.
     *
     * @return the application class
     */
    Class<? extends Application> applicationClass();

    /**
     * Where this provider ranks against the others when several are registered. The launcher starts
     * the provider with the highest priority; the default provider of the platform shell uses
     * {@code 0}, a product application a higher value.
     *
     * @return the priority; higher wins
     */
    default int priority() {
        return 0;
    }

    /**
     * Runs the optional in-application scenario for {@code fixture}. The default refuses.
     *
     * @param fixture the scenario fixture
     * @return the process exit status
     * @throws Exception when the scenario cannot run
     */
    default int runScenario(Path fixture) throws Exception {
        return 1;
    }
}
