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
package org.netbeans.modules.nbfx.platform.nodes;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.netbeans.api.nbfx.nodes.PropertyModel;
import org.netbeans.api.nbfx.property.PropertyEditor;
import org.netbeans.api.nbfx.property.PropertyEditorRegistration;

/** The editor for {@link String} values. */
@PropertyEditorRegistration(valueType = String.class, position = 10)
public final class TextPropertyEditor implements PropertyEditor {

    @Override
    public Class<?> valueType() {
        return String.class;
    }

    @Override
    public Node createEditor(PropertyModel property) {
        TextField field = new TextField(PropertyEditors.display(property.getValue()));
        field.setEditable(property.isWritable());
        field.textProperty().addListener((observable, old, now) -> {
            if (property.isWritable()) {
                property.setValue(now);
            }
        });
        return field;
    }
}
