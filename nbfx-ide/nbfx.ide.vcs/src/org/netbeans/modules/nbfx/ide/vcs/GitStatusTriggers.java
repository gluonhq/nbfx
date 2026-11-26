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
package org.netbeans.modules.nbfx.ide.vcs;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;

/**
 * What makes the {@link GitStatusCache} read a repository again: a file created, changed, deleted or
 * renamed through the IDE's filesystem, and a window of the IDE getting the focus, which is when
 * work done outside it - a commit from a terminal, a branch switch - is caught up with.
 *
 * @since 1.3
 */
final class GitStatusTriggers {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private GitStatusTriggers() {
    }

    /** Starts watching, once, for {@code cache}. Callable from any thread. */
    static void install(GitStatusCache cache) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        FileUtil.addFileChangeListener(new FileChanges(cache));
        try {
            Platform.runLater(() -> watchFocus(cache));
        } catch (IllegalStateException noToolkit) {
            // Without JavaFX there is no window to come back to.
        }
    }

    private static void watchFocus(GitStatusCache cache) {
        ChangeListener<Boolean> focus = (property, was, focused) -> {
            if (focused) {
                cache.refreshAll();
            }
        };
        // Stages only: a popup - a tooltip, a menu - takes the focus too, and is no coming back.
        Window.getWindows().stream().filter(Stage.class::isInstance)
                .forEach(window -> window.focusedProperty().addListener(focus));
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getRemoved().forEach(window -> window.focusedProperty().removeListener(focus));
                change.getAddedSubList().stream().filter(Stage.class::isInstance)
                        .forEach(window -> window.focusedProperty().addListener(focus));
            }
        });
    }

    /** Refreshes the repository of every file the filesystem reports a change of. */
    private record FileChanges(GitStatusCache cache) implements FileChangeListener {

        private void changed(FileObject file) {
            File changed = file == null ? null : FileUtil.toFile(file);
            File folder = changed == null ? null : changed.getParentFile();
            if (folder != null) {
                cache.refresh(folder.toPath());
            }
        }

        @Override
        public void fileFolderCreated(FileEvent event) {
            changed(event.getFile());
        }

        @Override
        public void fileDataCreated(FileEvent event) {
            changed(event.getFile());
        }

        @Override
        public void fileChanged(FileEvent event) {
            changed(event.getFile());
        }

        @Override
        public void fileDeleted(FileEvent event) {
            changed(event.getFile());
        }

        @Override
        public void fileRenamed(FileRenameEvent event) {
            changed(event.getFile());
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent event) {
            // Attributes are the IDE's own, not content git tracks.
        }
    }
}
