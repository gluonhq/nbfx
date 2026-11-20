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
package org.netbeans.modules.nbfx.java.navigation.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Verifies the folder comparison the tree search runs after file events, when folders may be gone. */
public class TreeNavTest {

    @Test
    public void foldersGoneFromTheDiskAreComparedByPath() throws Exception {
        Path root = Files.createTempDirectory("nbfx-tree-nav").toRealPath();
        Path gone = root.resolve("target/classes/org/openjfx/hellofx");
        Path other = root.resolve("target/classes/org/openjfx");

        assertTrue(TreeNav.isSameFolder(gone, root.resolve("target/classes/org/openjfx/hellofx")));
        assertFalse("a deleted folder is not another one, and comparing them does not fail",
                TreeNav.isSameFolder(gone, other));
        assertFalse(TreeNav.isSameFolder(root, gone));
        assertFalse(TreeNav.isSameFolder(gone, root));
        assertFalse(TreeNav.isSameFolder(null, root));
        assertFalse(TreeNav.isSameFolder(root, null));
    }

    @Test
    public void foldersOnTheDiskAreTheSameThroughALink() throws Exception {
        Path root = Files.createTempDirectory("nbfx-tree-nav").toRealPath();
        Path folder = Files.createDirectory(root.resolve("src"));
        Path link = Files.createSymbolicLink(root.resolve("link"), folder);

        assertTrue(TreeNav.isSameFolder(folder, link));
        assertFalse(TreeNav.isSameFolder(folder, root));
    }
}
