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
package org.netbeans.api.nbfx.views;

import java.util.Objects;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;

/**
 * Gives a plain {@link TabPane} inside a view - the consoles of Output, the queries of Search -
 * the look of the panes of the main window: the {@value #STYLE_CLASS} style class, whose styles
 * are part of the common control styles of the theme, and the {@code active} pseudo-class while
 * the keyboard focus is anywhere inside the pane, which is what marks the selected tab. Without
 * the latter a tab is marked only while its own header has the focus.
 *
 * @since 1.8
 */
public final class TabPaneActivation {

    /** The style class of a tab pane drawn as the panes of the main window. */
    public static final String STYLE_CLASS = "nbfx-tab-pane";

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private TabPaneActivation() {
    }

    /**
     * Styles {@code tabs} as the panes of the main window and marks it {@code active} while the
     * keyboard focus is within it - on a tab header or anywhere in the content of a tab.
     *
     * @param tabs the tab pane
     */
    public static void markActiveOnFocus(TabPane tabs) {
        Objects.requireNonNull(tabs, "tabs");
        if (!tabs.getStyleClass().contains(STYLE_CLASS)) {
            tabs.getStyleClass().add(STYLE_CLASS);
        }
        // The focus owner of the scene is asked each time it changes, as the panes of the main
        // window do: the mark is then what the scene says now, whatever the history of the pane's
        // focus-within flag - tabs replaced, closed or moved to another window while they held it.
        tabs.sceneProperty().flatMap(Scene::focusOwnerProperty)
                .subscribe(owner -> tabs.pseudoClassStateChanged(ACTIVE, isWithin(owner, tabs)));
    }

    /** Whether {@code node} is {@code ancestor} or inside it; a pane out of any scene has no focus owner. */
    private static boolean isWithin(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }
}
