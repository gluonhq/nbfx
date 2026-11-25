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
package org.netbeans.modules.nbfx.platform.actions;

import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.input.KeyCombination;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.editor.EditorDocument;

/** Binds a canonical layer command to the current value of an observable editor document. */
final class ScopedLayerCommand implements Command {

    private static final Logger LOG = Logger.getLogger(ScopedLayerCommand.class.getName());

    private final Command base;
    private final ObservableValue<EditorDocument> activeDocument;
    private final ReadOnlyBooleanWrapper disabled = new ReadOnlyBooleanWrapper(true);
    private final InvalidationListener documentListener = observable -> rebind();
    private final InvalidationListener disabledListener = observable -> updateDisabled();
    private Command current;
    private ReadOnlyBooleanProperty currentDisabled;
    private boolean closed;

    ScopedLayerCommand(Command base, ObservableValue<EditorDocument> activeDocument) {
        this.base = base;
        this.activeDocument = activeDocument;
        activeDocument.addListener(documentListener);
        rebind();
    }

    @Override
    public String getId() {
        return base.getId();
    }

    @Override
    public String getText() {
        return current == null ? base.getText() : current.getText();
    }

    @Override
    public KeyCombination getAccelerator() {
        return current == null ? base.getAccelerator() : current.getAccelerator();
    }

    @Override
    public Node getIcon() {
        return current == null ? base.getIcon() : current.getIcon();
    }

    @Override
    public boolean isDisabled() {
        return disabled.get();
    }

    @Override
    public ReadOnlyBooleanProperty disabledProperty() {
        return disabled.getReadOnlyProperty();
    }

    @Override
    public void run() {
        if (!isDisabled() && current != null) {
            current.run();
        }
    }

    @Override
    public void dispose() {
        if (closed) {
            return;
        }
        closed = true;
        activeDocument.removeListener(documentListener);
        clearCurrent();
        disabled.set(true);
    }

    private void rebind() {
        if (closed) {
            return;
        }
        clearCurrent();
        EditorDocument document = activeDocument.getValue();
        if (document == null) {
            updateDisabled();
            return;
        }
        try {
            current = base.forContext(document.getLookup());
            if (current != null) {
                currentDisabled = current.disabledProperty();
                if (currentDisabled != null) {
                    currentDisabled.addListener(disabledListener);
                }
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot bind layer command " + getId(), ex);
            current = null;
            currentDisabled = null;
        }
        updateDisabled();
    }

    private void clearCurrent() {
        if (currentDisabled != null) {
            currentDisabled.removeListener(disabledListener);
            currentDisabled = null;
        }
        if (current != null && current != base) {
            try {
                current.dispose();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot dispose scoped command " + getId(), ex);
            }
        }
        current = null;
    }

    private void updateDisabled() {
        disabled.set(current == null || current.isDisabled());
    }
}
