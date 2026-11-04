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
package org.netbeans.modules.nbfx.platform.shell.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.openide.util.lookup.NamedServiceDefinition;

/**
 * Runs a {@link Runnable} once the JavaFX window is showing. This is the nbfx counterpart of
 * {@code org.openide.windows.OnShowing}, which is unavailable here because the nbfx modules must
 * not depend on the Swing window system.
 * <p>
 * Tasks run on the JavaFX Application Thread. Use {@link ShellWindowProvider} from the global
 * lookup to reach the window. The named-service path is {@code NbFx/UIReady}, deliberately
 * distinct from the Swing path {@code Modules/UIReady}.
 *
 * @since 1.0
 */
@NamedServiceDefinition(
        path = "NbFx/UIReady",
        serviceType = Runnable.class,
        position = "position"
)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface OnUiReady {

    /** Order among the UI-ready tasks; lower values run first. */
    int position() default Integer.MAX_VALUE;
}
