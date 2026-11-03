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

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

/**
 * The product's application identity: name, window title and optional icon. A product registers an
 * implementation to replace the neutral default.
 *
 * @since 1.0
 */
public interface ShellBranding {

    /**
     * The precedence when several brandings are registered. The highest priority wins, so a
     * product branding can replace the neutral platform default without ambiguity.
     *
     * @return the priority; the default is {@code 0}
     */
    default int priority() {
        return 0;
    }

    /** The application name, used by the default title. */
    String applicationName();

    /** The window title for {@code context}. */
    default String title(ShellContext context) {
        return applicationName();
    }

    /** The application icon, or {@code null}. */
    default Node icon(ShellContext context) {
        return null;
    }

    /**
     * A live title for {@code context}, or {@code null} when the title does not change. The shell
     * follows it after the initial {@link #title(ShellContext) title}.
     *
     * @param context the shell context
     * @return the observable title, or {@code null}
     */
    default ObservableValue<String> titleProperty(ShellContext context) {
        return null;
    }
}
