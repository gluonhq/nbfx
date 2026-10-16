package com.gluonhq.netbeans.nbfx.ui.project;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;

/**
 * The projects whose trees are currently loading. Each load is reported as a cancellable system task
 * through the standard progress API, so the status line and the running-tasks popup show it without
 * the window wiring any progress itself.
 */
public final class ProjectLoads {

    private record Load(File dir, ProgressHandle handle) {
    }

    /** Project path (as in {@link OpenProject#getPath()}) to its ongoing load. */
    private final Map<String, Load> loading = new LinkedHashMap<>();

    /**
     * Records that {@code dir}, whose project path is {@code path}, has started loading, and shows a
     * cancellable "Opening &lt;project&gt;" task. {@code onCancel} is run when the user cancels it.
     */
    public void begin(String path, File dir, Runnable onCancel) {
        if (path == null) {
            return;
        }
        ProgressHandle handle = ProgressHandle.createSystemHandle(
                NbBundle.getMessage(ProjectLoads.class, "OpeningProject", dir.getName()),
                onCancel == null ? null : () -> {
                    onCancel.run();
                    return true;
                });
        handle.start();
        loading.put(path, new Load(dir, handle));
    }

    /** Records that the project at {@code path} is no longer loading, ending its task. */
    public void end(String path) {
        Load load = path == null ? null : loading.remove(path);
        if (load != null) {
            load.handle().finish();
        }
    }

    public boolean isLoading(String path) {
        return path != null && loading.containsKey(path);
    }

    /** The directory of the project loading at {@code path}, or {@code null} if it is not loading. */
    public File dirOf(String path) {
        Load load = path == null ? null : loading.get(path);
        return load == null ? null : load.dir();
    }

    /** The paths of the projects still loading, in the order their loads were started. */
    public List<String> paths() {
        return List.copyOf(loading.keySet());
    }
}
