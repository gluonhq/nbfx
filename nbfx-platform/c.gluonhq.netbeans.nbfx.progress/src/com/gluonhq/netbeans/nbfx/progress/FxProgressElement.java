package com.gluonhq.netbeans.nbfx.progress;

import com.gluonhq.netbeans.nbfx.annotations.FxStatusAlignment;
import com.gluonhq.netbeans.nbfx.annotations.FxStatusRegistration;
import com.gluonhq.netbeans.nbfx.statusbar.FxStatusElement;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import org.openide.util.NbBundle;

/**
 * The status-line progress element: an aggregate bar, the active task's name and a button that opens
 * the all-tasks popup (plus a cancel button for a cancellable active task). Driven by the
 * {@link FxProgressWorker} singleton, which receives events from the reused progress controller.
 */
@FxStatusRegistration(id = "progress", alignment = FxStatusAlignment.LEFT, position = 100)
public final class FxProgressElement implements FxStatusElement {

    private final FxProgressWorker worker = FxProgressWorker.getInstance();
    private final ProgressBar bar = new ProgressBar();
    private final Label label = new Label();
    private final Button taskCount = new Button();
    private final Button cancel = new Button();
    private final HBox node;

    /** Creates the element. Must be called on the JavaFX Application Thread. */
    public FxProgressElement() {
        bar.setPrefWidth(120);
        taskCount.setOnAction(event -> worker.showPopup());
        cancel.setText(NbBundle.getMessage(FxProgressElement.class, "Progress.cancel"));
        cancel.setOnAction(event -> {
            FxProgressWorker.Task active = worker.activeTaskProperty().get();
            if (active != null) {
                worker.cancel(active);
            }
        });
        worker.setAnchor(taskCount);

        label.setText("");
        worker.activeTaskProperty().addListener((obs, old, task) -> updateActive(task));
        updateActive(worker.activeTaskProperty().get());

        bar.progressProperty().bind(worker.progressProperty());
        taskCount.textProperty().bind(worker.taskCountProperty().asString());
        taskCount.visibleProperty().bind(worker.taskCountProperty().greaterThan(1));
        taskCount.managedProperty().bind(taskCount.visibleProperty());

        node = new HBox(6, bar, label, taskCount, cancel);
        node.setAlignment(Pos.CENTER_LEFT);
        node.visibleProperty().bind(worker.taskCountProperty().greaterThan(0));
        node.managedProperty().bind(node.visibleProperty());
    }

    private void updateActive(FxProgressWorker.Task task) {
        label.setText(task == null ? "" : task.nameProperty().get());
        cancel.setVisible(task != null && task.isCancellable());
        cancel.setManaged(cancel.isVisible());
    }

    @Override
    public String getId() {
        return "progress";
    }

    @Override
    public FxStatusAlignment alignment() {
        return FxStatusAlignment.LEFT;
    }

    @Override
    public int position() {
        return 100;
    }

    @Override
    public Node getNode() {
        return node;
    }
}
