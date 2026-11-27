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
package org.netbeans.modules.nbfx.ide.diff;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javafx.scene.Node;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Opens diff tabs in the editor area: two texts side by side with their differences marked. What
 * the two texts are - a file and its last commit, two revisions - is the caller's; this module
 * knows nothing of version control.
 */
public final class DiffView {

    /**
     * The diffs that are open, by what they are of, from the moment they are asked for until
     * their tab is closed. Read and changed on the JavaFX Application Thread only.
     */
    private static final Map<Object, DiffDocument> OPEN = new HashMap<>();

    private DiffView() {
    }

    /**
     * Shows the differences between {@code left} and {@code right} in a tab titled
     * "{@code name} [ Diff ]". A diff open with the same {@code key} is brought to the front, not
     * opened twice, and shows {@code left} and {@code right} in place of what it showed. Must run on the JavaFX Application Thread.
     *
     * @param key what the diff is of, by {@code equals}: the file, or the file and what it is
     *        compared with
     * @param name the name in the title of the tab, usually the file's
     * @param icon the icon of the tab, or {@code null}
     * @param left the text compared from, shown on the left
     * @param right the text compared to, shown on the right
     */
    public static void open(Object key, String name, Node icon, DiffSource left, DiffSource right) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        ContentManager content = Lookup.getDefault().lookup(ContentManager.class);
        if (content == null) {
            return;
        }
        DiffDocument open = OPEN.get(key);
        if (open != null) {
            // What it is of by now, not what it was of when it was opened.
            open.pane().setSources(left, right);
            content.openDocument(open, icon);
            return;
        }
        DiffDocument document = new DiffDocument(key, NbBundle.getMessage(DiffView.class, "Diff.title", name),
                new DiffPane(left, right), () -> OPEN.remove(key));
        // Known here from now on, not from when its tab is there: asked for twice in a row, before
        // the editor area has it, a diff is still opened once.
        OPEN.put(key, document);
        content.openDocument(document, icon);
    }

    /** The diff open for {@code key}, or {@code null}; for tests. */
    static DiffDocument openFor(Object key) {
        return OPEN.get(key);
    }
}
