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
package org.netbeans.modules.nbfx.ide.vcs.model;

/**
 * How a file differs between the two sides of a {@link VcsCompareMode}.
 *
 * @since 1.1
 */
public enum VcsChangeKind {

    /** The file exists on the newer side only and is tracked. */
    ADDED,

    /** The content differs. */
    MODIFIED,

    /** The file exists on the older side only. */
    DELETED,

    /** The file was moved; {@link VcsFileStatus#originalPath()} is where it was. */
    RENAMED,

    /** The file is a copy; {@link VcsFileStatus#originalPath()} is its source. */
    COPIED,

    /** The kind of file changed (regular file, symbolic link, submodule). */
    TYPE_CHANGED,

    /** The file is on disk and not tracked; it only exists in the working tree. */
    UNTRACKED,

    /** The file has an unresolved merge conflict. */
    CONFLICTED
}
