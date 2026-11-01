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
package org.netbeans.modules.nbfx.ide.workbench;

import java.nio.file.Path;
import javafx.application.Application;
import org.netbeans.modules.nbfx.platform.launcher.AppProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * Supplies the IDE application to the platform launcher.
 */
@ServiceProvider(service = AppProvider.class)
public final class NbfxAppProvider implements AppProvider {

    @Override
    public Class<? extends Application> applicationClass() {
        return JavaFXLaunchApp.class;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public int runScenario(Path fixture) {
        return ScenarioRunner.run(fixture);
    }
}
