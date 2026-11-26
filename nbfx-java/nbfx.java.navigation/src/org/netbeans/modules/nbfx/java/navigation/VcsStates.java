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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.control.TreeView;
import org.netbeans.api.nbfx.vcs.VcsAnnotator;
import org.netbeans.api.nbfx.vcs.VcsFileState;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

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
     * The state a group is badged with for the changed {@code files} it lists, or {@code null} for
     * none: conflicts when one of them has some, changes otherwise.
     */
    static VcsFileState stateAmong(List<FileObject> files) {
        VcsFileState among = null;
        for (FileObject file : files) {
            VcsFileState state = stateOf(file);
            if (state == VcsFileState.CONFLICTED) {
                return state;
            }
            if (state != null) {
                among = VcsFileState.MODIFIED;
            }
        }
        return among;
    }

    /**
     * Repaints {@code tree}, while it is shown, whenever an annotator reports that states may have
     * changed. The reports come from any thread and in bursts; one repaint on the FX thread answers
     * those pending. A tree taken out of its scene is no longer told, so that the annotators do not
     * hold it, and is repainted when put back. Called on the FX thread.
     */
    static void track(TreeView<?> tree) {
        Tracker tracker = new Tracker(tree);
        tree.sceneProperty().addListener((property, was, scene) -> tracker.listen(scene != null));
        tracker.listen(tree.getScene() != null);
    }

    /** What listens to the annotators for a tree: those registered now, and those that come or go. */
    private static final class Tracker implements LookupListener {

        private final TreeView<?> tree;
        private final AtomicBoolean pending = new AtomicBoolean();
        private final Lookup.Result<VcsAnnotator> registered = Lookup.getDefault().lookupResult(VcsAnnotator.class);
        private final Runnable repaint = this::repaint;
        private List<VcsAnnotator> heard = List.of();

        Tracker(TreeView<?> tree) {
            this.tree = tree;
        }

        /** Listens to the annotators registered now, or with {@code shown} false to none. FX thread. */
        void listen(boolean shown) {
            heard.forEach(annotator -> annotator.removeChangeListener(repaint));
            registered.removeLookupListener(this);
            heard = List.of();
            if (shown) {
                registered.addLookupListener(this);
                heard = List.copyOf(registered.allInstances());
                heard.forEach(annotator -> annotator.addChangeListener(repaint));
                // What changed while nobody was told.
                repaint();
            }
        }

        @Override
        public void resultChanged(LookupEvent event) {
            Platform.runLater(() -> listen(tree.getScene() != null));
        }

        private void repaint() {
            if (pending.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    pending.set(false);
                    tree.refresh();
                });
            }
        }
    }
}
