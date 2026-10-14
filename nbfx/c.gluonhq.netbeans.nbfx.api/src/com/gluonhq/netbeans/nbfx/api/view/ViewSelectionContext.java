package com.gluonhq.netbeans.nbfx.api.view;

import javafx.beans.value.ObservableValue;

/**
 * The view that is currently active: the selected tab of the focused pane.
 * <p>
 * This is the nbfx replacement for the activated {@code TopComponent} of the Swing window system:
 * it is the anchor the global action context ({@code FxActionContext}) derives the current selection
 * from. The application shell, which owns the panes and the focus tracking, publishes the active view
 * here; commands and views only observe it.
 * <p>
 * The context is resolved through the global {@link org.openide.util.Lookup}.
 */
public interface ViewSelectionContext {

    /**
     * The active view, or {@code null} when no view tab is selected (for example while an editor is
     * active, or when the window is empty). Observable, so callers can follow the focus from tab to
     * tab.
     *
     * @return the observable active view
     */
    ObservableValue<ViewProvider> activeView();

    /**
     * Publishes a new active view.
     *
     * @param view the view whose tab is selected, or {@code null} when none is
     */
    void setActiveView(ViewProvider view);
}
