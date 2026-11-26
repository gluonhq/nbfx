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

/**
 * What version control says of a file in the working tree, compared with the last commit: the
 * states a view tells apart when it draws the file.
 * <p>
 * Under development until the version control contract is complete (M2): states may be added -
 * untracked, renamed, copied - so a {@code switch} over them should have a {@code default}.
 *
 * @since 1.0
 */
public enum VcsFileState {
    /** New: not in the last commit, whether already added to the next one or not. */
    ADDED,
    /** In the last commit, with other content now. */
    MODIFIED,
    /** In the last commit, no longer in the working tree. */
    DELETED,
    /** Left with conflicts by a merge. */
    CONFLICTED
}
