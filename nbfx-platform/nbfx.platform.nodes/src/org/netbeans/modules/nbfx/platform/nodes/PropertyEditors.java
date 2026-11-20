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

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.netbeans.api.nbfx.layer.LayerEntry;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.nodes.PropertyModel;
import org.netbeans.api.nbfx.property.PropertyEditor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Resolves the {@link PropertyEditor} for a property: the inline editor with the lowest
 * {@code position} whose value type can hold the property's value type, or a read-only label when
 * none matches. Editors are registered with {@code @PropertyEditorRegistration}, which writes them
 * under {@link LayerPaths#PROPERTY_EDITORS}; non-inline editors are skipped.
 */
public final class PropertyEditors {

    private PropertyEditors() {
    }

    public static Node createEditor(PropertyModel property) {
        if (property.isWritable()) {
            PropertyEditor editor = findEditor(property.getValueType());
            if (editor != null) {
                return editor.createEditor(property);
            }
        }
        return new Label(display(property.getValue()));
    }

    static PropertyEditor findEditor(Class<?> valueType) {
        return findEditor(FileUtil.getConfigFile(LayerPaths.PROPERTY_EDITORS), valueType);
    }

    static PropertyEditor findEditor(FileObject folder, Class<?> valueType) {
        return findEditor(LayerInstances.load(folder, PropertyEditor.class), valueType);
    }

    static PropertyEditor findEditor(Iterable<LayerEntry<PropertyEditor>> entries, Class<?> valueType) {
        if (valueType == null) {
            return null;
        }
        for (LayerEntry<PropertyEditor> entry : entries) {
            if (Boolean.FALSE.equals(entry.attribute("inline"))) {
                continue;
            }
            if (entry.instance().valueType().isAssignableFrom(valueType)) {
                return entry.instance();
            }
        }
        return null;
    }

    static String display(Object value) {
        return value == null ? "" : value.toString();
    }
}
