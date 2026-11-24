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

import java.io.IOException;
import java.util.List;
import org.netbeans.api.nbfx.editor.SavedDocument;

/**
 * What a diff tab shows when it is of more than two fixed texts: the files that differ, each with
 * its two texts, and the ways to compare them the user can choose from. The diff view asks again
 * when a mode is chosen and when it is refreshed.
 *
 * @since 1.1
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
     */
    List<DiffEntry> entries(DiffMode mode) throws IOException;

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
}
