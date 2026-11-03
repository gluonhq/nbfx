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

import java.util.List;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.status.StatusAlignment;
import org.netbeans.modules.nbfx.platform.status.StatusBar;
import org.netbeans.modules.nbfx.platform.status.StatusElement;
import org.netbeans.modules.nbfx.platform.status.StatusElementRegistry;

/**
 * The platform's default chrome: menu bar and tool bars above the docking area and the composed
 * status bar below it. Concrete controls stay here; the product-facing {@link ShellChrome} contract
 * only exposes nodes.
 *
 * @since 1.0
 */
public final class DefaultShellChrome implements ShellChrome {

    private final ShellContext context;
    private final ShellMenuBar menuBuilder;
    private final ShellToolBars toolBarBuilder;
    private final VBox top;
    private final StatusBar statusBar;
    private final CaretStatusElement caret = new CaretStatusElement();
    private final LineSeparatorStatusElement lineSeparator = new LineSeparatorStatusElement();
    private Subscription caretSubscription;
    private Subscription lineSeparatorSubscription;

    /** Creates the default chrome for {@code context}. */
    public DefaultShellChrome(ShellContext context) {
        this.context = context;
        this.menuBuilder = new ShellMenuBar(context);
        this.toolBarBuilder = new ShellToolBars(context);
        boolean hasMenus = !menuBuilder.menuBar().getMenus().isEmpty();
        boolean hasToolBars = !toolBarBuilder.container().getToolBars().isEmpty();
        this.top = hasMenus || hasToolBars
                ? new VBox(menuBuilder.menuBar(), toolBarBuilder.container())
                : null;
        this.statusBar = new StatusBar(StatusElementRegistry.discover(List.of(
                new StatusElement("caret", StatusAlignment.RIGHT, 10, caret),
                new StatusElement("line-separator", StatusAlignment.RIGHT, 20, lineSeparator))));
        ObservableValue<EditorDocument> activeDocument = EditorContexts.activeDocument();
        if (activeDocument != null) {
            caretSubscription = activeDocument.flatMap(EditorDocument::caretInfoProperty)
                    .subscribe(caret::setInfo);
            lineSeparatorSubscription = activeDocument.flatMap(EditorDocument::lineSeparatorProperty)
                    .subscribe(lineSeparator::setSeparator);
        }
        lineSeparator.setOnChange(separator -> {
            if (activeDocument != null) {
                EditorDocument document = activeDocument.getValue();
                if (document != null) {
                    document.setLineSeparator(separator);
                }
            }
        });
    }

    /** The context this chrome was created for. */
    ShellContext context() {
        return context;
    }

    /** The main tool bar container, for {@link ShellApplication}'s persistence hook. */
    ToolBarContainer toolBars() {
        return toolBarBuilder.container();
    }

    @Override
    public Node top() {
        return top;
    }

    @Override
    public Node bottom() {
        return statusBar;
    }

    @Override
    public void dispose() {
        if (caretSubscription != null) {
            caretSubscription.unsubscribe();
        }
        if (lineSeparatorSubscription != null) {
            lineSeparatorSubscription.unsubscribe();
        }
        menuBuilder.dispose();
    }
}
