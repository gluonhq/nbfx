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
package org.netbeans.modules.nbfx.java.navigation;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.control.TreeView;
import org.netbeans.api.nbfx.vcs.VcsAnnotator;
import org.netbeans.api.nbfx.vcs.VcsFileState;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * The version control state of the files of the navigator trees, from the registered
 * {@link VcsAnnotator}s: the first one that knows a file answers for it.
 */
final class VcsStates {

    private VcsStates() {
    }

    /** The state {@code file} is marked with, or {@code null} for none. */
    static VcsFileState stateOf(FileObject file) {
        if (file == null) {
            return null;
        }
        for (VcsAnnotator annotator : Lookup.getDefault().lookupAll(VcsAnnotator.class)) {
            Optional<VcsFileState> state = annotator.stateOf(file);
            if (state.isPresent()) {
                return state.get();
            }
        }
        return null;
    }

    /**
     * The state {@code folder} is badged with for the changed files it holds - below it too with
     * {@code recursive} - or {@code null} for none.
     */
    static VcsFileState stateWithin(FileObject folder, boolean recursive) {
        if (folder == null || !folder.isFolder()) {
            return null;
        }
        for (VcsAnnotator annotator : Lookup.getDefault().lookupAll(VcsAnnotator.class)) {
            Optional<VcsFileState> state = annotator.stateWithin(folder, recursive);
            if (state.isPresent()) {
                return state.get();
            }
        }
        return null;
    }

    /**
     * Repaints {@code tree} whenever an annotator reports that states may have changed. The reports
     * come from any thread and in bursts; one repaint on the FX thread answers those pending.
     */
    static void track(TreeView<?> tree) {
        AtomicBoolean pending = new AtomicBoolean();
        Runnable repaint = () -> {
            if (pending.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    pending.set(false);
                    tree.refresh();
                });
            }
        };
        for (VcsAnnotator annotator : Lookup.getDefault().lookupAll(VcsAnnotator.class)) {
            annotator.addChangeListener(repaint);
        }
    }
}
