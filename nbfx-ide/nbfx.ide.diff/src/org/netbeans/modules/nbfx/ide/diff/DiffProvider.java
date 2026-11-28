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

import java.util.function.BooleanSupplier;
import java.io.IOException;
import java.util.List;
import org.netbeans.api.nbfx.editor.SavedDocument;

/**
 * What a diff tab shows when it is of more than two fixed texts: the files that differ, each with
 * its two texts, and the ways to compare them the user can choose from. The diff view asks again
 * when a mode is chosen and when it is refreshed.
 * <p>
 * {@link #modes()}, {@link #listed()}, {@link #savedState} and {@link #modeChosen} are called on
 * the JavaFX Application Thread: they answer at once, from what the provider already has, and
 * throw nothing - a provider that does is taken to have no modes, to list its files, and not to
 * be restored. {@link #entries} and the {@linkplain DiffEntry.Loader loaders} of its entries are
 * called off that thread, one at a time, and may read the disk or run a command; what they throw
 * is shown in the diff as a comparison that failed. A request the user no longer waits for - the
 * mode was changed again, the tab closed - is not started, its answer is not shown, and while it
 * runs it is told so by the {@code cancelled} it was given, to give up what it reads or runs.
 */
public interface DiffProvider {

    /**
     * The ways to compare, offered as toggles in this order; empty when there is only one.
     */
    List<DiffMode> modes();

    /**
     * The files that differ in {@code mode}. Called off the JavaFX Application Thread.
     *
     * @param mode one of {@link #modes()}, or {@code null} when there are none
     * @param cancelled whether the list is no longer wanted - another mode was chosen, the files
     *        were asked for again, the tab was closed; asked as often as the work can be given
     *        up, and never {@code null}
     * @throws java.util.concurrent.CancellationException when {@code cancelled} answered
     *         {@code true} and the work was given up; nothing is shown of it, nor said
     */
    List<DiffEntry> entries(DiffMode mode, BooleanSupplier cancelled) throws IOException;

    /**
     * What opens this diff again in a later session, comparing as {@code mode}: the id of a
     * {@link org.netbeans.spi.nbfx.editor.DocumentRestorer} and what it needs. {@code null}, the
     * default, for a diff that is not restored.
     *
     * @param mode the way the diff compares now - one of {@link #modes()}, or {@code null} when
     *        there are none
     */
    default SavedDocument savedState(DiffMode mode) {
        return null;
    }

    /**
     * Whether the files are listed in a table above the diff, to choose from. A provider of the
     * diff of one file says no, and always has that one entry.
     */
    default boolean listed() {
        return true;
    }

    /**
     * Told when the user chooses {@code mode} with its toggle - not when a diff is opened in a
     * mode, compared again or opened again with the session - so that a provider can make it the
     * mode its next diff opens in. The default does nothing.
     *
     * @param mode one of {@link #modes()}
     */
    default void modeChosen(DiffMode mode) {
    }

    /**
     * Told when the tab of the diff is closed: nothing is asked of the provider afterwards, and
     * it lets go of what it holds. The default does nothing.
     */
    default void dispose() {
    }
}
