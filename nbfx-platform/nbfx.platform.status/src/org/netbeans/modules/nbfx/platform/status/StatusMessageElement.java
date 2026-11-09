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
package org.netbeans.modules.nbfx.platform.status;

import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.netbeans.api.nbfx.status.StatusAlignment;
import org.netbeans.api.nbfx.status.StatusMessageService;
import org.netbeans.api.nbfx.status.StatusRegistration;
import org.openide.util.Lookup;

/**
 * The transient status message: the fill element at the start of the status bar, like the Swing
 * {@code StatusLine}. The message takes the remaining width and truncates with an ellipsis; the
 * progress bar and the fixed elements stay on the right.
 */
@StatusRegistration(id = "message", alignment = StatusAlignment.FILL, position = 0)
public final class StatusMessageElement implements StatusElementHolder {

    private final Consumer<String> listener = this::textChanged;
    private Label node;
    private StatusMessageService service;

    @Override
    public String getId() {
        return "message";
    }

    @Override
    public Node getNode() {
        if (node == null) {
            node = new Label();
            node.getStyleClass().add("status-bar-message");
            node.setTextOverrun(OverrunStyle.ELLIPSIS);
            node.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(node, Priority.ALWAYS);
            service = Lookup.getDefault().lookup(StatusMessageService.class);
            if (service != null) {
                service.addListener(listener);
                textChanged(service.getMessage());
            }
        }
        return node;
    }

    private void textChanged(String message) {
        Label label = node;
        if (label == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            label.setText(message);
        } else {
            Platform.runLater(() -> label.setText(message));
        }
    }

    @Override
    public void dispose() {
        if (service != null) {
            service.removeListener(listener);
            service = null;
        }
        node = null;
    }
}
