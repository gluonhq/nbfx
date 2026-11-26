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
package org.netbeans.api.nbfx.vcs;

import java.util.Optional;
import org.openide.filesystems.FileObject;

/**
 * Tells the views that draw files - the Projects and Files trees - the version control state of
 * each, so that they can mark the changed ones and the folders that hold them. A version control system registers one in the
 * default lookup; a view asks every one registered and takes the first answer.
 * <p>
 * The answer comes from what the annotator already knows, as it is asked for every row a view draws,
 * on the thread that draws it: it must not read the disk or run a command. An annotator that has to
 * find out answers with nothing, finds out elsewhere, and tells its listeners when it knows.
 * <p>
 * Under development until the version control contract is complete (M2): which annotator answers
 * for a file when several are registered, and how "no changes" is told from "not known yet", are
 * not settled, and this interface may change incompatibly until then.
 *
 * @since 1.0
 */
public interface VcsAnnotator {

    /**
     * The state of {@code file}, or empty when it has no changes, is not under this version control,
     * or its state is not known yet. Returns at once, on any thread.
     *
     * @param file the file a view is drawing; may be {@code null}
     * @return the state to mark the file with, never {@code null}
     */
    Optional<VcsFileState> stateOf(FileObject file);

    /**
     * What the changed files in {@code folder} amount to, for the badge a view puts on a folder, a
     * package or a project: {@link VcsFileState#CONFLICTED} when one of them has conflicts,
     * {@link VcsFileState#MODIFIED} when there are changes of any other kind, and empty when there
     * are none, the folder is not under this version control, or it is not known yet. Returns at
     * once, on any thread.
     *
     * @param folder the folder a view is drawing; may be {@code null}
     * @param recursive whether the files in the folders below count too, as for a folder or a
     *        project, or only the files right in it, as for a package
     * @return the state to badge the folder with, never {@code null}
     */
    Optional<VcsFileState> stateWithin(FileObject folder, boolean recursive);

    /**
     * Adds a listener told when the state of some files may have changed, so that the views ask
     * again. It is called on any thread.
     *
     * @param listener the listener to add
     */
    void addChangeListener(Runnable listener);

    /**
     * Removes a listener added with {@link #addChangeListener(Runnable)}.
     *
     * @param listener the listener to remove
     */
    void removeChangeListener(Runnable listener);
}
