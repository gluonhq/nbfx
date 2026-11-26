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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.api.nbfx.vcs.VcsAnnotator;
import org.netbeans.api.nbfx.vcs.VcsFileState;
import org.netbeans.junit.MockServices;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Verifies the badge of a group of chosen files - the project files of a project: it is for the
 * files the group lists, wherever they are, and not for the folder of the project.
 */
public class VcsStatesTest {

    private static final Map<FileObject, VcsFileState> STATES = new ConcurrentHashMap<>();

    private FileObject pom;
    private FileObject nested;
    private FileObject source;

    @Before
    public void createFiles() throws Exception {
        STATES.clear();
        MockServices.setServices(Annotator.class);
        FileObject project = FileUtil.createMemoryFileSystem().getRoot();
        pom = project.createData("pom.xml");
        nested = project.createFolder("nbproject").createData("project.xml");
        source = project.createFolder("src").createData("Main.java");
    }

    @Test
    public void aGroupOfUnchangedFilesHasNoBadge() {
        // A changed source is not one of the files the group lists.
        STATES.put(source, VcsFileState.MODIFIED);

        assertNull(VcsStates.stateAmong(List.of(pom, nested)));
        assertNull(VcsStates.stateAmong(List.of()));
    }

    @Test
    public void aGroupIsBadgedForAChangedFileBelowTheProjectFolder() {
        STATES.put(nested, VcsFileState.ADDED);

        assertEquals(VcsFileState.MODIFIED, VcsStates.stateAmong(List.of(pom, nested)));
    }

    @Test
    public void conflictsComeFirst() {
        STATES.put(pom, VcsFileState.DELETED);
        STATES.put(nested, VcsFileState.CONFLICTED);

        assertEquals(VcsFileState.CONFLICTED, VcsStates.stateAmong(List.of(pom, nested)));
    }

    /** Answers with the states the test set. */
    public static final class Annotator implements VcsAnnotator {

        @Override
        public Optional<VcsFileState> stateOf(FileObject file) {
            return Optional.ofNullable(STATES.get(file));
        }

        @Override
        public Optional<VcsFileState> stateWithin(FileObject folder, boolean recursive) {
            return Optional.empty();
        }

        @Override
        public void addChangeListener(Runnable listener) {
        }

        @Override
        public void removeChangeListener(Runnable listener) {
        }
    }
}
