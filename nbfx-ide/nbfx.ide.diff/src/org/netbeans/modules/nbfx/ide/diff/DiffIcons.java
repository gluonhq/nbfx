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
package org.netbeans.modules.nbfx.ide.diff;

import javafx.scene.Node;
import javafx.scene.layout.Region;

/** The icons of a diff that the tool icons do not have, drawn by the style sheet. */
final class DiffIcons {

    private DiffIcons() {
    }

    /** An arrow to the right: what is on the left goes over what is on the right. */
    static Node revert() {
        return icon("diff-icon-revert");
    }

    /** Two arrows to the right: all of the left goes over the right. */
    static Node revertAll() {
        return icon("diff-icon-revert-all");
    }

    private static Node icon(String styleClass) {
        Region icon = new Region();
        icon.getStyleClass().addAll("diff-icon", styleClass);
        return icon;
    }
}
