package com.gluonhq.netbeans.nbfx.ui.context;

import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import com.gluonhq.netbeans.nbfx.api.view.ViewSelectionContext;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

import org.openide.util.lookup.ServiceProvider;

/**
 * Default {@link ViewSelectionContext}: holds the view whose tab is selected in the focused pane as
 * observable JavaFX state. It is updated by the application shell (which owns the panes and the focus
 * tracking) and read by {@code nbfx.view.context} to feed the global action context.
 */
@ServiceProvider(service = ViewSelectionContext.class)
public class ViewSelectionContextImpl implements ViewSelectionContext {

    private final ObjectProperty<ViewProvider> activeView =
            new SimpleObjectProperty<>(this, "activeView");

    @Override
    public ObservableValue<ViewProvider> activeView() {
        return activeView;
    }

    @Override
    public void setActiveView(ViewProvider view) {
        activeView.set(view);
    }
}
