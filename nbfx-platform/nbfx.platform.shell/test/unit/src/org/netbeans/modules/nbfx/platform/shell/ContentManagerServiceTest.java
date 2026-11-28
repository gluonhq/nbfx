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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.netbeans.modules.nbfx.platform.shell.AppState.TabEntry;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Verifies what the editor area does with a document that is no file's: one tab for it however
 * often it is asked for before that tab is there, and a session that is kept whatever such a
 * document says of how to open it again.
 */
public class ContentManagerServiceTest {

    @Test
    public void aDocumentAskedForTwiceBeforeItsTabIsThereHasOneTab() throws Exception {
        ContentManagerService service = JavaFxTestSupport.onFx(ContentManagerService::new);
        EditorDocument document = document(() -> null);

        // Both in one step of the FX thread: the tab of the first is not attached yet.
        JavaFxTestSupport.onFx(() -> {
            service.openDocument(document, null);
            service.openDocument(document, null);
        });
        assertEquals(1, tabsOf(document));

        // And once it is there, asked for again and again it is the one brought to the front.
        JavaFxTestSupport.onFx(() -> {
            service.openDocument(document, null);
            service.openDocument(document, null);
        });
        assertEquals(1, tabsOf(document));

        // Closed, and asked for twice in a row once more: one tab again, not none.
        JavaFxTestSupport.onFx(() -> {
            for (TabPane pane : NbfxTabPane.tabPanes()) {
                pane.getTabs().removeIf(tab -> NbfxTabPane.documentOf(tab) == document);
            }
            service.openDocument(document, null);
            service.openDocument(document, null);
        });
        assertEquals(1, tabsOf(document));
        JavaFxTestSupport.onFx(() -> {
            for (TabPane pane : NbfxTabPane.tabPanes()) {
                pane.getTabs().removeIf(tab -> NbfxTabPane.documentOf(tab) == document);
            }
        });
    }

    @Test
    public void aDocumentThatFailsToTellOrTellsTooMuchIsNotRestoredAndNothingIsThrown() {
        SavedDocument small = new SavedDocument("git-diff", "HEAD_VS_INDEX\nfile\n/repo/A.java");
        TabEntry entry = SessionRestorer.documentEntry(document(() -> small));
        assertEquals(small, entry.saved());

        assertNull(SessionRestorer.documentEntry(document(() -> null)));
        assertNull(SessionRestorer.documentEntry(document(() -> {
            throw new IllegalStateException("broken");
        })));
        // More than a layout, kept as one preference with every tab in it, carries.
        assertNull(SessionRestorer.documentEntry(document(
                () -> new SavedDocument("big", "x".repeat(AppState.MOST_DOCUMENT_STATE)))));
    }

    private static int tabsOf(EditorDocument document) throws Exception {
        // In a step of its own: after the ones the tabs are attached in.
        JavaFxTestSupport.onFx(() -> { });
        return JavaFxTestSupport.onFx(() -> {
            int tabs = 0;
            for (TabPane pane : NbfxTabPane.tabPanes()) {
                for (Tab tab : pane.getTabs()) {
                    if (NbfxTabPane.documentOf(tab) == document) {
                        tabs++;
                    }
                }
            }
            return tabs;
        });
    }

    /** A document of no file, saved as {@code saved} says. */
    private static EditorDocument document(Supplier<SavedDocument> saved) {
        Region node = new Region();
        ReadOnlyBooleanWrapper never = new ReadOnlyBooleanWrapper(false);
        ReadOnlyBooleanWrapper editable = new ReadOnlyBooleanWrapper(true);
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getNode":
                    return node;
                case "getTitle":
                    return "A.txt [ Diff ]";
                case "getSavedState":
                    return saved.get();
                case "getLookup":
                    return Lookup.EMPTY;
                case "editableProperty":
                    return editable.getReadOnlyProperty();
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "document";
                default:
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if (method.getReturnType() == ReadOnlyBooleanProperty.class) {
                        return never.getReadOnlyProperty();
                    }
                    if (method.getReturnType() == ObservableValue.class) {
                        return new SimpleObjectProperty<>();
                    }
                    return method.getReturnType() == boolean.class ? Boolean.FALSE
                            : method.getReturnType() == int.class ? Integer.valueOf(0) : null;
            }
        };
        return (EditorDocument) Proxy.newProxyInstance(EditorDocument.class.getClassLoader(),
                new Class<?>[] {EditorDocument.class}, handler);
    }
}
