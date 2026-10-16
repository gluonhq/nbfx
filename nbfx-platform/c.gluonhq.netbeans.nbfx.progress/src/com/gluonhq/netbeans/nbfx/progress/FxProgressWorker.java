package com.gluonhq.netbeans.nbfx.progress;

import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.netbeans.modules.progress.spi.InternalHandle;
import org.netbeans.modules.progress.spi.ProgressEvent;
import org.netbeans.modules.progress.spi.ProgressUIWorkerWithModel;
import org.netbeans.modules.progress.spi.TaskModel;
import org.openide.util.NbBundle;

/**
 * The FX progress worker: it consumes the headless progress events and keeps a JavaFX-observable
 * model of the running tasks, rendered by {@link FxProgressElement} in the status line and by an
 * all-tasks popup. Reuses the NetBeans {@link org.netbeans.modules.progress.spi.Controller}/
 * {@link TaskModel} for the task bookkeeping.
 */
public final class FxProgressWorker implements ProgressUIWorkerWithModel {

    private static final FxProgressWorker INSTANCE = new FxProgressWorker();

    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final Map<InternalHandle, Task> byHandle = new HashMap<>();
    private final ReadOnlyObjectWrapper<Task> active = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(-1);
    private final ReadOnlyIntegerWrapper count = new ReadOnlyIntegerWrapper(0);

    private TaskModel model;
    private Popup popup;
    private Node anchor;

    private FxProgressWorker() {
    }

    public static FxProgressWorker getInstance() {
        return INSTANCE;
    }

    /** The running tasks, in start order. Read on the JavaFX thread. */
    public ObservableList<Task> getTasks() {
        return FXCollections.unmodifiableObservableList(tasks);
    }

    /** The task shown in the status line (the watched task, else the most recent one). */
    public ReadOnlyObjectProperty<Task> activeTaskProperty() {
        return active.getReadOnlyProperty();
    }

    /** The active task's progress in [0,1], or {@code -1} for an indeterminate task / no task. */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    /** The number of running tasks. */
    public ReadOnlyIntegerProperty taskCountProperty() {
        return count.getReadOnlyProperty();
    }

    /** Anchors the popup to the status element that shows this worker. */
    public void setAnchor(Node anchor) {
        this.anchor = anchor;
    }

    @Override
    public void setModel(TaskModel model) {
        this.model = model;
    }

    @Override
    public void showPopup() {
        Platform.runLater(this::showPopupFx);
    }

    @Override
    public void processProgressEvent(ProgressEvent event) {
        Platform.runLater(() -> apply(event));
    }

    @Override
    public void processSelectedProgressEvent(ProgressEvent event) {
        Platform.runLater(() -> {
            Task task = byHandle.get(event.getSource());
            if (task != null) {
                active.set(task);
                progress.set(task.getProgress());
            }
        });
    }

    /** Cancels {@code task} when the underlying handle allows it. */
    public void cancel(Task task) {
        if (task.handle().isAllowCancel()) {
            task.handle().requestCancel();
        }
    }

    private void apply(ProgressEvent event) {
        InternalHandle handle = event.getSource();
        switch (event.getType()) {
            case ProgressEvent.TYPE_START -> {
                Task task = new Task(handle, displayName(event, handle), event.getMessage());
                byHandle.put(handle, task);
                tasks.add(task);
                if (event.isWatched() || active.get() == null) {
                    active.set(task);
                }
                refresh();
            }
            case ProgressEvent.TYPE_FINISH -> {
                Task task = byHandle.remove(handle);
                if (task != null) {
                    tasks.remove(task);
                }
                if (active.get() != null && active.get().handle() == handle) {
                    active.set(tasks.isEmpty() ? null : tasks.get(tasks.size() - 1));
                }
                refresh();
            }
            default -> {
                Task task = byHandle.get(handle);
                if (task != null) {
                    task.update(displayName(event, handle), event.getMessage(), event.getPercentageDone());
                    if (active.get() == task) {
                        progress.set(task.getProgress());
                    }
                }
            }
        }
        count.set(tasks.size());
    }

    private void refresh() {
        Task task = active.get();
        progress.set(task == null ? -1 : task.getProgress());
        count.set(tasks.size());
    }

    private static String displayName(ProgressEvent event, InternalHandle handle) {
        String name = event.getDisplayName();
        return name != null ? name : handle.getDisplayName();
    }

    private void showPopupFx() {
        if (tasks.isEmpty() || anchor == null || anchor.getScene() == null) {
            return;
        }
        if (popup == null) {
            popup = createPopup();
        }
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        popup.show(anchor, bounds.getMinX(), bounds.getMinY() - popup.getHeight());
    }

    private Popup createPopup() {
        Label title = new Label(NbBundle.getMessage(FxProgressWorker.class, "Progress.title"));
        title.setStyle("-fx-font-weight: bold;");

        ListView<Task> list = new ListView<>(tasks);
        list.setPrefWidth(360);
        list.setPrefHeight(200);
        list.setCellFactory(view -> new TaskCell(this));

        VBox content = new VBox(6, title, list);
        content.getStyleClass().add("progress-popup");
        content.setStyle("-fx-padding: 8; -fx-background-color: -fx-background;");

        Popup p = new Popup();
        p.setAutoHide(true);
        p.getContent().add(content);
        return p;
    }

    /** One running task, shown in the status line and in the popup. */
    public static final class Task {

        private final InternalHandle handle;
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty detail = new SimpleStringProperty();
        private final SimpleDoubleProperty progress = new SimpleDoubleProperty(-1);

        Task(InternalHandle handle, String name, String detail) {
            this.handle = handle;
            this.name.set(name);
            this.detail.set(detail);
            this.progress.set(handle.getPercentageDone());
        }

        InternalHandle handle() {
            return handle;
        }

        public StringProperty nameProperty() {
            return name;
        }

        public StringProperty detailProperty() {
            return detail;
        }

        public double getProgress() {
            return progress.get();
        }

        public boolean isCancellable() {
            return handle.isAllowCancel();
        }

        void update(String name, String detail, double progress) {
            if (name != null) {
                this.name.set(name);
            }
            this.detail.set(detail);
            this.progress.set(progress);
        }
    }

    /** A popup row: name + detail, a progress bar and a cancel button. */
    private static final class TaskCell extends ListCell<Task> {

        private final FxProgressWorker worker;
        private final Label name = new Label();
        private final Label detail = new Label();
        private final ProgressBar bar = new ProgressBar();
        private final Button cancel = new Button();
        private final HBox root;

        TaskCell(FxProgressWorker worker) {
            this.worker = worker;
            name.setStyle("-fx-font-weight: bold;");
            detail.getStyleClass().add("progress-detail");
            bar.setPrefWidth(120);
            cancel.setText(NbBundle.getMessage(FxProgressWorker.class, "Progress.cancel"));

            VBox texts = new VBox(name, detail);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            root = new HBox(8, texts, spacer, bar, cancel);
            root.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Task task, boolean empty) {
            super.updateItem(task, empty);
            if (empty || task == null) {
                setGraphic(null);
                return;
            }
            name.textProperty().bind(task.nameProperty());
            detail.textProperty().bind(task.detailProperty());
            bar.setProgress(task.getProgress());
            cancel.setVisible(task.isCancellable());
            cancel.setOnAction(event -> worker.cancel(task));
            setGraphic(root);
        }
    }
}
