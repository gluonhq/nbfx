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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies, on the headless JavaFX toolkit, that a tab pane is marked active exactly while the
 * keyboard focus is inside it - also after the focused content went away with its tab.
 */
public class TabPaneActivationTest {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    @BeforeClass
    public static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(30, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    public void activeWhileTheFocusIsInside() throws Exception {
        onFx(() -> {
            Button first = new Button("first");
            Button second = new Button("second");
            Button elsewhere = new Button("elsewhere");
            TabPane tabs = new TabPane(new Tab("A", first), new Tab("B", second));
            TabPaneActivation.markActiveOnFocus(tabs);
            Stage stage = show(tabs, elsewhere);
            try {
                assertEquals(1, tabs.getStyleClass().stream().filter(TabPaneActivation.STYLE_CLASS::equals).count());

                elsewhere.requestFocus();
                assertFalse(isActive(tabs));

                first.requestFocus();
                assertTrue(isActive(tabs));

                tabs.getSelectionModel().select(1);
                second.requestFocus();
                assertTrue(isActive(tabs));

                elsewhere.requestFocus();
                assertFalse(isActive(tabs));
            } finally {
                stage.hide();
            }
        });
    }

    /**
     * A query run again replaces its tab while the focus is in its results: the focused node leaves
     * the scene, and the pane must not stay marked once the focus is somewhere else.
     */
    @Test
    public void notActiveAfterTheFocusedTabWasReplaced() throws Exception {
        onFx(() -> {
            Button results = new Button("results");
            Button elsewhere = new Button("elsewhere");
            TabPane tabs = new TabPane(new Tab("Usages of scene", results), new Tab("Other", new Button("other")));
            TabPaneActivation.markActiveOnFocus(tabs);
            Stage stage = show(tabs, elsewhere);
            try {
                results.requestFocus();
                assertTrue(isActive(tabs));

                tabs.getTabs().set(0, new Tab("Usages of scene", new Button("new results")));
                elsewhere.requestFocus();

                assertTrue(elsewhere.isFocused());
                assertFalse("the focus is outside the pane", isActive(tabs));
            } finally {
                stage.hide();
            }
        });
    }

    /** The mark is what the scene's focus owner says, also when the pane's own focus flags were not told. */
    @Test
    public void notActiveOnceThePaneIsOutOfTheScene() throws Exception {
        onFx(() -> {
            Button results = new Button("results");
            Button elsewhere = new Button("elsewhere");
            TabPane tabs = new TabPane(new Tab("A", results));
            TabPaneActivation.markActiveOnFocus(tabs);
            VBox holder = new VBox(tabs);
            Stage stage = show(holder, elsewhere);
            try {
                results.requestFocus();
                assertTrue(isActive(tabs));

                holder.getChildren().clear();
                assertFalse("a pane out of the scene holds no focus", isActive(tabs));

                // Back in the scene, the mark is again what the scene's focus owner says.
                holder.getChildren().add(tabs);
                assertEquals(stage.getScene().getFocusOwner() == results, isActive(tabs));
                elsewhere.requestFocus();
                assertFalse(isActive(tabs));
                results.requestFocus();
                assertTrue(isActive(tabs));
            } finally {
                stage.hide();
            }
        });
    }

    /** Two marked panes: the mark follows the focus from one to the other. */
    @Test
    public void theMarkMovesWithTheFocus() throws Exception {
        onFx(() -> {
            Button inFirst = new Button("in first");
            Button inSecond = new Button("in second");
            TabPane first = new TabPane(new Tab("A", inFirst));
            TabPane second = new TabPane(new Tab("B", inSecond));
            TabPaneActivation.markActiveOnFocus(first);
            TabPaneActivation.markActiveOnFocus(second);
            Stage stage = show(first, second);
            try {
                inFirst.requestFocus();
                assertTrue(isActive(first));
                assertFalse(isActive(second));

                inSecond.requestFocus();
                assertFalse(isActive(first));
                assertTrue(isActive(second));
            } finally {
                stage.hide();
            }
        });
    }

    private static boolean isActive(TabPane tabs) {
        return tabs.getPseudoClassStates().contains(ACTIVE);
    }

    private static Stage show(javafx.scene.Node... nodes) {
        Stage stage = new Stage();
        stage.setScene(new Scene(new VBox(nodes), 400, 300));
        stage.show();
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        return stage;
    }

    private static void onFx(Runnable task) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue("timed out waiting for the JavaFX thread", done.await(30, TimeUnit.SECONDS));
        if (error.get() instanceof AssertionError assertion) {
            throw assertion;
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }
}
