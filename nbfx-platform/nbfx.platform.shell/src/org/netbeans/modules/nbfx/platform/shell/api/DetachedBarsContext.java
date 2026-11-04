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

import java.util.List;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
import org.netbeans.api.nbfx.actions.Command;
import org.openide.util.Lookup;

/**
 * The context for building the chrome of one detached window: the shell services, the window's own
 * active document (as an untyped observable, so the generic shell does not name editor types) and
 * the sink for window-scoped commands.
 *
 * @param services       the shell services available to the contribution
 * @param activeDocument the detached window's selected document, observed for changes
 * @param scopedCommands collects the window-scoped commands so the window can dispose them
 * @since 1.0
 */
public record DetachedBarsContext(Lookup services, ObservableValue<?> activeDocument,
        List<Command> scopedCommands) {

    public DetachedBarsContext {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(scopedCommands, "scopedCommands");
    }

    /**
     * The active document as the type the caller expects. The contribution that knows the document
     * type calls this; the generic shell only transports the observable.
     *
     * @param type the document type
     * @param <D>  the document type
     * @return the observable, or {@code null} when the window has no document
     */
    @SuppressWarnings("unchecked")
    public <D> ObservableValue<D> activeDocument(Class<D> type) {
        return activeDocument == null ? null : (ObservableValue<D>) activeDocument;
    }
}
