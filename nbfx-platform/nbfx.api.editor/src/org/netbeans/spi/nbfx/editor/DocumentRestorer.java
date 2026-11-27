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
package org.netbeans.spi.nbfx.editor;

/**
 * Opens again the documents of one kind that are the editor of no file - diffs - when the shell
 * restores the tabs of the last session. Registered in the default lookup; a document names its
 * restorer in {@link org.netbeans.api.nbfx.editor.EditorDocument#getSavedState()}.
 *
 * @since 1.5
 */
public interface DocumentRestorer {

    /** What the saved documents of this restorer are told by: letters, digits, {@code _}, {@code .} and {@code -}. */
    String id();

    /**
     * Opens the document saved as {@code state}, with
     * {@link org.netbeans.api.nbfx.editor.ContentManager#openDocument}, before returning; or does
     * nothing when what it was of is gone. The document opened must give back, from
     * {@link org.netbeans.api.nbfx.editor.EditorDocument#getSavedState()}, this id and
     * {@code state}: that is how the shell finds its tab, to put it where it was. Called on the
     * JavaFX Application Thread.
     *
     * @param state the state of a {@link org.netbeans.api.nbfx.editor.SavedDocument} of this id
     */
    void restore(String state);
}
