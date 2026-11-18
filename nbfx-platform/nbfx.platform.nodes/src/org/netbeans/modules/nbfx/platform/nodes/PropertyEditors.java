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

import java.util.HashMap;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.nodes.PropertyModel;
import org.netbeans.api.nbfx.property.PropertyEditor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.Lookups;

/**
 * Resolves the {@link PropertyEditor} for a property: the registered editor with the lowest
 * {@code position} whose value type can hold the property's value type, or a read-only label when
 * none matches. Editors are registered with {@code @PropertyEditorRegistration}, which writes
 * them under {@link LayerPaths#PROPERTY_EDITORS}.
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
        if (valueType == null) {
            return null;
        }
        Map<String, Integer> positions = editorPositions();
        PropertyEditor best = null;
        int bestPosition = Integer.MAX_VALUE;
        for (PropertyEditor editor : Lookups.forPath(LayerPaths.PROPERTY_EDITORS)
                .lookupAll(PropertyEditor.class)) {
            if (!editor.valueType().isAssignableFrom(valueType)) {
                continue;
            }
            int position = positions.getOrDefault(editor.getClass().getName(), Integer.MAX_VALUE);
            if (best == null || position < bestPosition) {
                best = editor;
                bestPosition = position;
            }
        }
        return best;
    }

    /**
     * The {@code position} attributes of the generated layer entries, by implementation class.
     * The layer is the registration source; this only recovers the ordering attribute.
     */
    private static Map<String, Integer> editorPositions() {
        Map<String, Integer> positions = new HashMap<>();
        FileObject folder = FileUtil.getConfigFile(LayerPaths.PROPERTY_EDITORS);
        if (folder == null) {
            return positions;
        }
        for (FileObject entry : folder.getChildren()) {
            Object className = entry.getAttribute("instanceClass");
            Object position = entry.getAttribute("position");
            if (className instanceof String name && position instanceof Integer value) {
                positions.put(name, value);
            }
        }
        return positions;
    }

    static String display(Object value) {
        return value == null ? "" : value.toString();
    }
}
