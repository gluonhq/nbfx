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

import java.util.Objects;
import java.util.function.Supplier;
import javafx.scene.Node;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Opens diff tabs in the editor area: two texts side by side with their differences marked. What
 * the two texts are - a file and its last commit, two revisions - is the caller's; this module
 * knows nothing of version control.
 * <p>
 * The text on the right is edited in the diff when its {@link DiffSource#file() file} is one on
 * disk that can be written, as in an editor of that file and sharing its text with them; a block
 * of it, or all of it, can be reverted to the text on the left. Any other text is only shown.
 *
 * @since 1.0
 */
public final class DiffView {

    private DiffView() {
    }

    /**
     * Shows the differences between {@code left} and {@code right} in a tab titled
     * "{@code name} [ Diff ]". A diff open with the same {@code key} is brought to the front and
     * compared again, not opened twice. Must run on the JavaFX Application Thread.
     *
     * @param key what the diff is of, by {@code equals}: the file, or the file and what it is
     *        compared with
     * @param name the name in the title of the tab, usually the file's
     * @param icon the icon of the tab, or {@code null}
     * @param left the text compared from, shown on the left
     * @param right the text compared to, shown on the right
     */
    public static void open(Object key, String name, Node icon, DiffSource left, DiffSource right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        open(key, name, icon, () -> new DiffDocument(key, title(name), new DiffPane(left, right)));
    }

    /**
     * Shows the diff of what {@code provider} has in a tab titled "{@code name} [ Diff ]": the ways
     * it can compare as toggles, its files in a table when it lists them, and the two texts of the
     * file selected. A diff open with the same {@code key} is brought to the front and refreshed,
     * in the way to compare it is in. Must run on the JavaFX Application Thread.
     *
     * @param key what the diff is of, by {@code equals}: the file or the folder
     * @param name the name in the title of the tab
     * @param icon the icon of the tab, or {@code null}
     * @param provider what differs, and how it can be compared
     * @param mode the way to compare the diff opens in - one of the provider's - or {@code null}
     *        for its first
     * @since 1.1
     */
    public static void open(Object key, String name, Node icon, DiffProvider provider, DiffMode mode) {
        Objects.requireNonNull(provider, "provider");
        open(key, name, icon, () -> {
            DiffBrowser browser = new DiffBrowser(provider, mode);
            // As the last diff was left: a list or a tree, and the columns.
            DiffSettings.attach(browser);
            return new DiffDocument(key, title(name), browser);
        });
    }

    private static void open(Object key, String name, Node icon, Supplier<DiffDocument> factory) {
        Objects.requireNonNull(key, "key");
        ContentManager content = Lookup.getDefault().lookup(ContentManager.class);
        if (content == null) {
            return;
        }
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        if (context != null) {
            for (EditorDocument open : context.getDocuments()) {
                if (open instanceof DiffDocument diff && diff.key().equals(key)) {
                    diff.content().refresh();
                    content.openDocument(diff, icon);
                    return;
                }
            }
        }
        content.openDocument(factory.get(), icon);
    }

    private static String title(String name) {
        return NbBundle.getMessage(DiffView.class, "Diff.title", name);
    }
}
