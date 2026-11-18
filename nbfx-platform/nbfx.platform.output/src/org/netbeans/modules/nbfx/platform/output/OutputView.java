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
package org.netbeans.modules.nbfx.platform.output;

import java.util.List;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.openide.util.NbBundle;

/**
 * The content of the "Output" tab - NetBeans' Output window: one inner tab per console, styled as
 * the panes of the main window, and beside them the vertical tool bar acting on the console of the
 * selected tab - <em>Re-run</em>, <em>Re-run with Different Parameters</em>, <em>Stop</em> and the
 * build tool's settings. Closing an inner tab forgets its console.
 */
final class OutputView extends BorderPane {

    private static final PseudoClass OUTPUT_TABS = PseudoClass.getPseudoClass("output-tabs");

    /** What the shell's tab panes carry while they hold the focus: the style of the marked tab. */
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private final ConsoleOutput output;
    private final OutputSettings settings;
    private final TabPane tabs = new TabPane();
    private final Button rerun = button("rerun", "TIP_Rerun");
    private final Button rerunWith = button("rerun-mod", "TIP_RerunWith");
    private final Button stop = button("stop", "TIP_Stop");
    private final Button toolSettings = button("options", "TIP_ToolSettings");
    /** Follows the selected console's rerun and stop: kept in a field to move it from console to console. */
    private final InvalidationListener controlsListener = obs -> updateToolBar();
    private ConsoleModel selected;

    OutputView(ConsoleOutput output, OutputSettings settings) {
        this.output = Objects.requireNonNull(output);
        this.settings = Objects.requireNonNull(settings);
        getStyleClass().add("output-view");
        getStylesheets().add(Objects.requireNonNull(OutputView.class.getResource("output.css")).toExternalForm());

        tabs.getStyleClass().add("nbfx-tab-pane");
        tabs.pseudoClassStateChanged(OUTPUT_TABS, true);
        // As the panes of the main window: the selected tab is marked while the focus is anywhere
        // in the pane - in the console text - and not only on the tab header.
        tabs.focusWithinProperty().subscribe(within -> tabs.pseudoClassStateChanged(ACTIVE, within));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        setLeft(createToolBar());
        setCenter(tabs);

        List.copyOf(output.consoles()).forEach(this::addTab);
        output.consoles().addListener((ListChangeListener<ConsoleModel>) change -> {
            while (change.next()) {
                change.getRemoved().forEach(this::removeTab);
                change.getAddedSubList().forEach(this::addTab);
            }
        });
        tabs.getSelectionModel().selectedItemProperty().subscribe(tab -> select(tab == null ? null : consoleOf(tab)));
        tabs.getTabs().subscribe(() -> tabs.setVisible(!tabs.getTabs().isEmpty()));
    }

    /** The inner tab pane, for tests. */
    TabPane tabs() {
        return tabs;
    }

    /** The Re-run button, for tests. */
    Button rerunButton() {
        return rerun;
    }

    /** The Stop button, for tests. */
    Button stopButton() {
        return stop;
    }

    private void addTab(ConsoleModel console) {
        Tab tab = new Tab(console.getName());
        tab.setUserData(console);
        tab.setContent(new ConsolePane(console, settings,
                () -> tabs.getSelectionModel().select(tab), () -> output.close(console)));
        // The close button removed the tab already; forgetting the console finds nothing more to remove.
        tab.setOnClosed(e -> {
            ((ConsolePane) tab.getContent()).dispose();
            output.close(console);
        });
        tab.setContextMenu(createTabMenu(console));
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    private void removeTab(ConsoleModel console) {
        tabs.getTabs().removeIf(tab -> {
            if (consoleOf(tab) != console) {
                return false;
            }
            ((ConsolePane) tab.getContent()).dispose();
            return true;
        });
    }

    private static ConsoleModel consoleOf(Tab tab) {
        return (ConsoleModel) tab.getUserData();
    }

    /** NetBeans' tab menu: Close Tab, Close All Tabs, Close Other Tabs. */
    private ContextMenu createTabMenu(ConsoleModel console) {
        MenuItem close = new MenuItem(message("CTX_CloseTab"));
        close.setOnAction(e -> output.close(console));
        MenuItem closeAll = new MenuItem(message("CTX_CloseAllTabs"));
        closeAll.setOnAction(e -> List.copyOf(output.consoles()).forEach(output::close));
        MenuItem closeOthers = new MenuItem(message("CTX_CloseOtherTabs"));
        closeOthers.setOnAction(e -> List.copyOf(output.consoles()).stream()
                .filter(other -> other != console)
                .forEach(output::close));
        return new ContextMenu(close, closeAll, closeOthers);
    }

    // --- tool bar ---

    private ToolBar createToolBar() {
        rerun.setOnAction(e -> {
            Runnable action = selected == null ? null : selected.rerunProperty().get();
            if (action != null) {
                action.run();
            }
        });
        stop.setOnAction(e -> {
            Runnable action = selected == null ? null : selected.stopProperty().get();
            if (action != null) {
                action.run();
            }
        });
        // TODO needs the "run with parameters" dialog of the build tool behind the console
        rerunWith.setDisable(true);
        // TODO needs the options dialog page of the build tool behind the console
        toolSettings.setDisable(true);
        ToolBar bar = new ToolBar(rerun, rerunWith, stop, toolSettings);
        bar.setOrientation(Orientation.VERTICAL);
        bar.getStyleClass().add("output-toolbar");
        bar.setMinHeight(0);
        updateToolBar();
        return bar;
    }

    /** Makes the tool bar act on {@code console}, the one of the selected tab, or on none. */
    private void select(ConsoleModel console) {
        if (selected != null) {
            selected.rerunProperty().removeListener(controlsListener);
            selected.stopProperty().removeListener(controlsListener);
        }
        selected = console;
        if (selected != null) {
            selected.rerunProperty().addListener(controlsListener);
            selected.stopProperty().addListener(controlsListener);
        }
        updateToolBar();
    }

    /** Re-run needs a console that can run again and is not running; Stop one that is running. */
    private void updateToolBar() {
        boolean running = selected != null && selected.stopProperty().get() != null;
        rerun.setDisable(selected == null || running || selected.rerunProperty().get() == null);
        stop.setDisable(!running);
    }

    private static Button button(String icon, String tooltipKey) {
        ImageView view = new ImageView(new Image(Objects.requireNonNull(
                OutputView.class.getResource("icons/" + icon + ".png"), () -> "Missing icon " + icon).toExternalForm()));
        view.setFitWidth(16);
        view.setFitHeight(16);
        view.setPreserveRatio(true);
        Button button = new Button();
        button.setGraphic(view);
        button.setTooltip(new Tooltip(message(tooltipKey)));
        button.getStyleClass().add("output-tool-button");
        button.setFocusTraversable(false);
        return button;
    }

    private static String message(String key) {
        return NbBundle.getMessage(OutputView.class, key);
    }
}
